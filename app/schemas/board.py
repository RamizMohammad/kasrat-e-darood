"""Leaderboards (global, group, groups-vs-groups) and Hall of Fame."""
from __future__ import annotations

import uuid
from datetime import datetime

from pydantic import BaseModel

from app.schemas.common import ORMModel


class MiniUser(ORMModel):
    """A person as shown inside a group (members list, requests, audit)."""
    id: uuid.UUID
    display_name: str
    avatar: str | None = None
    photo_url: str | None = None


class BoardUser(BaseModel):
    """A person on a leaderboard. Someone who chose "hide me on leaderboards"
    shows as ``hidden`` with no id, the name "A member" and no picture —
    except to themselves."""
    id: uuid.UUID | None
    display_name: str
    avatar: str | None = None
    photo_url: str | None = None
    hidden: bool = False


class BoardEntry(BaseModel):
    rank: int | None              # None on a total-only group board
    total: int
    user: BoardUser
    is_me: bool = False
    previous_rank: int | None = None   # last week's rank (global week board)


class WeekInfo(BaseModel):
    id: uuid.UUID
    week_number: int
    starts_at: datetime
    closes_at: datetime


class BoardResponse(BaseModel):
    scope: str                    # week | all
    week: WeekInfo | None = None
    total: int = 0                # everyone on this board together
    entries: list[BoardEntry]
    me: BoardEntry | None = None  # always present when you're on the board
    total_only: bool = False
    goal: int | None = None
    member_count: int | None = None


class HallOfFameWeek(BaseModel):
    week_id: uuid.UUID
    week_number: int
    label: str | None = None
    starts_at: datetime
    closed_at: datetime | None = None
    total: int
    top: list[BoardEntry]
