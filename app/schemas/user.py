"""User schemas."""
from __future__ import annotations

from beanie import PydanticObjectId
from pydantic import BaseModel

from app.schemas.common import ORMModel


class UserOut(ORMModel):
    id: PydanticObjectId
    display_name: str
    email: str | None = None
    photo_url: str | None = None
    role: str
    lifetime_total: int = 0
    streak_days: int = 0


class UserUpdate(BaseModel):
    display_name: str | None = None
    photo_url: str | None = None
    preferences: dict | None = None


class RoleUpdate(BaseModel):
    role: str  # member | super_member | super_admin


class UserListResponse(BaseModel):
    items: list[UserOut]
    next_cursor: str | None = None
    total: int
