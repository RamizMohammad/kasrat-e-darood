"""Weekly session repository."""
from __future__ import annotations

import uuid

from sqlalchemy import func, select

from app.database.postgres import db
from app.models.weekly_session import WeeklySession, WeekStatus
from app.repositories.base import BaseRepository


class WeeklySessionRepository(BaseRepository[WeeklySession]):
    def __init__(self) -> None:
        super().__init__(WeeklySession)

    async def get_active(self, group_id: uuid.UUID, *, for_update: bool = False) -> WeeklySession | None:
        stmt = self.live().where(WeeklySession.group_id == group_id,
                                 WeeklySession.status == WeekStatus.ACTIVE).limit(1)
        if for_update:
            stmt = stmt.with_for_update()
        return (await db().execute(stmt)).scalars().first()

    async def latest_week_number(self, group_id: uuid.UUID) -> int:
        stmt = select(func.max(WeeklySession.week_number)).where(WeeklySession.group_id == group_id)
        return int((await db().execute(stmt)).scalar() or 0)

    async def closed_for_group(self, group_id: uuid.UUID) -> list[WeeklySession]:
        stmt = (self.live().where(WeeklySession.group_id == group_id,
                                  WeeklySession.status != WeekStatus.ACTIVE)
                .order_by(WeeklySession.week_number.desc()))
        return list((await db().execute(stmt)).scalars().all())

    async def history(self, group_id: uuid.UUID, cursor: str | None, limit: int):
        return await self.list_paginated(
            WeeklySession.group_id == group_id,
            WeeklySession.status != WeekStatus.ACTIVE,
            cursor=cursor, limit=limit,
        )


weekly_repository = WeeklySessionRepository()
