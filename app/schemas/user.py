"""User schemas."""
from __future__ import annotations

from beanie import PydanticObjectId
from pydantic import BaseModel, Field

from app.schemas.common import ORMModel


# "av1:" + dot-separated part codes (e.g. av1:k2.h3.c1), or a preset aura id.
AVATAR_PATTERN = r"^(av1:[a-z][0-9]{1,2}(\.[a-z][0-9]{1,2}){0,23}|aura_[0-9]{2})$"


class PublicUserOut(ORMModel):
    """A user as OTHER users see them (leaderboards, search): no email."""
    id: PydanticObjectId
    display_name: str
    photo_url: str | None = None
    avatar: str | None = None
    role: str
    lang: str = "en"
    lifetime_total: int = 0
    streak_days: int = 0


class UserOut(PublicUserOut):
    """A user as they see themselves, or as a super admin sees them."""
    email: str | None = None


class UserUpdate(BaseModel):
    display_name: str | None = None
    photo_url: str | None = None
    avatar: str | None = Field(default=None, max_length=120, pattern=AVATAR_PATTERN)
    lang: str | None = Field(default=None, pattern="^(en|hi|ur)$")
    preferences: dict | None = None


class RoleUpdate(BaseModel):
    role: str  # member | super_member | super_admin


class UserListResponse(BaseModel):
    items: list[UserOut]
    next_cursor: str | None = None
    total: int
