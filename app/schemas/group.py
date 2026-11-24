"""Group schemas."""
from __future__ import annotations

import uuid
from pydantic import BaseModel, Field

from app.schemas.common import ORMModel


class GroupCreate(BaseModel):
    name: str = Field(min_length=2, max_length=80)
    description: str | None = None
    privacy: str = "private"
    timezone: str = "Asia/Kolkata"


class GroupUpdate(BaseModel):
    name: str | None = None
    description: str | None = None
    privacy: str | None = None
    banner_url: str | None = None
    logo_url: str | None = None


class GroupOut(ORMModel):
    id: uuid.UUID
    name: str
    description: str | None = None
    banner_url: str | None = None
    logo_url: str | None = None
    privacy: str
    invite_code: str
    owner_id: uuid.UUID
    member_count: int


class JoinRequest(BaseModel):
    invite_code: str


class MembershipOut(ORMModel):
    id: uuid.UUID
    group_id: uuid.UUID
    user_id: uuid.UUID
    role: str
    status: str
