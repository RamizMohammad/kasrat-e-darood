"""Community statistics schemas (Stats page)."""
from __future__ import annotations

from datetime import datetime

from beanie import PydanticObjectId
from pydantic import BaseModel


class RecitationStat(BaseModel):
    recitation_id: PydanticObjectId
    name: str
    urdu_name: str | None = None
    category: str | None = None
    count: int


class CategoryStat(BaseModel):
    category: str
    count: int


class CommunityStats(BaseModel):
    week_id: PydanticObjectId | None = None
    week_number: int | None = None
    label: str | None = None
    status: str | None = None
    total: int = 0
    by_recitation: list[RecitationStat] = []
    by_category: list[CategoryStat] = []
    can_manage: bool = False  # current user may close the week


class WeekSummary(BaseModel):
    week_id: PydanticObjectId
    week_number: int
    label: str | None = None
    total: int = 0
    closed_at: datetime | None = None
