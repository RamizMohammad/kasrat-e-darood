"""Submission document — the core write of the platform."""
from __future__ import annotations

import pymongo
from beanie import PydanticObjectId

from app.models.base import BaseDocument


class Submission(BaseDocument):
    group_id: PydanticObjectId
    week_id: PydanticObjectId
    user_id: PydanticObjectId
    recitation_id: PydanticObjectId
    count: int
    note: str | None = None
    source: str = "app"  # app | bulk | offline_sync
    client_uuid: str | None = None  # idempotency key

    class Settings:
        name = "submissions"
        indexes = [
            pymongo.IndexModel(
                [("user_id", pymongo.ASCENDING), ("client_uuid", pymongo.ASCENDING)],
                unique=True,
                partialFilterExpression={"client_uuid": {"$type": "string"}},
                name="uq_user_client_uuid",
            ),
            [("week_id", pymongo.ASCENDING), ("user_id", pymongo.ASCENDING)],
            [("group_id", pymongo.ASCENDING), ("recitation_id", pymongo.ASCENDING)],
            [("created_at", pymongo.DESCENDING)],
        ]
