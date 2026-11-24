"""Generic repository: soft-delete-aware CRUD over SQLAlchemy models.

Every repository works on the session of the current request/job (``db()``).
"""
from __future__ import annotations

import uuid
from datetime import datetime
from typing import Any, Generic, Type, TypeVar

from sqlalchemy import Select, and_, or_, select
from sqlalchemy.exc import IntegrityError

from app.database.postgres import db
from app.models.base import AuditMixin

TModel = TypeVar("TModel", bound=AuditMixin)


def encode_cursor(created_at: datetime, row_id: uuid.UUID) -> str:
    return f"{created_at.isoformat()}|{row_id}"


def decode_cursor(cursor: str) -> tuple[datetime, uuid.UUID] | None:
    try:
        ts, rid = cursor.split("|", 1)
        return datetime.fromisoformat(ts), uuid.UUID(rid)
    except (ValueError, AttributeError):
        return None


class BaseRepository(Generic[TModel]):
    """Base class for repositories. All SQL for a model lives in its repository."""

    model: Type[TModel]

    def __init__(self, model: Type[TModel]) -> None:
        self.model = model

    def live(self) -> Select:
        """SELECT of the model's rows that aren't soft-deleted."""
        return select(self.model).where(self.model.deleted.is_(False))

    async def get(self, row_id: uuid.UUID | None) -> TModel | None:
        if row_id is None:
            return None
        row = await db().get(self.model, row_id)
        if row is None or row.deleted:
            return None
        return row

    async def first(self, *where: Any) -> TModel | None:
        result = await db().execute(self.live().where(*where).limit(1))
        return result.scalars().first()

    async def create(self, row: TModel) -> TModel:
        db().add(row)
        await db().flush()
        return row

    async def create_unique(self, row: TModel) -> TModel | None:
        """Insert inside a savepoint; return None (and keep the transaction
        usable) if a unique constraint says the row already exists."""
        try:
            async with db().begin_nested():
                db().add(row)
                await db().flush()
        except IntegrityError:
            return None
        return row

    async def save(self, row: TModel) -> TModel:
        row.touch()
        db().add(row)
        await db().flush()
        return row

    async def soft_delete(self, row: TModel) -> None:
        row.deleted = True
        row.touch()
        await db().flush()

    async def list_paginated(
        self, *where: Any, cursor: str | None = None, limit: int = 20,
    ) -> tuple[list[TModel], str | None]:
        """Newest first, keyset-paginated on (created_at, id). Returns (items, next_cursor)."""
        stmt = self.live().where(*where)
        after = decode_cursor(cursor) if cursor else None
        if after is not None:
            ts, rid = after
            m = self.model
            stmt = stmt.where(or_(m.created_at < ts, and_(m.created_at == ts, m.id < rid)))
        stmt = stmt.order_by(self.model.created_at.desc(), self.model.id.desc()).limit(limit + 1)
        items = list((await db().execute(stmt)).scalars().all())
        next_cursor = None
        if len(items) > limit:
            items = items[:limit]
            last = items[-1]
            next_cursor = encode_cursor(last.created_at, last.id)
        return items, next_cursor
