"""Community statistics: per-recitation + per-category breakdowns, week history."""
from __future__ import annotations

import uuid
from datetime import timedelta

from app.models.user import GlobalRole, User
from app.repositories.recitation_repo import recitation_repository
from app.repositories.submission_repo import submission_repository
from app.repositories.weekly_repo import weekly_repository
from app.schemas.statistics import (
    CategoryStat,
    CommunityStats,
    DayTotal,
    MyStats,
    RecitationStat,
    WeekSummary,
)
from app.core import clock
from app.database.redis import cache
from app.services.community_service import community_service
from app.services.streak_service import streak_service
from app.services.submission_service import stats_cache_key

HEATMAP_DAYS = 365
STATS_CACHE_SECONDS = 60

_MANAGER_ROLES = {GlobalRole.SUPER_MEMBER, GlobalRole.SUPER_ADMIN}


def can_manage_weeks(user: User) -> bool:
    return user.role in _MANAGER_ROLES


class StatsService:
    async def community(
        self,
        user: User,
        group_id: uuid.UUID | None = None,
        week_id: uuid.UUID | None = None,
    ) -> CommunityStats:
        gid = await community_service.resolve_group(user, group_id)
        week = (await weekly_repository.get(week_id) if week_id
                else await weekly_repository.get_active(gid))

        can_manage = can_manage_weeks(user)
        if week is None:
            return CommunityStats(total=0, can_manage=can_manage)

        rows = await submission_repository.totals_by_recitation(week.id)
        recs = await recitation_repository.get_many({r["_id"] for r in rows})
        by_recitation: list[RecitationStat] = []
        by_category: dict[str, int] = {}
        total = 0
        for row in rows:
            rid = row["_id"]
            count = int(row["total"])
            total += count
            rec = recs.get(rid)
            category = (rec.category if rec and rec.category else "Other")
            by_recitation.append(RecitationStat(
                recitation_id=rid,
                name=rec.english_name if rec else "Recitation",
                urdu_name=rec.urdu_name if rec else None,
                category=category,
                count=count,
            ))
            by_category[category] = by_category.get(category, 0) + count

        cat_list = [CategoryStat(category=k, count=v)
                    for k, v in sorted(by_category.items(), key=lambda x: -x[1])]

        return CommunityStats(
            week_id=week.id,
            week_number=week.week_number,
            label=week.gregorian_week,
            status=week.status.value,
            total=total,
            by_recitation=by_recitation,
            by_category=cat_list,
            can_manage=can_manage,
        )

    async def weeks(
        self, user: User, group_id: uuid.UUID | None = None
    ) -> list[WeekSummary]:
        """All closed (non-active) weeks, newest first."""
        gid = await community_service.resolve_group(user, group_id)
        sessions = await weekly_repository.closed_for_group(gid)
        return [WeekSummary(
            week_id=s.id, week_number=s.week_number, label=s.gregorian_week,
            total=s.group_total, closed_at=s.locked_at,
        ) for s in sessions]

    async def me(self, user: User) -> MyStats:
        """The Me tab: heat map, per-recitation totals, period totals, streaks.

        Cached for 60 s per user; every log and undo clears it.
        """
        key = stats_cache_key(user.id)
        cached = await cache.get_json(key)
        if cached:
            return MyStats.model_validate(cached)

        now = clock.now_utc()
        today = clock.local_today(now)
        from_date = today - timedelta(days=HEATMAP_DAYS - 1)
        days = await submission_repository.daily_totals(
            user.id, clock.start_of_local_day(from_date), clock.tz_name())

        rows = await submission_repository.totals_by_recitation_for_user(user.id)
        recs = await recitation_repository.get_many({rid for rid, _ in rows})
        by_recitation = []
        for rid, n in rows:
            rec = recs.get(rid)
            by_recitation.append(RecitationStat(
                recitation_id=rid,
                name=rec.english_name if rec else "Recitation",
                urdu_name=rec.urdu_name if rec else None,
                category=(rec.category if rec and rec.category else "Other"),
                count=n,
            ))

        gid = await community_service.resolve_group(user, None)
        week = await weekly_repository.get_active(gid)
        streak = await streak_service.compute(user.id, today)

        result = MyStats(
            timezone=clock.tz_name(),
            from_date=from_date,
            to_date=today,
            today=await submission_repository.sum_for_user_since_all_groups(
                user.id, clock.start_of_local_day(today)),
            week=(await submission_repository.sum_for_user_week(week.id, user.id)) if week else 0,
            month=await submission_repository.sum_for_user_since_all_groups(
                user.id, clock.start_of_month(now)),
            lifetime=sum(n for _, n in rows),
            current_streak=streak.current,
            best_streak=streak.best,
            active_days=len(days),
            days=[DayTotal(date=d, total=n) for d, n in days],
            by_recitation=by_recitation,
            generated_at=now,
        )
        await cache.set_json(key, result.model_dump(mode="json"), ttl=STATS_CACHE_SECONDS)
        return result


stats_service = StatsService()
