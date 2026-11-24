"""Invite codes: preview (works signed out) and join."""
from __future__ import annotations

from fastapi import APIRouter, Depends, Path

from app.dependencies.auth import get_current_user, get_optional_user
from app.models.user import User
from app.schemas.group import GroupPreview, JoinResult
from app.services.join_service import join_service

router = APIRouter(prefix="/invites", tags=["invites"])

_CODE = Path(min_length=4, max_length=12, pattern=r"^[A-Za-z0-9 ]+$")


@router.get("/{code}", response_model=GroupPreview)
async def preview(code: str = _CODE, user: User | None = Depends(get_optional_user)) -> GroupPreview:
    """Join preview: group card, members, this week's total and goal, top 3
    (unless total-only). ``viewer_status`` says if you're already in, asked,
    or banned. 404 code=invalid_code, 410 code=group_deleted."""
    return await join_service.preview_code(code, user)


@router.post("/{code}/join", response_model=JoinResult)
async def join(code: str = _CODE, user: User = Depends(get_current_user)) -> JoinResult:
    """Join, or ask to join when an admin approves joins (status=requested).
    Errors: banned (403), already_member / group_full / request_too_soon (409),
    rate_limited (429, 20 joins an hour)."""
    return await join_service.join_code(user, code)
