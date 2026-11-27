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
from app.database.redis import cache
from app.services.board_service import HIDDEN_ID, HIDDEN_NAME, is_hidden

# The community top 5 on the dashboard is shared by everyone for this long.
TOP_CACHE_SECONDS = 20

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

        # Top contributors. Ranking the whole week for every dashboard open is
        # the heaviest query here, and it's the same for everyone: computed once
        # per TOP_CACHE_SECONDS and shared (a few seconds stale is fine).
        contributors: list[Contributor] = []
        if week is not None:
            key = f"community:top5:{week.id}"
            rows = await cache.get_json(key)
            if rows is None:
                ranked_rows = (await submission_repository.leaderboard_for_week(week.id))[:5]
                rows = [[str(r["_id"]), int(r["total"])] for r in ranked_rows]
                await cache.set_json(key, rows, ttl=TOP_CACHE_SECONDS)
            people = await user_repository.get_many({uuid.UUID(uid) for uid, _ in rows})
            for rank, (uid, total) in enumerate(rows, start=1):
                u = people.get(uuid.UUID(uid))
                if u is None:
                    continue
                public = PublicUserOut.model_validate(u)
                if u.id != user.id and is_hidden(u):        # "hide me on boards"
                    public = public.model_copy(update={
                        "id": HIDDEN_ID, "display_name": HIDDEN_NAME, "photo_url": None, "avatar": None})
                contributors.append(Contributor(user=public, total=total, rank=rank))

        # Weekly chart buckets (Sun..Sat).
        chart_map = {d: 0 for d in ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"]}
        if week is not None:
            for row in await submission_repository.daily_chart(week.id, user.id):
                chart_map[_DAYS[int(row["_id"])]] = int(row["value"])
        weekly_chart = [ChartPoint(day=d, value=chart_map[d])
                        for d in ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"]]

        # Recent activity (enriched with actor display info).
        recent = await self.feed(group_id, limit=10, viewer_id=user.id)

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
        self, group_id: uuid.UUID, limit: int = 20, viewer_id: uuid.UUID | None = None,
    ) -> list[FeedItemOut]:
        """Recent group activity, each enriched with the actor's name/initial."""
        from app.services.board_service import feed_actor
        activities = await activity_repository.recent(group_id, limit=limit)
        actors = await user_repository.get_many({a.actor_id for a in activities})
        items: list[FeedItemOut] = []
        for a in activities:
            actor_id, name, text = feed_actor(actors.get(a.actor_id), viewer_id, a.actor_id,
                                              a.text, "Member")
            initial = (name.strip()[:1] or "•").upper()
            items.append(FeedItemOut(
                id=a.id, actor_id=actor_id, actor_name=name, initial=initial,
                type=a.type, text=text, reactions=a.reactions,
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
