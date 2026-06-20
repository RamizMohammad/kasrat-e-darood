"""Activity feed repository."""
from __future__ import annotations

from beanie import PydanticObjectId

from app.models.activity import ActivityItem
from app.repositories.base import BaseRepository


class ActivityRepository(BaseRepository[ActivityItem]):
    def __init__(self) -> None:
        super().__init__(ActivityItem)

    async def recent(self, group_id: PydanticObjectId, limit: int = 10):
        return await ActivityItem.find(
            ActivityItem.group_id == group_id,
            ActivityItem.deleted == False,  # noqa: E712
        ).sort("-created_at").limit(limit).to_list()


activity_repository = ActivityRepository()
