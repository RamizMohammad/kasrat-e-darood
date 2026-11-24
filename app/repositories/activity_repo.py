"""Activity feed repository."""
from __future__ import annotations

import uuid

from app.database.postgres import db
from app.models.activity import ActivityItem
from app.repositories.base import BaseRepository


class ActivityRepository(BaseRepository[ActivityItem]):
    def __init__(self) -> None:
        super().__init__(ActivityItem)

    async def recent(self, group_id: uuid.UUID, limit: int = 10,
                     types: set[str] | None = None) -> list[ActivityItem]:
        stmt = self.live().where(ActivityItem.group_id == group_id)
        if types:
            stmt = stmt.where(ActivityItem.type.in_(types))
        stmt = stmt.order_by(ActivityItem.created_at.desc()).limit(limit)
        return list((await db().execute(stmt)).scalars().all())


activity_repository = ActivityRepository()
