"""Submission repository with aggregation helpers."""
from __future__ import annotations

import uuid
from datetime import datetime

from sqlalchemy import and_, extract, func, or_, select

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

    # ── A user's own stats (GET /statistics/me) ───────────────────────────

    async def daily_totals(self, user_id: uuid.UUID, since: datetime, tz: str) -> list[tuple]:
        """[(local date, total)] for days with logs since ``since``, oldest first."""
        day = func.date(func.timezone(tz, Submission.created_at)).label("day")
        stmt = (select(day, func.sum(Submission.count))
                .where(_LIVE, Submission.user_id == user_id, Submission.created_at >= since)
                .group_by(day).order_by(day))
        return [(d, int(n)) for d, n in (await db().execute(stmt)).all()]

    async def totals_by_recitation_for_user(self, user_id: uuid.UUID) -> list[tuple]:
        """[(recitation_id, total)] across all time, biggest first."""
        total = func.sum(Submission.count).label("total")
        stmt = (select(Submission.recitation_id, total)
                .where(_LIVE, Submission.user_id == user_id)
                .group_by(Submission.recitation_id).order_by(total.desc()))
        return [(r, int(n)) for r, n in (await db().execute(stmt)).all()]

    async def sum_for_user_since_all_groups(self, user_id: uuid.UUID, since: datetime) -> int:
        return await self._sum(Submission.user_id == user_id, Submission.created_at >= since)

    async def page_for_user(self, user_id: uuid.UUID, limit: int,
                            before: tuple[datetime, uuid.UUID] | None) -> list[Submission]:
        """Newest first; ``before`` = (created_at, id) of the last row already shown."""
        stmt = self.live().where(Submission.user_id == user_id)
        if before is not None:
            ts, rid = before
            stmt = stmt.where(or_(Submission.created_at < ts,
                                  and_(Submission.created_at == ts, Submission.id < rid)))
        stmt = stmt.order_by(Submission.created_at.desc(), Submission.id.desc()).limit(limit)
        return list((await db().execute(stmt)).scalars().all())


submission_repository = SubmissionRepository()
