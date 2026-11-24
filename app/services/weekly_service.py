"""The weekly cycle.

There is one series of weeks, kept on the global community group. Every log
points at the active week. A week closes on Friday at WEEK_CLOSE_LOCAL_TIME
(IST, around Maghrib): it is archived into Hall of Fame, every group's week is
frozen (total + top 3), and the next week opens at that same instant.

Closing is safe to trigger twice:
  * the scheduled job only closes a week whose closing time has come, and the
    week it opens closes a week later, so a second run finds nothing due;
  * a manual close can name the week it means (``expected_week_id``); if that
    week is already closed nothing happens;
  * the active week row is locked (SELECT ... FOR UPDATE) and a partial
    unique index allows only one active week, so two closes can't interleave.
"""
from __future__ import annotations

import uuid
from datetime import datetime, timedelta

from loguru import logger

from app.config.settings import settings
from app.core import clock
from app.core.exceptions import NotFoundError
from app.database.redis import cache
from app.models.activity import ActivityItem
from app.models.group import Group
from app.models.weekly_session import WeeklySession, WeekStatus
from app.repositories import board_repo
from app.repositories.activity_repo import activity_repository
from app.repositories.group_repo import group_repository, group_week_repository
from app.repositories.user_repo import user_repository
from app.repositories.weekly_repo import weekly_repository
from app.schemas.weekly import HallOfFameEntry
from app.services import push_service

# A scheduled close runs a little after the boundary; allow for clock skew.
_GRACE = timedelta(minutes=2)


def week_end_for(start: datetime) -> datetime:
    """When a week opened at ``start`` closes: the next scheduled close,
    unless that's too close (a week closed early by hand), then one more week."""
    end = clock.next_week_close(start)
    if end - start < timedelta(hours=settings.WEEK_MIN_HOURS):
        end += timedelta(days=7)
    return end


def _ranked_top(rows: list[tuple[uuid.UUID, int]], n: int = 3) -> list[tuple[uuid.UUID, int, int]]:
    from app.services.board_service import ranked
    return [r for r in ranked([r for r in rows if r[1] > 0]) if r[2] <= n][:n]


