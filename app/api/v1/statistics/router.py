"""Community statistics + manual week management (Stats page)."""
from __future__ import annotations

import uuid
from fastapi import APIRouter, Depends

from app.core.exceptions import PermissionDeniedError
from app.dependencies.auth import get_current_user
from app.models.user import User
from app.schemas.auth import OkResponse
from app.schemas.statistics import CommunityStats, MyStats, WeekSummary
from app.services.stats_service import can_manage_weeks, stats_service
from app.services.weekly_service import weekly_service

router = APIRouter(prefix="/statistics", tags=["statistics"])


@router.get("/me", response_model=MyStats)
async def my_stats(user: User = Depends(get_current_user)) -> MyStats:
    """Your own numbers for the Me tab: a 365-day heat map (local days),
    totals per recitation, today / week / month / lifetime and your current
    and best streak. Cached for 60 s; logging or undoing refreshes it."""
    return await stats_service.me(user)


@router.get("/community", response_model=CommunityStats)
async def community_stats(
    week_id: uuid.UUID | None = None,
    user: User = Depends(get_current_user),
) -> CommunityStats:
    """Community totals + per-recitation/per-category breakdown for a week.

    Defaults to the current (active) week when ``week_id`` is omitted.
    """
    return await stats_service.community(user, week_id=week_id)


@router.get("/weeks", response_model=list[WeekSummary])
async def week_history(user: User = Depends(get_current_user)) -> list[WeekSummary]:
    """All past (closed) weeks, newest first."""
    return await stats_service.weeks(user)


@router.post("/close-week", response_model=OkResponse)
async def close_week(
    week_id: uuid.UUID | None = None,
    user: User = Depends(get_current_user),
) -> OkResponse:
    """Close the week now (it otherwise closes itself on Friday around
    Maghrib). Super members/admins only. Pass the ``week_id`` you showed in
    the confirm dialog: if that week is already closed, nothing happens."""
    if not can_manage_weeks(user):
        raise PermissionDeniedError("Only super members or admins can close the week")
    await weekly_service.close_week(user.id, expected_week_id=week_id)
    return OkResponse()
