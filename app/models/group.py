"""Groups and memberships."""
from __future__ import annotations

import uuid
from enum import Enum

from sqlalchemy import Index, Integer, String, text as sql_text
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import AuditMixin, Base, enum_col, json_dict, uuid_col


class Privacy(str, Enum):
    PUBLIC = "public"
    PRIVATE = "private"


class MemberRole(str, Enum):
    OWNER = "owner"
    ADMIN = "admin"
    MODERATOR = "moderator"
    MEMBER = "member"
    VIEWER = "viewer"


class MemberStatus(str, Enum):
    ACTIVE = "active"
    PENDING = "pending"
    BANNED = "banned"
    MUTED = "muted"


class Group(AuditMixin, Base):
    __tablename__ = "groups"

    name: Mapped[str] = mapped_column(String(120), nullable=False)
    description: Mapped[str | None] = mapped_column(String(500))
    banner_url: Mapped[str | None] = mapped_column(String(500))
    logo_url: Mapped[str | None] = mapped_column(String(500))
    privacy: Mapped[Privacy] = enum_col(Privacy, Privacy.PRIVATE)
    invite_code: Mapped[str] = mapped_column(String(32), unique=True, nullable=False)
    # No foreign key: the global community group is owned by a system sentinel id.
    owner_id: Mapped[uuid.UUID] = uuid_col()
    timezone: Mapped[str] = mapped_column(String(64), default="Asia/Kolkata",
                                          server_default="Asia/Kolkata", nullable=False)
    hijri_method: Mapped[str] = mapped_column(String(32), default="umm_al_qura",
                                              server_default="umm_al_qura", nullable=False)
    member_count: Mapped[int] = mapped_column(Integer, default=1, server_default="1", nullable=False)
    settings: Mapped[dict] = json_dict()


class Membership(AuditMixin, Base):
    __tablename__ = "memberships"

    group_id: Mapped[uuid.UUID] = uuid_col(fk="groups.id")
    user_id: Mapped[uuid.UUID] = uuid_col(fk="users.id", index=True)
    role: Mapped[MemberRole] = enum_col(MemberRole, MemberRole.MEMBER)
    status: Mapped[MemberStatus] = enum_col(MemberStatus, MemberStatus.ACTIVE)

    __table_args__ = (
        # One live membership per (group, user); leaving soft-deletes it so a
        # later re-join can insert a fresh row.
        Index("uq_memberships_group_user_live", "group_id", "user_id", unique=True,
              postgresql_where=sql_text("deleted = false")),
        Index("ix_memberships_group_role", "group_id", "role"),
    )
