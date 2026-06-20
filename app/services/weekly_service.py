"""Weekly session use-cases including the Friday lock / Hall of Fame."""
from __future__ import annotations

from datetime import datetime, timedelta, timezone

from beanie import PydanticObjectId

from app.core.exceptions import ConflictError, NotFoundError
from app.models.group import Group
from app.models.weekly_session import WeeklySession, WeekStatus
from app.repositories.submission_repo import submission_repository
from app.repositories.user_repo import user_repository
from app.repositories.weekly_repo import weekly_repository
from app.schemas.weekly import HallOfFameEntry


def _week_bounds(now: datetime) -> tuple[datetime, datetime, str]:
    """Return (start, end, iso_label) for the Gregorian week containing `now`.

    A Noor week runs Saturday→Friday so that the Friday lock closes the week.
    """
    # Monday=0 … Sunday=6 ; we anchor on Saturday=5.
    days_since_sat = (now.weekday() - 5) % 7
    start = (now - timedelta(days=days_since_sat)).replace(
        hour=0, minute=0, second=0, microsecond=0
    )
    end = start + timedelta(days=6, hours=23, minutes=59, seconds=59)
    iso_year, iso_week, _ = now.isocalendar()
    return start, end, f"{iso_year}-W{iso_week:02d}"


class WeeklyService:
    async def open_first_week(self, group: Group) -> WeeklySession:
        return await self._open_week(group.id, week_number=1)

    async def _open_week(
        self, group_id: PydanticObjectId, week_number: int
    ) -> WeeklySession:
        if await weekly_repository.get_active(group_id) is not None:
            raise ConflictError("An active week already exists for this group")
        now = datetime.now(timezone.utc)
        start, end, label = _week_bounds(now)
        session = WeeklySession(
            group_id=group_id,
            week_number=week_number,
            gregorian_week=label,
            start_date=start,
            end_date=end,
            status=WeekStatus.ACTIVE,
        )
        await weekly_repository.create(session)
        return session

    async def get_current(self, group_id: PydanticObjectId) -> WeeklySession:
        session = await weekly_repository.get_active(group_id)
        if session is None:
            raise NotFoundError("No active week for this group")
        return session

    async def history(self, group_id: PydanticObjectId,
                      cursor: PydanticObjectId | None, limit: int):
        return await weekly_repository.history(group_id, cursor, limit)

    async def lock_week(
        self, group_id: PydanticObjectId, locked_by: PydanticObjectId
    ) -> tuple[WeeklySession, list[HallOfFameEntry], WeeklySession]:
        """Friday lock: finalize totals + Hall of Fame, archive, open next week.

        Runs inside a MongoDB transaction when the deployment supports it
        (replica set); on a standalone dev server it executes sequentially.
        """
        active = await weekly_repository.get_active(group_id)
        if active is None:
            raise NotFoundError("No active week to lock")

        rows = await submission_repository.leaderboard_for_week(active.id)
        hall: list[HallOfFameEntry] = []
        totals: dict[str, int] = {}
        group_total = 0
        for rank, row in enumerate(rows, start=1):
            uid = row["_id"]
            total = int(row["total"])
            totals[str(uid)] = total
            group_total += total
            if rank <= 3:
                user = await user_repository.get(uid)
                hall.append(HallOfFameEntry(
                    user_id=uid,
                    display_name=user.display_name if user else "Member",
                    total=total, rank=rank,
                ))

        active.status = WeekStatus.ARCHIVED
        active.totals = totals
        active.group_total = group_total
        active.hall_of_fame = [h.model_dump() for h in hall]
        active.locked_at = datetime.now(timezone.utc)
        active.locked_by = locked_by
        await weekly_repository.save(active)

        new_week = await self._open_week(group_id, active.week_number + 1)
        return active, hall, new_week


weekly_service = WeeklyService()
