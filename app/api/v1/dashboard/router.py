"""Dashboard & leaderboard endpoints."""
from __future__ import annotations

from beanie import PydanticObjectId
from fastapi import APIRouter, Depends, Query

from app.dependencies.auth import get_current_user
from app.models.user import User
from app.schemas.dashboard import DashboardResponse, LeaderboardResponse
from app.services.dashboard_service import dashboard_service
from app.services.leaderboard_service import leaderboard_service

router = APIRouter(tags=["dashboard"])


@router.get("/dashboard", response_model=DashboardResponse)
async def get_dashboard(
    group_id: PydanticObjectId, user: User = Depends(get_current_user)
) -> DashboardResponse:
    """Return the entire home dashboard in one optimized response."""
    return await dashboard_service.build(user, group_id)


@router.get("/leaderboards", response_model=LeaderboardResponse)
async def get_leaderboard(
    group_id: PydanticObjectId,
    scope: str = Query(default="weekly"),
    around_me: bool = False,
    limit: int = Query(default=100, le=100),
    user: User = Depends(get_current_user),
) -> LeaderboardResponse:
    return await leaderboard_service.weekly(group_id, user.id, around_me, limit)
