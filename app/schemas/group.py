"""Group schemas (v3)."""
from __future__ import annotations

import uuid
from datetime import datetime
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, field_validator

from app.models.group import JoinRule, MemberRole, Privacy
from app.schemas.board import BoardEntry, MiniUser, WeekInfo
from app.schemas.common import ORMModel

# "grp1:" + part codes, like user avatars ("av1:...").
GROUP_AVATAR_PATTERN = r"^grp1:[a-z][0-9]{1,2}(\.[a-z][0-9]{1,2}){0,11}$"


def _clean_name(v: str | None) -> str | None:
    if v is None:
        return v
    v = " ".join(v.split())          # trim + collapse runs of spaces; any script is fine
    if not 3 <= len(v) <= 40:
        raise ValueError("Name must be 3–40 characters")
    return v


def _clean_desc(v: str | None) -> str | None:
    if v is None:
        return v
    v = " ".join(v.split())
    return v or None


class _Strict(BaseModel):
    model_config = ConfigDict(extra="forbid")


class GroupCreate(_Strict):
    name: str
    description: str | None = Field(default=None, max_length=200)
    avatar: str | None = Field(default=None, pattern=GROUP_AVATAR_PATTERN)
    privacy: Privacy = Privacy.PRIVATE
    join_rule: JoinRule = JoinRule.CODE
    weekly_goal: int | None = Field(default=None, ge=1, le=1_000_000_000)
    total_only: bool = False
    timezone: str = "Asia/Kolkata"            # accepted for old clients; unused

    @field_validator("name")
    @classmethod
    def _name(cls, v):
        return _clean_name(v)

    @field_validator("description")
    @classmethod
    def _desc(cls, v):
        return _clean_desc(v)


class GroupUpdate(_Strict):
    """Partial: only the fields you send change. ``weekly_goal: null`` clears it."""
    name: str | None = None
    description: str | None = Field(default=None, max_length=200)
    avatar: str | None = Field(default=None, pattern=GROUP_AVATAR_PATTERN)
    privacy: Privacy | None = None
    join_rule: JoinRule | None = None
    weekly_goal: int | None = Field(default=None, ge=1, le=1_000_000_000)
    total_only: bool | None = None

    @field_validator("name")
    @classmethod
    def _name(cls, v):
        return _clean_name(v)

    @field_validator("description")
    @classmethod
    def _desc(cls, v):
        return _clean_desc(v)


class GroupCard(ORMModel):
    id: uuid.UUID
    name: str
    description: str | None = None
    avatar: str | None = None
    privacy: Privacy
    join_rule: JoinRule
    member_count: int
    member_limit: int
    weekly_goal: int | None = None
    total_only: bool = False
    is_global: bool = False
    created_at: datetime
    week_total: int = 0


class MyGroup(GroupCard):
    role: MemberRole
    my_week_total: int = 0
    joined_at: datetime | None = None


class AnnouncementOut(BaseModel):
    id: uuid.UUID
    text: str
    author: MiniUser | None = None
    created_at: datetime


class LastWeek(BaseModel):
    """Last closed week: the top 3 wear a quiet badge until the next close."""
    week_number: int
    total: int
    top: list[BoardEntry]
    goal_reached: bool = False


class GroupDetail(MyGroup):
    owner_id: uuid.UUID
    invite_code: str
    invite_link: str
    week: WeekInfo | None = None
    goal_reached: bool = False
    pending_requests: int | None = None     # only for those who can decide
    announcement: AnnouncementOut | None = None
    last_week: LastWeek | None = None


ViewerStatus = Literal["none", "member", "requested", "banned"]


class GroupPreview(GroupCard):
    """What someone outside the group sees (Join preview, Discover)."""
    full: bool = False
    top: list[BoardEntry] = []              # empty when the group is total-only
    viewer_status: ViewerStatus = "none"


class JoinResult(BaseModel):
    status: Literal["joined", "requested"]
    group: GroupCard


class InviteOut(BaseModel):
    code: str
    link: str
    share_text: str


class DeleteGroupBody(_Strict):
    confirm_name: str


class GroupRankEntry(BaseModel):
    rank: int
    group: GroupCard
    total: int
    average: float
    is_mine: bool = False


class GroupRanks(BaseModel):
    metric: Literal["total", "avg"]
    week: WeekInfo | None = None
    entries: list[GroupRankEntry]
    mine: list[GroupRankEntry]


class JoinRequestOut(BaseModel):
    id: uuid.UUID
    user: MiniUser
    created_at: datetime


class DecideMany(_Strict):
    user_ids: list[uuid.UUID] = Field(min_length=1, max_length=200)


class DecideResult(BaseModel):
    approved: int = 0
    skipped: int = 0


class MemberOut(BaseModel):
    user: MiniUser
    role: MemberRole
    status: str
    joined_at: datetime
    week_total: int = 0


class RoleChange(_Strict):
    role: Literal["admin", "moderator", "member"]


class TransferBody(_Strict):
    user_id: uuid.UUID


class AnnouncementCreate(_Strict):
    text: str = Field(min_length=1, max_length=1000)

    @field_validator("text")
    @classmethod
    def _strip(cls, v: str) -> str:
        v = v.strip()
        if not v:
            raise ValueError("Announcement is empty")
        return v


class GroupActivityOut(BaseModel):
    id: uuid.UUID
    type: str
    text: str
    actor: MiniUser | None = None
    payload: dict = {}
    created_at: datetime


class AuditOut(BaseModel):
    id: uuid.UUID
    action: str
    actor: MiniUser | None = None
    target: MiniUser | None = None
    details: dict = {}
    created_at: datetime


class GroupsThisWeek(BaseModel):
    """Home carousel: one card per group you're in."""
    id: uuid.UUID
    name: str
    avatar: str | None = None
    week_total: int
    weekly_goal: int | None = None
    member_count: int
    role: MemberRole


# Old v2 shapes, still used by a few helpers.
class GroupOut(ORMModel):
    id: uuid.UUID
    name: str
    description: str | None = None
    privacy: str
    invite_code: str
    owner_id: uuid.UUID
    member_count: int


class MembershipOut(ORMModel):
    id: uuid.UUID
    group_id: uuid.UUID
    user_id: uuid.UUID
    role: str
    status: str
