"""Activity feed, leaderboard snapshots, notifications, device + refresh tokens, OTP codes."""
from __future__ import annotations

import uuid
from datetime import datetime

from sqlalchemy import Boolean, DateTime, Index, Integer, String, Text, text as sql_text
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import AuditMixin, Base, json_dict, json_list, uuid_col


class ActivityItem(AuditMixin, Base):
    __tablename__ = "activity_feed"

    group_id: Mapped[uuid.UUID] = uuid_col(fk="groups.id")
    actor_id: Mapped[uuid.UUID] = uuid_col()  # no FK: system posts use the group owner sentinel
    type: Mapped[str] = mapped_column(String(40), nullable=False)
    text: Mapped[str] = mapped_column(Text, nullable=False)
    payload: Mapped[dict] = json_dict()
    reactions: Mapped[dict] = json_dict()

    __table_args__ = (Index("ix_activity_feed_group_created", "group_id", sql_text("created_at DESC")),)


class LeaderboardSnapshot(AuditMixin, Base):
    __tablename__ = "leaderboards"

    group_id: Mapped[uuid.UUID] = uuid_col(fk="groups.id")
    scope: Mapped[str] = mapped_column(String(16), nullable=False)  # daily | weekly | … | lifetime
    period_key: Mapped[str] = mapped_column(String(32), nullable=False)
    entries: Mapped[list] = json_list()
    generated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)

    __table_args__ = (Index("uq_leaderboards_period", "group_id", "scope", "period_key", unique=True),)


class Notification(AuditMixin, Base):
    __tablename__ = "notifications"

    user_id: Mapped[uuid.UUID] = uuid_col(fk="users.id")
    type: Mapped[str] = mapped_column(String(40), nullable=False)
    title: Mapped[str] = mapped_column(String(200), nullable=False)
    body: Mapped[str] = mapped_column(Text, nullable=False)
    data: Mapped[dict] = json_dict()
    read: Mapped[bool] = mapped_column(Boolean, default=False, server_default="false", nullable=False)

    __table_args__ = (Index("ix_notifications_user_created", "user_id", sql_text("created_at DESC")),)


class DeviceToken(AuditMixin, Base):
    __tablename__ = "device_tokens"

    user_id: Mapped[uuid.UUID] = uuid_col(fk="users.id", index=True)
    fcm_token: Mapped[str] = mapped_column(String(300), unique=True, nullable=False)
    platform: Mapped[str] = mapped_column(String(16), default="android", server_default="android", nullable=False)


class RefreshToken(AuditMixin, Base):
    __tablename__ = "refresh_tokens"

    user_id: Mapped[uuid.UUID] = uuid_col(fk="users.id", index=True)
    token_hash: Mapped[str] = mapped_column(String(64), unique=True, nullable=False)
    revoked: Mapped[bool] = mapped_column(Boolean, default=False, server_default="false", nullable=False)
    expires_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, index=True)


class PasswordResetCode(AuditMixin, Base):
    """A one-time OTP for a sensitive action. Only the code hash is stored.

    ``purpose`` distinguishes password resets from account deletions.
    """
    __tablename__ = "password_reset_codes"

    user_id: Mapped[uuid.UUID] = uuid_col(fk="users.id")
    email: Mapped[str] = mapped_column(String(254), nullable=False)
    code_hash: Mapped[str] = mapped_column(String(64), nullable=False)
    purpose: Mapped[str] = mapped_column(String(32), default="password_reset",
                                         server_default="password_reset", nullable=False)
    expires_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, index=True)
    used: Mapped[bool] = mapped_column(Boolean, default=False, server_default="false", nullable=False)
    attempts: Mapped[int] = mapped_column(Integer, default=0, server_default="0", nullable=False)

    __table_args__ = (Index("ix_password_reset_codes_lookup", "email", "purpose", sql_text("created_at DESC")),)
