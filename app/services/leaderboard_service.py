"""Leaderboard read-model with Redis caching and tie-aware ranking."""
from __future__ import annotations

from beanie import PydanticObjectId

from app.database.redis import cache
from app.repositories.submission_repo import submission_repository
from app.repositories.user_repo import user_repository
from app.repositories.weekly_repo import weekly_repository
from app.schemas.dashboard import LeaderboardEntry, LeaderboardResponse
from app.schemas.user import UserOut


class LeaderboardService:
    async def weekly(
        self, group_id: PydanticObjectId, me_id: PydanticObjectId,
        around_me: bool = False, limit: int = 100,
    ) -> LeaderboardResponse:
        cache_key = f"leaderboard:{group_id}:weekly:{limit}"
        cached = await cache.get_json(cache_key)
        week = await weekly_repository.get_active(group_id)
        if week is None:
            return LeaderboardResponse(scope="weekly", entries=[], me=None)

        rows = await submission_repository.leaderboard_for_week(week.id)
        entries: list[LeaderboardEntry] = []
        me_entry: LeaderboardEntry | None = None
        prev_total: int | None = None
        rank = 0
        for index, row in enumerate(rows, start=1):
            total = int(row["total"])
            # Standard competition ranking (ties share a rank).
            if total != prev_total:
                rank = index
                prev_total = total
            user = await user_repository.get(row["_id"])
            if user is None:
                continue
            entry = LeaderboardEntry(
                user=UserOut.model_validate(user), total=total, rank=rank,
                streak=user.streak_days,
            )
            if user.id == me_id:
                me_entry = entry
            if len(entries) < limit:
                entries.append(entry)

        result = LeaderboardResponse(scope="weekly", entries=entries, me=me_entry)
        await cache.set_json(cache_key, result.model_dump())
        return result


leaderboard_service = LeaderboardService()
