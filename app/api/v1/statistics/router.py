"""Community statistics + manual week management (Stats page)."""
from __future__ import annotations

from beanie import PydanticObjectId
from fastapi import APIRouter, Depends

from app.core.exceptions import PermissionDeniedError
from app.dependencies.auth import get_current_user
from app.models.user import User
from app.schemas.auth import OkResponse
from app.schemas.statistics import CommunityStats, WeekSummary
from app.services.community_service import community_service
from app.services.stats_service import can_manage_weeks, stats_service
from app.services.weekly_service import weekly_service

router = APIRouter(prefix="/statistics", tags=["statistics"])


@router.get("/community", response_model=CommunityStats)
async def community_stats(
    week_id: PydanticObjectId | None = None,
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
async def close_week(user: User = Depends(get_current_user)) -> OkResponse:
    """Finalize the current week and start a fresh one. Super members/admins only."""
    if not can_manage_weeks(user):
        raise PermissionDeniedError("Only super members or admins can close the week")
    gid = await community_service.resolve_group(user, None)
    await weekly_service.lock_week(gid, user.id)
    return OkResponse()
