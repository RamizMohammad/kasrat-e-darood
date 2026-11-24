"""Dashboard & leaderboard schemas."""
from __future__ import annotations

from datetime import datetime

import uuid
from pydantic import BaseModel

from app.schemas.group import GroupsThisWeek
from app.schemas.user import PublicUserOut
from app.schemas.weekly import WeeklySessionOut


class GoalProgress(BaseModel):
    target: int
    progress: int
    percent: int


class Contributor(BaseModel):
    user: PublicUserOut
    total: int
    rank: int


class ActivityOut(BaseModel):
    id: uuid.UUID
    actor_id: uuid.UUID
    type: str
    text: str
    reactions: dict = {}


class FeedItemOut(BaseModel):
    """An activity-feed entry enriched with the actor's display info."""
    id: uuid.UUID
    actor_id: uuid.UUID
    actor_name: str
    initial: str
    type: str
    text: str
    reactions: dict = {}
    created_at: datetime


class ChartPoint(BaseModel):
    day: str
    value: int


class DashboardResponse(BaseModel):
    current_week: WeeklySessionOut | None
    user_total: int
    today_total: int
    weekly_total: int
    monthly_total: int
    lifetime_total: int
    group_total: int
    remaining_days: int
    streak: int
    goal: GoalProgress
    top_contributors: list[Contributor]
    recent_activity: list[FeedItemOut]
    weekly_chart: list[ChartPoint]
    # v3: one card per group you're in (not the global circle), biggest first.
    groups_this_week: list[GroupsThisWeek] = []


class LeaderboardEntry(BaseModel):
    user: PublicUserOut
    total: int
    rank: int
    streak: int = 0


class LeaderboardResponse(BaseModel):
    scope: str
    entries: list[LeaderboardEntry]
    me: LeaderboardEntry | None = None
