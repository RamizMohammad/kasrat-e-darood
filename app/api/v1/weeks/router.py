"""The current week and Hall of Fame."""
from __future__ import annotations

from fastapi import APIRouter, Depends, Query, Response

from app.api.v1.paging import set_next
from app.dependencies.auth import get_current_user
from app.models.user import User
from app.schemas.board import HallOfFameWeek, WeekInfo
from app.services.board_service import board_service, week_info
from app.services.weekly_service import weekly_service

router = APIRouter(prefix="/weeks", tags=["weeks"])


@router.get("/current", response_model=WeekInfo)
async def current_week(_: User = Depends(get_current_user)) -> WeekInfo:
    """This week: number, start, and when it closes (Friday, around Maghrib)."""
    info = week_info(await weekly_service.current())
    assert info is not None
    return info


@router.get("/hall-of-fame", response_model=list[HallOfFameWeek])
async def hall_of_fame(
    response: Response,
    cursor: str | None = None,
    limit: int = Query(default=20, ge=1, le=50),
    user: User = Depends(get_current_user),
) -> list[HallOfFameWeek]:
    """Past weeks, newest first, with each week's top 3. Next page:
    ``?cursor=`` + the X-Next-Cursor header."""
    items, next_cursor = await board_service.hall_of_fame(user, cursor, limit)
    set_next(response, next_cursor)
    return items
