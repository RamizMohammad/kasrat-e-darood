"""Dashboard & leaderboard endpoints."""
from __future__ import annotations

from beanie import PydanticObjectId
from fastapi import APIRouter, Depends, Query

from app.dependencies.auth import get_current_user
from app.models.user import User
from app.schemas.dashboard import (
    DashboardResponse,
    FeedItemOut,
    LeaderboardResponse,
)
from app.services.community_service import community_service
from app.services.dashboard_service import dashboard_service
from app.services.leaderboard_service import leaderboard_service

router = APIRouter(tags=["dashboard"])


@router.get("/dashboard", response_model=DashboardResponse)
async def get_dashboard(
    group_id: PydanticObjectId | None = None,
    user: User = Depends(get_current_user),
) -> DashboardResponse:
    """Return the entire home dashboard in one optimized response.

    When ``group_id`` is omitted the shared community group is used.
    """
    gid = await community_service.resolve_group(user, group_id)
    return await dashboard_service.build(user, gid)


@router.get("/leaderboards", response_model=LeaderboardResponse)
async def get_leaderboard(
    group_id: PydanticObjectId | None = None,
    scope: str = Query(default="weekly"),
    around_me: bool = False,
    limit: int = Query(default=100, le=100),
    user: User = Depends(get_current_user),
) -> LeaderboardResponse:
    gid = await community_service.resolve_group(user, group_id)
    return await leaderboard_service.weekly(gid, user.id, around_me, limit)


@router.get("/feed", response_model=list[FeedItemOut])
async def get_feed(
    group_id: PydanticObjectId | None = None,
    limit: int = Query(default=20, le=50),
    user: User = Depends(get_current_user),
) -> list[FeedItemOut]:
    """Community activity feed. Defaults to the shared community group."""
    gid = await community_service.resolve_group(user, group_id)
    return await dashboard_service.feed(gid, limit)