class WeeklyService:
    async def open_first_week(self, group: Group) -> WeeklySession:
        return await self._open_week(group.id, week_number=1)

    async def global_week_or_none(self, group_id: uuid.UUID) -> WeeklySession | None:
        return await weekly_repository.get_active(group_id)

    async def _open_week(self, group_id: uuid.UUID, week_number: int,
                         start: datetime | None = None) -> WeeklySession:
        start = start or clock.now_utc()
        iso_year, iso_week, _ = start.astimezone(clock.tz()).isocalendar()
        session = WeeklySession(
            group_id=group_id, week_number=week_number,
            gregorian_week=f"{iso_year}-W{iso_week:02d}",
            start_date=start, end_date=week_end_for(start), status=WeekStatus.ACTIVE,
        )
        await weekly_repository.create(session)
        return session

    async def current(self) -> WeeklySession:
        """The active global week (created on first use)."""
        from app.services.community_service import community_service
        group = await community_service.get_or_create_global_group()
        week = await weekly_repository.get_active(group.id)
        if week is None:
            week = await self._open_week(group.id, await weekly_repository.latest_week_number(group.id) + 1)
        return week

    async def get_current(self, group_id: uuid.UUID) -> WeeklySession:
        session = await weekly_repository.get_active(group_id)
        if session is None:
            raise NotFoundError("No active week for this group")
        return session

    async def history(self, group_id: uuid.UUID, cursor: str | None, limit: int):
        return await weekly_repository.history(group_id, cursor, limit)

    # ── Closing ──────────────────────────────────────────────────────────

    async def close_due(self, now: datetime | None = None) -> WeeklySession | None:
        """Scheduled close: close the active week if its time has come.
        Returns the archived week, or None when nothing was due."""
        from app.services.community_service import community_service
        now = now or clock.now_utc()
        group = await community_service.get_or_create_global_group()
        active = await weekly_repository.get_active(group.id, for_update=True)
        if active is None or active.end_date > now + _GRACE:
            return None
        # The next week starts at the boundary itself, unless the server was
        # down for a long time (then now, so the new week isn't already over).
        late = now - active.end_date
        start = active.end_date if timedelta(0) <= late < timedelta(hours=1) else now
        archived, _, _ = await self._close(active, locked_by=group.owner_id, now=now, next_start=start)
        return archived

    async def close_week(self, locked_by: uuid.UUID, expected_week_id: uuid.UUID | None = None):
        """Close now, by hand (super members / admins). With ``expected_week_id``
        a repeated press does nothing once that week is closed.
        Returns (archived, hall_of_fame, new_week) or None."""
        from app.services.community_service import community_service
        group = await community_service.get_or_create_global_group()
        active = await weekly_repository.get_active(group.id, for_update=True)
        if active is None:
            raise NotFoundError("No active week to lock")
        if expected_week_id is not None and active.id != expected_week_id:
            return None
        return await self._close(active, locked_by=locked_by, now=clock.now_utc())

    async def lock_week(self, group_id: uuid.UUID, locked_by: uuid.UUID):
        """v2 name for close_week (the group id is ignored: there's one week)."""
        result = await self.close_week(locked_by)
        assert result is not None
        return result

    async def _close(self, active: WeeklySession, *, locked_by: uuid.UUID, now: datetime,
                     next_start: datetime | None = None):
        rows = await board_repo.global_week(active.id)
        top = _ranked_top(rows)
        people = await user_repository.get_many({uid for uid, _, _ in top})
        hall = [HallOfFameEntry(user_id=uid, total=total, rank=rank,
                                display_name=people[uid].display_name if uid in people else "Member")
                for uid, total, rank in top]

        active.status = WeekStatus.ARCHIVED
        active.totals = {str(uid): total for uid, total in rows}
        active.group_total = sum(total for _, total in rows)
        active.hall_of_fame = [h.model_dump(mode="json") for h in hall]
        active.locked_at = now
        active.locked_by = locked_by
        await weekly_repository.save(active)

        new_week = await self._open_week(active.group_id, active.week_number + 1, next_start or now)
        await self._close_groups(active)

        text = (f"Week {active.week_number} has been finalized and archived. "
                f"A new week has begun.")
        try:
            await activity_repository.create(ActivityItem(
                group_id=active.group_id, actor_id=locked_by, type="week.locked",
                text=text, created_by=locked_by,
                payload={"week_number": active.week_number, "total": active.group_total}))
            await push_service.send_to_community("Consideration filed", text,
                                                 {"type": "week.locked", "week_number": active.week_number})
        except Exception:  # pragma: no cover - notification must not block the lock
            logger.exception("week close notification failed")
        await cache.delete_prefix("board:")
        return active, hall, new_week

    async def _close_groups(self, week: WeeklySession) -> None:
        """Freeze each group's week: total, top 3, a "week winners" activity."""
        for gid in await group_repository.all_live_ids():
            group = await group_repository.get(gid)
            if group is None:
                continue
            rows = await board_repo.group_board(gid, week.id)
            total = sum(t for _, t in rows)
            top = [{"user_id": str(uid), "total": t, "rank": r} for uid, t, r in _ranked_top(rows)]
            await group_week_repository.close(gid, week.id, week.week_number, total, top, group.weekly_goal)
            if total <= 0:
                continue
            people = await user_repository.get_many({uuid.UUID(t["user_id"]) for t in top})
            names = ", ".join(people[uuid.UUID(t["user_id"])].display_name
                              for t in top if uuid.UUID(t["user_id"]) in people)
            text = f"Week {week.week_number} closed with {total:,} recitations. Top: {names}."
            await activity_repository.create(ActivityItem(
                group_id=gid, actor_id=group.owner_id, type="week.winners", text=text,
                payload={"week_number": week.week_number, "total": total, "top": top}))
            try:
                await push_service.send_to_topic(
                    push_service.group_topic(gid), f"{group.name}: week {week.week_number}", text,
                    {"type": "week.winners", "group_id": str(gid), "week_number": week.week_number})
            except Exception:  # pragma: no cover
                logger.exception("week winners push failed for {}", gid)


weekly_service = WeeklyService()
