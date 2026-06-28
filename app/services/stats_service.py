"""Community statistics: per-recitation + per-category breakdowns, week history."""
from __future__ import annotations

from beanie import PydanticObjectId

from app.models.user import GlobalRole, User
from app.models.weekly_session import WeeklySession, WeekStatus
from app.repositories.recitation_repo import recitation_repository
from app.repositories.submission_repo import submission_repository
from app.repositories.weekly_repo import weekly_repository
from app.schemas.statistics import (
    CategoryStat,
    CommunityStats,
    RecitationStat,
    WeekSummary,
)
from app.services.community_service import community_service

_MANAGER_ROLES = {GlobalRole.SUPER_MEMBER, GlobalRole.SUPER_ADMIN}


def can_manage_weeks(user: User) -> bool:
    return user.role in _MANAGER_ROLES


class StatsService:
    async def community(
        self,
        user: User,
        group_id: PydanticObjectId | None = None,
        week_id: PydanticObjectId | None = None,
    ) -> CommunityStats:
        gid = await community_service.resolve_group(user, group_id)
        week = (await weekly_repository.get(week_id) if week_id
                else await weekly_repository.get_active(gid))

        can_manage = can_manage_weeks(user)
        if week is None:
            return CommunityStats(total=0, can_manage=can_manage)

        rows = await submission_repository.totals_by_recitation(week.id)
        by_recitation: list[RecitationStat] = []
        by_category: dict[str, int] = {}
        total = 0
        name_cache: dict = {}
        for row in rows:
            rid = row["_id"]
            count = int(row["total"])
            total += count
            rec = name_cache.get(rid)
            if rec is None:
                rec = await recitation_repository.get(rid)
                name_cache[rid] = rec
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
        self, user: User, group_id: PydanticObjectId | None = None
    ) -> list[WeekSummary]:
        """All closed (non-active) weeks, newest first."""
        gid = await community_service.resolve_group(user, group_id)
        sessions = await WeeklySession.find(
            WeeklySession.group_id == gid,
            WeeklySession.status != WeekStatus.ACTIVE,
            WeeklySession.deleted == False,  # noqa: E712
        ).sort("-week_number").to_list()
        return [WeekSummary(
            week_id=s.id, week_number=s.week_number, label=s.gregorian_week,
            total=s.group_total, closed_at=s.locked_at,
        ) for s in sessions]


stats_service = StatsService()
