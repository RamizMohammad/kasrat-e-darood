"""Weekly session schemas."""
from __future__ import annotations

from datetime import datetime

import uuid
from pydantic import BaseModel

from app.schemas.common import ORMModel


class WeeklySessionOut(ORMModel):
    id: uuid.UUID
    group_id: uuid.UUID
    week_number: int
    start_date: datetime
    end_date: datetime
    status: str
    group_total: int


class HallOfFameEntry(BaseModel):
    user_id: uuid.UUID
    display_name: str
    total: int
    rank: int


class WeekLockResult(BaseModel):
    locked_week: WeeklySessionOut
    hall_of_fame: list[HallOfFameEntry]
    new_week: WeeklySessionOut
