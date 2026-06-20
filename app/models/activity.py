"""Activity feed, leaderboard snapshot, notification, device token, refresh token."""
from __future__ import annotations

from datetime import datetime

import pymongo
from beanie import PydanticObjectId
from pydantic import Field

from app.models.base import BaseDocument


class ActivityItem(BaseDocument):
    group_id: PydanticObjectId
    actor_id: PydanticObjectId
    type: str
    text: str
    payload: dict = Field(default_factory=dict)
    reactions: dict = Field(default_factory=dict)

    class Settings:
        name = "activity_feed"
        indexes = [[("group_id", pymongo.ASCENDING), ("created_at", pymongo.DESCENDING)]]


class LeaderboardSnapshot(BaseDocument):
    group_id: PydanticObjectId
    scope: str  # daily | weekly | monthly | yearly | lifetime
    period_key: str
    entries: list = Field(default_factory=list)
    generated_at: datetime

    class Settings:
        name = "leaderboards"
        indexes = [
            pymongo.IndexModel(
                [("group_id", pymongo.ASCENDING), ("scope", pymongo.ASCENDING),
                 ("period_key", pymongo.ASCENDING)],
                unique=True, name="uq_leaderboard_period",
            ),
        ]


class Notification(BaseDocument):
    user_id: PydanticObjectId
    type: str
    title: str
    body: str
    data: dict = Field(default_factory=dict)
    read: bool = False

    class Settings:
        name = "notifications"
        indexes = [[("user_id", pymongo.ASCENDING), ("created_at", pymongo.DESCENDING)]]


class DeviceToken(BaseDocument):
    user_id: PydanticObjectId
    fcm_token: str
    platform: str = "android"

    class Settings:
        name = "device_tokens"
        indexes = [
            pymongo.IndexModel([("fcm_token", pymongo.ASCENDING)], unique=True,
                               name="uq_fcm_token"),
        ]


class RefreshToken(BaseDocument):
    user_id: PydanticObjectId
    token_hash: str
    revoked: bool = False
    expires_at: datetime

    class Settings:
        name = "refresh_tokens"
        indexes = [
            [("token_hash", pymongo.ASCENDING)],
            pymongo.IndexModel([("expires_at", pymongo.ASCENDING)],
                               expireAfterSeconds=0, name="ttl_refresh"),
        ]
