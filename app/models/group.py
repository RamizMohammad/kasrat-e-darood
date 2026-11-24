"""Groups, memberships and everything that hangs off a group."""
from __future__ import annotations

import uuid
from datetime import datetime
from enum import Enum

from sqlalchemy import BigInteger, Boolean, DateTime, Index, Integer, String, Text, text as sql_text
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import AuditMixin, Base, enum_col, json_dict, json_list, uuid_col


class Privacy(str, Enum):
    PUBLIC = "public"
    PRIVATE = "private"


class JoinRule(str, Enum):
    CODE = "code"            # anyone with the code (or a public group) joins at once
    APPROVAL = "approval"    # an admin or moderator approves each request


class MemberRole(str, Enum):
    OWNER = "owner"
    ADMIN = "admin"
    MODERATOR = "moderator"
    MEMBER = "member"
    VIEWER = "viewer"        # kept for old rows; not offered in the app


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
    # Group avatar from the app's renderer, e.g. "grp1:c2.p4" (no uploads).
    avatar: Mapped[str | None] = mapped_column(String(120))
    privacy: Mapped[Privacy] = enum_col(Privacy, Privacy.PRIVATE)
    join_rule: Mapped[JoinRule] = enum_col(JoinRule, JoinRule.CODE)
    invite_code: Mapped[str] = mapped_column(String(32), unique=True, nullable=False)
    # No foreign key: the global community group is owned by a system sentinel id.
    owner_id: Mapped[uuid.UUID] = uuid_col()
    timezone: Mapped[str] = mapped_column(String(64), default="Asia/Kolkata",
                                          server_default="Asia/Kolkata", nullable=False)
    hijri_method: Mapped[str] = mapped_column(String(32), default="umm_al_qura",
                                              server_default="umm_al_qura", nullable=False)
    member_count: Mapped[int] = mapped_column(Integer, default=1, server_default="1", nullable=False)
    member_limit: Mapped[int] = mapped_column(Integer, default=500, server_default="500", nullable=False)
    weekly_goal: Mapped[int | None] = mapped_column(BigInteger)
    # Members see only the group total and their own number.
    total_only: Mapped[bool] = mapped_column(Boolean, default=False, server_default="false", nullable=False)
    # The one app-wide circle everyone belongs to ("Noor Community").
    is_global: Mapped[bool] = mapped_column(Boolean, default=False, server_default="false", nullable=False)
    settings: Mapped[dict] = json_dict()

    __table_args__ = (
        Index("uq_groups_global", "is_global", unique=True, postgresql_where=sql_text("is_global")),
        Index("ix_groups_owner", "owner_id", postgresql_where=sql_text("deleted = false")),
        Index("ix_groups_discover", "privacy", sql_text("created_at DESC"),
              postgresql_where=sql_text("deleted = false AND is_global = false")),
        # Discover search: words in the name or description, any script.
        Index("ix_groups_search",
              sql_text("to_tsvector('simple', name || ' ' || coalesce(description, ''))"),
              postgresql_using="gin"),
    )


class Membership(AuditMixin, Base):
    """A user in a group. ``created_at`` is the join time: boards count a
    member's logs from then on."""
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
        Index("ix_memberships_group_status", "group_id", "status"),
    )


class RequestStatus(str, Enum):
    PENDING = "pending"
    APPROVED = "approved"
    DECLINED = "declined"


class JoinRequest(AuditMixin, Base):
    """Asking to join a group whose join rule is "approval"."""
    __tablename__ = "join_requests"

    group_id: Mapped[uuid.UUID] = uuid_col(fk="groups.id")
    user_id: Mapped[uuid.UUID] = uuid_col(fk="users.id")
    status: Mapped[RequestStatus] = enum_col(RequestStatus, RequestStatus.PENDING)
    decided_by: Mapped[uuid.UUID | None] = uuid_col(nullable=True)
    decided_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))

    __table_args__ = (
        Index("uq_join_requests_pending", "group_id", "user_id", unique=True,
              postgresql_where=sql_text("status = 'pending' AND deleted = false")),
        Index("ix_join_requests_group_status", "group_id", "status", sql_text("created_at DESC")),
        Index("ix_join_requests_user_created", "user_id", sql_text("created_at DESC")),
    )


class GroupAnnouncement(AuditMixin, Base):
    __tablename__ = "group_announcements"

    group_id: Mapped[uuid.UUID] = uuid_col(fk="groups.id")
    author_id: Mapped[uuid.UUID] = uuid_col(fk="users.id")
    text: Mapped[str] = mapped_column(Text, nullable=False)
    pinned: Mapped[bool] = mapped_column(Boolean, default=True, server_default="true", nullable=False)

    __table_args__ = (
        Index("uq_group_announcements_pinned", "group_id", unique=True,
              postgresql_where=sql_text("pinned AND deleted = false")),
        Index("ix_group_announcements_group_created", "group_id", sql_text("created_at DESC")),
    )


class GroupAuditLog(AuditMixin, Base):
    """Who did what to whom (removals, bans, role changes...). Owner-visible."""
    __tablename__ = "group_audit_log"

    group_id: Mapped[uuid.UUID] = uuid_col(fk="groups.id")
    actor_id: Mapped[uuid.UUID] = uuid_col()
    action: Mapped[str] = mapped_column(String(40), nullable=False)
    target_id: Mapped[uuid.UUID | None] = uuid_col(nullable=True)
    details: Mapped[dict] = json_dict()

    __table_args__ = (Index("ix_group_audit_log_group_created", "group_id", sql_text("created_at DESC")),)


class GroupWeek(AuditMixin, Base):
    """One group in one (global) week: when its goal was reached, and the
    frozen total and top 3 once the week closes."""
    __tablename__ = "group_weeks"

    group_id: Mapped[uuid.UUID] = uuid_col(fk="groups.id")
    week_id: Mapped[uuid.UUID] = uuid_col(fk="weekly_sessions.id")
    week_number: Mapped[int] = mapped_column(Integer, nullable=False)
    total: Mapped[int] = mapped_column(BigInteger, default=0, server_default="0", nullable=False)
    top: Mapped[list] = json_list()          # [{user_id, total, rank}] at close
    goal: Mapped[int | None] = mapped_column(BigInteger)
    goal_reached_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    closed_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))

    __table_args__ = (
        Index("uq_group_weeks_group_week", "group_id", "week_id", unique=True),
        Index("ix_group_weeks_group_number", "group_id", sql_text("week_number DESC")),
    )
