"""Submission repository with aggregation helpers."""
from __future__ import annotations

import uuid
from datetime import datetime

from sqlalchemy import extract, func, select

from app.database.postgres import db
from app.models.submission import Submission
from app.repositories.base import BaseRepository

_LIVE = Submission.deleted.is_(False)


class SubmissionRepository(BaseRepository[Submission]):
    def __init__(self) -> None:
        super().__init__(Submission)

    async def recent_for_user(self, user_id: uuid.UUID, limit: int = 20) -> list[Submission]:
        stmt = (self.live().where(Submission.user_id == user_id)
                .order_by(Submission.created_at.desc()).limit(limit))
        return list((await db().execute(stmt)).scalars().all())

    async def find_by_client_uuid(self, user_id: uuid.UUID, client_uuid: str) -> Submission | None:
        return await self.first(Submission.user_id == user_id,
                                Submission.client_uuid == client_uuid)

    async def _sum(self, *where) -> int:
        stmt = select(func.coalesce(func.sum(Submission.count), 0)).where(_LIVE, *where)
        return int((await db().execute(stmt)).scalar_one())

    async def sum_for_user_week(self, week_id: uuid.UUID, user_id: uuid.UUID) -> int:
        return await self._sum(Submission.week_id == week_id, Submission.user_id == user_id)

    async def sum_for_user_since(self, user_id: uuid.UUID, group_id: uuid.UUID,
                                 since: datetime) -> int:
        return await self._sum(Submission.user_id == user_id, Submission.group_id == group_id,
                               Submission.created_at >= since)

    async def totals_by_recitation(self, week_id: uuid.UUID) -> list[dict]:
        """[{_id: recitation_id, total: n}] for a week, biggest first."""
        total = func.sum(Submission.count).label("total")
        stmt = (select(Submission.recitation_id, total)
                .where(_LIVE, Submission.week_id == week_id)
                .group_by(Submission.recitation_id)
                .order_by(total.desc()))
        return [{"_id": rid, "total": int(t)} for rid, t in (await db().execute(stmt)).all()]

    async def leaderboard_for_week(self, week_id: uuid.UUID) -> list[dict]:
        """[{_id: user_id, total: n}] for a week, biggest first (ties: earliest first log)."""
        total = func.sum(Submission.count).label("total")
        first = func.min(Submission.created_at)
        stmt = (select(Submission.user_id, total)
                .where(_LIVE, Submission.week_id == week_id)
                .group_by(Submission.user_id)
                .order_by(total.desc(), first.asc()))
        return [{"_id": uid, "total": int(t)} for uid, t in (await db().execute(stmt)).all()]

    async def daily_chart(self, week_id: uuid.UUID, user_id: uuid.UUID) -> list[dict]:
        """[{_id: day 1..7 (1 = Sunday, UTC), value: n}] for one user's week."""
        dow = (extract("dow", Submission.created_at) + 1).label("dow")
        stmt = (select(dow, func.sum(Submission.count))
                .where(_LIVE, Submission.week_id == week_id, Submission.user_id == user_id)
                .group_by(dow))
        return [{"_id": int(d), "value": int(v)} for d, v in (await db().execute(stmt)).all()]


submission_repository = SubmissionRepository()
