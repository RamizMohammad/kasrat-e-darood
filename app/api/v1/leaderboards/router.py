"""Global and group-vs-group leaderboards (Ranks tab)."""
from __future__ import annotations

from typing import Literal

from fastapi import APIRouter, Depends, Query

from app.dependencies.auth import get_current_user
from app.models.user import User
from app.schemas.board import BoardResponse
from app.schemas.group import GroupRanks
from app.services.board_service import board_service
from app.services.group_service import group_service

router = APIRouter(prefix="/leaderboards", tags=["leaderboards"])


@router.get("/global", response_model=BoardResponse)
async def global_board(
    scope: Literal["week", "all"] = "week",
    around_me: bool = Query(default=False, description="Rows around you instead of the top"),
    limit: int = Query(default=100, ge=1, le=200),
    user: User = Depends(get_current_user),
) -> BoardResponse:
    """Everyone, this week or all time. Ties share a rank; your own row is
    always in ``me``; people who hide themselves show as "A member"."""
    return await board_service.global_board(user, scope, around_me, limit)


@router.get("/groups", response_model=GroupRanks)
async def groups_board(
    metric: Literal["total", "avg"] = "total",
    limit: int = Query(default=100, ge=1, le=200),
    user: User = Depends(get_current_user),
) -> GroupRanks:
    """Public groups and your own, by this week's total or average per member."""
    return await group_service.ranks(user, metric, limit)
