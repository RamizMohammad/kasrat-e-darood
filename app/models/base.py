"""SQLAlchemy declarative base with the audit / soft-delete columns every table has."""
from __future__ import annotations

import enum
import uuid
from datetime import datetime, timezone
from typing import Any

from sqlalchemy import BigInteger, Boolean, DateTime, Integer, MetaData, func
from sqlalchemy import Enum as SAEnum
from sqlalchemy.dialects.postgresql import JSONB, UUID
from sqlalchemy.ext.mutable import MutableDict, MutableList
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column


def utcnow() -> datetime:
    return datetime.now(timezone.utc)


# Stable constraint names so Alembic migrations are predictable.
NAMING = {
    "ix": "ix_%(table_name)s_%(column_0_N_name)s",
    "uq": "uq_%(table_name)s_%(column_0_N_name)s",
    "ck": "ck_%(table_name)s_%(constraint_name)s",
    "fk": "fk_%(table_name)s_%(column_0_name)s_%(referred_table_name)s",
    "pk": "pk_%(table_name)s",
}


class Base(DeclarativeBase):
    metadata = MetaData(naming_convention=NAMING)


# --- Column helpers ----------------------------------------------------------
def uuid_pk() -> Mapped[uuid.UUID]:
    return mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)


def uuid_col(*, nullable: bool = False, index: bool = False, fk: str | None = None):
    from sqlalchemy import ForeignKey
    args = [ForeignKey(fk)] if fk else []
    return mapped_column(UUID(as_uuid=True), *args, nullable=nullable, index=index)


def enum_col(enum_cls: type[enum.Enum], default: enum.Enum, length: int = 20):
    """Enum stored as its string value (VARCHAR + CHECK), not a native PG enum."""
    return mapped_column(
        SAEnum(enum_cls, native_enum=False, length=length, validate_strings=True,
               values_callable=lambda e: [m.value for m in e],
               name=f"{enum_cls.__name__.lower()}_enum"),
        default=default, server_default=default.value, nullable=False,
    )


def json_dict():
    return mapped_column(MutableDict.as_mutable(JSONB), default=dict,
                         server_default="{}", nullable=False)


def json_list():
    return mapped_column(MutableList.as_mutable(JSONB), default=list,
                         server_default="[]", nullable=False)


def counter():
    return mapped_column(BigInteger, default=0, server_default="0", nullable=False)


class AuditMixin:
    """Audit, soft-delete and version columns shared by every table."""

    id: Mapped[uuid.UUID] = uuid_pk()
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), default=utcnow, server_default=func.now(), nullable=False)
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), default=utcnow, onupdate=utcnow,
        server_default=func.now(), nullable=False)
    created_by: Mapped[uuid.UUID | None] = mapped_column(UUID(as_uuid=True), nullable=True)
    updated_by: Mapped[uuid.UUID | None] = mapped_column(UUID(as_uuid=True), nullable=True)
    deleted: Mapped[bool] = mapped_column(
        Boolean, default=False, server_default="false", nullable=False)
    version: Mapped[int] = mapped_column(
        Integer, default=1, server_default="1", nullable=False)

    def __init__(self, **kwargs: Any) -> None:
        # Apply Python-side column defaults immediately (not only at flush), so
        # code that builds an object can read e.g. `.id` or `.preferences` at once.
        for col in self.__table__.columns:  # type: ignore[attr-defined]
            default = col.default
            if col.key in kwargs or default is None:
                continue
            if default.is_callable:
                kwargs[col.key] = default.arg(None)  # SQLAlchemy wraps it to take a context
            elif default.is_scalar:
                kwargs[col.key] = default.arg
        super().__init__(**kwargs)

    def touch(self, by: uuid.UUID | None = None) -> None:
        """Update audit fields prior to a write."""
        self.updated_at = utcnow()
        self.version = (self.version or 1) + 1
        if by is not None:
            self.updated_by = by
