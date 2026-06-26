"""User document."""
from __future__ import annotations

from enum import Enum

import pymongo
from beanie import Indexed
from pydantic import Field

from app.models.base import BaseDocument


class GlobalRole(str, Enum):
    SUPER_ADMIN = "super_admin"
    SUPER_MEMBER = "super_member"
    MEMBER = "member"


class User(BaseDocument):
    firebase_uid: Indexed(str, unique=True)  # type: ignore[valid-type]
    display_name: str
    email: str | None = None
    phone: str | None = None
    photo_url: str | None = None
    password_hash: str | None = None  # set for email/password accounts
    lang: str = "en"  # preferred language: en | hi | ur (used for emails + UI)
    role: GlobalRole = GlobalRole.MEMBER
    lifetime_total: int = 0
    streak_days: int = 0
    last_active_at: str | None = None
    preferences: dict = Field(default_factory=dict)

    class Settings:
        name = "users"
        indexes = [
            [("display_name", pymongo.TEXT)],
            [("email", pymongo.ASCENDING)],
        ]
