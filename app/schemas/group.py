"""Group schemas."""
from __future__ import annotations

from beanie import PydanticObjectId
from pydantic import BaseModel, Field

from app.schemas.common import ORMModel


class GroupCreate(BaseModel):
    name: str = Field(min_length=2, max_length=80)
    description: str | None = None
    privacy: str = "private"
    timezone: str = "Asia/Karachi"


class GroupUpdate(BaseModel):
    name: str | None = None
    description: str | None = None
    privacy: str | None = None
    banner_url: str | None = None
    logo_url: str | None = None


class GroupOut(ORMModel):
    id: PydanticObjectId
    name: str
    description: str | None = None
    banner_url: str | None = None
    logo_url: str | None = None
    privacy: str
    invite_code: str
    owner_id: PydanticObjectId
    member_count: int


class JoinRequest(BaseModel):
    invite_code: str


class MembershipOut(ORMModel):
    id: PydanticObjectId
    group_id: PydanticObjectId
    user_id: PydanticObjectId
    role: str
    status: str
