"""Weekly sessions. At most one ACTIVE per group (partial unique index)."""
from __future__ import annotations

import uuid
from datetime import datetime
from enum import Enum

from sqlalchemy import DateTime, Index, Integer, String, text as sql_text
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import AuditMixin, Base, counter, enum_col, json_dict, json_list, uuid_col


class WeekStatus(str, Enum):
    ACTIVE = "ACTIVE"
    LOCKED = "LOCKED"
    ARCHIVED = "ARCHIVED"


class WeeklySession(AuditMixin, Base):
    __tablename__ = "weekly_sessions"

    group_id: Mapped[uuid.UUID] = uuid_col(fk="groups.id")
    week_number: Mapped[int] = mapped_column(Integer, nullable=False)
    hijri_week: Mapped[str | None] = mapped_column(String(32))
    gregorian_week: Mapped[str | None] = mapped_column(String(16))
    start_date: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    end_date: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    status: Mapped[WeekStatus] = enum_col(WeekStatus, WeekStatus.ACTIVE)
    # Live group total (atomic increments); per-user totals are summed from
    # submissions while the week is open and frozen into `totals` when it locks.
    group_total: Mapped[int] = counter()
    totals: Mapped[dict] = json_dict()  # {user_id: count}, filled at lock
    locked_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    locked_by: Mapped[uuid.UUID | None] = uuid_col(nullable=True)
    hall_of_fame: Mapped[list] = json_list()

    __table_args__ = (
        Index("uq_weekly_sessions_active_per_group", "group_id", unique=True,
              postgresql_where=sql_text("status = 'ACTIVE' AND deleted = false")),
        Index("ix_weekly_sessions_group_week", "group_id", sql_text("week_number DESC")),
    )
