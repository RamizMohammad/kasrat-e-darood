"""Group and membership documents."""
from __future__ import annotations

from enum import Enum

import pymongo
from beanie import Indexed, PydanticObjectId
from pydantic import Field

from app.models.base import BaseDocument


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


class Group(BaseDocument):
    name: str
    description: str | None = None
    banner_url: str | None = None
    logo_url: str | None = None
    privacy: Privacy = Privacy.PRIVATE
    invite_code: Indexed(str, unique=True)  # type: ignore[valid-type]
    owner_id: PydanticObjectId
    timezone: str = "Asia/Karachi"
    hijri_method: str = "umm_al_qura"
    member_count: int = 1
    settings: dict = Field(default_factory=dict)

    class Settings:
        name = "groups"
        indexes = [[("name", pymongo.TEXT), ("description", pymongo.TEXT)]]


class Membership(BaseDocument):
    group_id: PydanticObjectId
    user_id: PydanticObjectId
    role: MemberRole = MemberRole.MEMBER
    status: MemberStatus = MemberStatus.ACTIVE

    class Settings:
        name = "memberships"
        indexes = [
            pymongo.IndexModel(
                [("group_id", pymongo.ASCENDING), ("user_id", pymongo.ASCENDING)],
                unique=True,
                name="uq_group_user",
            ),
            [("group_id", pymongo.ASCENDING), ("role", pymongo.ASCENDING)],
        ]
