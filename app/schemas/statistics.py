"""Community statistics schemas (Stats page)."""
from __future__ import annotations

from datetime import date, datetime

import uuid
from pydantic import BaseModel


class RecitationStat(BaseModel):
    recitation_id: uuid.UUID
    name: str
    urdu_name: str | None = None
    category: str | None = None
    count: int


class CategoryStat(BaseModel):
    category: str
    count: int


class CommunityStats(BaseModel):
    week_id: uuid.UUID | None = None
    week_number: int | None = None
    label: str | None = None
    status: str | None = None
    total: int = 0
    by_recitation: list[RecitationStat] = []
    by_category: list[CategoryStat] = []
    can_manage: bool = False  # current user may close the week


class WeekSummary(BaseModel):
    week_id: uuid.UUID
    week_number: int
    label: str | None = None
    total: int = 0
    closed_at: datetime | None = None


# ── GET /statistics/me ──────────────────────────────────────────────────────

class DayTotal(BaseModel):
    date: date          # a local (IST) calendar day, "2026-11-24"
    total: int


class MyStats(BaseModel):
    """Everything the Me tab shows, in one response.

    ``days`` is sparse: only days with at least one log, oldest first, within
    [from_date, to_date] (the last 365 local days). Missing days are zero.
    """
    timezone: str
    from_date: date
    to_date: date
    today: int = 0
    week: int = 0          # this community week (same as Home's "This week")
    month: int = 0         # this calendar month, local time
    lifetime: int = 0
    current_streak: int = 0
    best_streak: int = 0
    active_days: int = 0   # days with a log within the 365-day window
    days: list[DayTotal] = []
    by_recitation: list[RecitationStat] = []
    generated_at: datetime
