"""Weekly session repository."""
from __future__ import annotations

from beanie import PydanticObjectId

from app.models.weekly_session import WeeklySession, WeekStatus
from app.repositories.base import BaseRepository


class WeeklySessionRepository(BaseRepository[WeeklySession]):
    def __init__(self) -> None:
        super().__init__(WeeklySession)

    async def get_active(self, group_id: PydanticObjectId) -> WeeklySession | None:
        return await WeeklySession.find_one(
            WeeklySession.group_id == group_id,
            WeeklySession.status == WeekStatus.ACTIVE,
            WeeklySession.deleted == False,  # noqa: E712
        )

    async def latest_week_number(self, group_id: PydanticObjectId) -> int:
        latest = await WeeklySession.find(
            WeeklySession.group_id == group_id
        ).sort("-week_number").limit(1).to_list()
        return latest[0].week_number if latest else 0

    async def history(self, group_id: PydanticObjectId,
                      cursor: PydanticObjectId | None, limit: int):
        return await self.list_paginated(
            WeeklySession.group_id == group_id,
            WeeklySession.status != WeekStatus.ACTIVE,
            cursor=cursor, limit=limit,
        )


weekly_repository = WeeklySessionRepository()
