"""In-app notification feed (Activity / Notifications / Considerations)."""
from __future__ import annotations

from fastapi import APIRouter, Depends, Query, Response
from sqlalchemy import update

from app.api.v1.paging import check_keyset, set_next
from app.database.postgres import db
from app.models.activity import Notification
from app.models.base import utcnow
from app.repositories.base import BaseRepository
from app.schemas.auth import OkResponse
from app.schemas.notification import InboxItem

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


_inbox = BaseRepository(Notification)


@router.get("/notifications/inbox", response_model=list[InboxItem])
async def inbox(
    response: Response,
    cursor: str | None = None,
    limit: int = Query(default=30, ge=1, le=100),
    user: User = Depends(get_current_user),
) -> list[InboxItem]:
    """Your personal notifications (join requests, approvals, role changes...),
    newest first. ``data`` carries what to open (group_id, user_id, type)."""
    check_keyset(cursor)
    rows, nxt = await _inbox.list_paginated(Notification.user_id == user.id, cursor=cursor, limit=limit)
    set_next(response, nxt)
    return [InboxItem.model_validate(r) for r in rows]


@router.post("/notifications/inbox/read", response_model=OkResponse)
async def mark_read(user: User = Depends(get_current_user)) -> OkResponse:
    await db().execute(update(Notification)
                       .where(Notification.user_id == user.id, Notification.read.is_(False))
                       .values(read=True, updated_at=utcnow()))
    return OkResponse()
