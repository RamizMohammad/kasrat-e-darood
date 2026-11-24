"""Beta programme signups."""
from __future__ import annotations

from enum import Enum

from sqlalchemy import Boolean, Index, String, Text
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import AuditMixin, Base, enum_col


class BetaTrack(str, Enum):
    CLOSED = "closed"
    OPEN = "open"


class SignupStatus(str, Enum):
    PENDING = "pending"
    INVITED = "invited"
    JOINED = "joined"
    REJECTED = "rejected"


class BetaSignup(AuditMixin, Base):
    __tablename__ = "beta_signups"

    full_name: Mapped[str] = mapped_column(String(120), nullable=False)
    email: Mapped[str] = mapped_column(String(254), unique=True, nullable=False)
    google_email: Mapped[str | None] = mapped_column(String(254))  # Play Store tester account
    phone: Mapped[str | None] = mapped_column(String(32))
    country: Mapped[str | None] = mapped_column(String(80))
    device: Mapped[str] = mapped_column(String(16), default="android", server_default="android", nullable=False)
    how_heard: Mapped[str | None] = mapped_column(String(200))
    reason: Mapped[str | None] = mapped_column(Text)
    track: Mapped[BetaTrack] = enum_col(BetaTrack, BetaTrack.CLOSED)
    status: Mapped[SignupStatus] = enum_col(SignupStatus, SignupStatus.PENDING)
    consent: Mapped[bool] = mapped_column(Boolean, default=True, server_default="true", nullable=False)
    lang: Mapped[str] = mapped_column(String(5), default="en", server_default="en", nullable=False)

    __table_args__ = (Index("ix_beta_signups_track_status", "track", "status"),)
