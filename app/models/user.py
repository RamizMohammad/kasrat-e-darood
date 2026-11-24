"""Users."""
from __future__ import annotations

from enum import Enum

from sqlalchemy import Index, String, text as sql_text
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import AuditMixin, Base, counter, enum_col, json_dict


class GlobalRole(str, Enum):
    SUPER_ADMIN = "super_admin"
    SUPER_MEMBER = "super_member"
    MEMBER = "member"


class User(AuditMixin, Base):
    __tablename__ = "users"

    # Sign-in identity: "google:<sub>", "pwd:<email>", a Firebase uid, or
    # "deleted:<id>" after account deletion.
    firebase_uid: Mapped[str] = mapped_column(String(200), unique=True, nullable=False)
    display_name: Mapped[str] = mapped_column(String(120), nullable=False)
    email: Mapped[str | None] = mapped_column(String(254))
    phone: Mapped[str | None] = mapped_column(String(32))
    photo_url: Mapped[str | None] = mapped_column(String(500))
    # Illustrated avatar from the avatar studio ("av1:k2.h3…") or a preset
    # ("aura_01"…"aura_12"). Replaces photo uploads from v3 on.
    avatar: Mapped[str | None] = mapped_column(String(120))
    password_hash: Mapped[str | None] = mapped_column(String(200))  # email/password accounts
    lang: Mapped[str] = mapped_column(String(5), default="en", server_default="en", nullable=False)
    role: Mapped[GlobalRole] = enum_col(GlobalRole, GlobalRole.MEMBER)
    lifetime_total: Mapped[int] = counter()
    # Consecutive local (IST) days with at least one log, ending today or
    # yesterday; best_streak is the longest run ever. Kept up to date on every
    # log/undo and reset nightly for users who missed a day.
    streak_days: Mapped[int] = counter()
    best_streak: Mapped[int] = counter()
    last_active_at: Mapped[str | None] = mapped_column(String(40))
    preferences: Mapped[dict] = json_dict()

    __table_args__ = (
        # One live account per email (deleted accounts have email cleared).
        Index("uq_users_email_live", "email", unique=True,
              postgresql_where=sql_text("deleted = false AND email IS NOT NULL")),
        Index("ix_users_display_name_lower", sql_text("lower(display_name)")),
    )
