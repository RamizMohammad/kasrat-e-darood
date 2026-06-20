"""Weekly session document. At most one ACTIVE per group (partial unique index)."""
from __future__ import annotations

from datetime import datetime
from enum import Enum

import pymongo
from beanie import PydanticObjectId
from pydantic import Field

from app.models.base import BaseDocument


class WeekStatus(str, Enum):
    ACTIVE = "ACTIVE"
    LOCKED = "LOCKED"
    ARCHIVED = "ARCHIVED"


class WeeklySession(BaseDocument):
    group_id: PydanticObjectId
    week_number: int
    hijri_week: str | None = None
    gregorian_week: str | None = None
    start_date: datetime
    end_date: datetime
    status: WeekStatus = WeekStatus.ACTIVE
    totals: dict = Field(default_factory=dict)  # {user_id: count}
    group_total: int = 0
    locked_at: datetime | None = None
    locked_by: PydanticObjectId | None = None
    hall_of_fame: list = Field(default_factory=list)

    class Settings:
        name = "weekly_sessions"
        indexes = [
            pymongo.IndexModel(
                [("group_id", pymongo.ASCENDING)],
                unique=True,
                partialFilterExpression={"status": "ACTIVE"},
                name="uq_active_week_per_group",
            ),
            [("group_id", pymongo.ASCENDING), ("week_number", pymongo.DESCENDING)],
        ]
