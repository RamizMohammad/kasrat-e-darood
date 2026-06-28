"""In-app notification feed (Activity / Notifications / Considerations)."""
from __future__ import annotations

from fastapi import APIRouter, Depends, Query

from app.dependencies.auth import get_current_user
from app.models.user import User
from app.schemas.dashboard import FeedItemOut
from app.services.notification_service import notification_service

router = APIRouter(tags=["notifications"])


@router.get("/notifications", response_model=list[FeedItemOut])
async def list_notifications(
    category: str = Query(default="notifications",
                          description="activity | notifications | considerations"),
    user: User = Depends(get_current_user),
) -> list[FeedItemOut]:
    return await notification_service.feed(user, category)
