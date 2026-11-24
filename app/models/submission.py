"""Submissions: the core write of the platform."""
from __future__ import annotations

import uuid

from sqlalchemy import CheckConstraint, Index, Integer, String, text as sql_text
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import AuditMixin, Base, uuid_col


class Submission(AuditMixin, Base):
    __tablename__ = "submissions"

    # Logs belong to the user, not a group: one log counts on the global board
    # and on every group board the user is in. group_id only records where it
    # was logged from (None for the usual case).
    group_id: Mapped[uuid.UUID | None] = uuid_col(fk="groups.id", nullable=True)
    # Always a week of the one global week series.
    week_id: Mapped[uuid.UUID] = uuid_col(fk="weekly_sessions.id")
    user_id: Mapped[uuid.UUID] = uuid_col(fk="users.id")
    recitation_id: Mapped[uuid.UUID] = uuid_col(fk="recitations.id")
    count: Mapped[int] = mapped_column(Integer, nullable=False)
    note: Mapped[str | None] = mapped_column(String(500))
    source: Mapped[str] = mapped_column(String(20), default="app", server_default="app", nullable=False)
    client_uuid: Mapped[str | None] = mapped_column(String(64))  # idempotency key

    __table_args__ = (
        CheckConstraint("count > 0", name="count_positive"),
        Index("uq_submissions_user_client_uuid", "user_id", "client_uuid", unique=True,
              postgresql_where=sql_text("client_uuid IS NOT NULL")),
        Index("ix_submissions_week_user", "week_id", "user_id"),
        Index("ix_submissions_group_recitation", "group_id", "recitation_id"),
        Index("ix_submissions_user_created", "user_id", sql_text("created_at DESC")),
        Index("ix_submissions_week_user_created", "week_id", "user_id", "created_at",
              postgresql_where=sql_text("deleted = false")),
    )
