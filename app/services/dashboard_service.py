"""Dashboard read-model: assembles the whole home screen in one response."""
from __future__ import annotations

from datetime import datetime, timezone

from app.core import clock
from app.schemas.preferences import Preferences

import uuid

from app.models.user import User
from app.repositories.activity_repo import activity_repository
from app.repositories.submission_repo import submission_repository
from app.repositories.user_repo import user_repository
from app.schemas.dashboard import (
    ChartPoint,
    Contributor,
    DashboardResponse,
    FeedItemOut,
    GoalProgress,
)
from app.schemas.user import PublicUserOut
from app.schemas.weekly import WeeklySessionOut

_DAYS = ["", "Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"]  # day-of-week 1..7 (Sunday = 1)


class DashboardService:
    async def build(self, user: User, group_id: uuid.UUID) -> DashboardResponse:
        """``group_id`` only picks the activity feed; the numbers are the
        user's own and the one global week's."""
        from app.services.group_service import group_service
        from app.services.weekly_service import weekly_service
        week = await weekly_service.current()
        current_week = (
            WeeklySessionOut.model_validate(week) if week is not None else None
        )

        user_week = (
            await submission_repository.sum_for_user_week(week.id, user.id)
            if week else 0
        )
        today = await submission_repository.sum_for_user_since_all_groups(
            user.id, _start_of_today()
        )
        monthly = await submission_repository.sum_for_user_since_all_groups(
            user.id, _start_of_month()
        )

        # Top contributors (reuse the weekly leaderboard aggregation).
        contributors: list[Contributor] = []
        if week is not None:
            rows = (await submission_repository.leaderboard_for_week(week.id))[:5]
            people = await user_repository.get_many({r["_id"] for r in rows})
            for rank, row in enumerate(rows, start=1):
                u = people.get(row["_id"])
                if u is None:
                    continue
                contributors.append(Contributor(
                    user=PublicUserOut.model_validate(u), total=int(row["total"]), rank=rank,
                ))

        # Weekly chart buckets (Sun..Sat).
        chart_map = {d: 0 for d in ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"]}
        if week is not None:
            for row in await submission_repository.daily_chart(week.id, user.id):
                chart_map[_DAYS[int(row["_id"])]] = int(row["value"])
        weekly_chart = [ChartPoint(day=d, value=chart_map[d])
                        for d in ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"]]

        # Recent activity (enriched with actor display info).
        recent = await self.feed(group_id, limit=10)

        remaining = self._remaining_days(week.end_date if week else None)
        goal_target = Preferences.from_stored(user.preferences).weekly_goal
        goal = GoalProgress(
            target=goal_target,
            progress=min(user_week, goal_target),
            percent=min(100, int(user_week / goal_target * 100)) if goal_target else 0,
        )

        return DashboardResponse(
            current_week=current_week,
            user_total=user_week,
            today_total=today,
            weekly_total=user_week,
            monthly_total=monthly,
            lifetime_total=user.lifetime_total,
            group_total=week.group_total if week else 0,
            remaining_days=remaining,
            streak=user.streak_days,
            goal=goal,
            top_contributors=contributors,
            recent_activity=recent,
            weekly_chart=weekly_chart,
            groups_this_week=await group_service.this_week(user),
        )

    async def feed(
        self, group_id: uuid.UUID, limit: int = 20
    ) -> list[FeedItemOut]:
        """Recent group activity, each enriched with the actor's name/initial."""
        activities = await activity_repository.recent(group_id, limit=limit)
        actors = await user_repository.get_many({a.actor_id for a in activities})
        items: list[FeedItemOut] = []
        for a in activities:
            actor = actors.get(a.actor_id)
            name = actor.display_name if actor else "Member"
            initial = (name.strip()[:1] or "•").upper()
            items.append(FeedItemOut(
                id=a.id, actor_id=a.actor_id, actor_name=name, initial=initial,
                type=a.type, text=a.text, reactions=a.reactions,
                created_at=a.created_at,
            ))
        return items

    @staticmethod
    def _remaining_days(end: datetime | None) -> int:
        if end is None:
            return 0
        delta = end - datetime.now(timezone.utc)
        return max(0, delta.days)


def _start_of_today() -> datetime:
    return clock.start_of_today()


def _start_of_month() -> datetime:
    return clock.start_of_month()


dashboard_service = DashboardService()
