"""Group, membership and weekly-session endpoints."""
from __future__ import annotations

from beanie import PydanticObjectId
from fastapi import APIRouter, Depends, Query, Response, status

from app.dependencies.auth import get_current_user, require_permission
from app.models.group import Membership
from app.models.user import User
from app.schemas.group import (
    GroupCreate,
    GroupOut,
    GroupUpdate,
    JoinRequest,
    MembershipOut,
)
from app.schemas.weekly import WeekLockResult, WeeklySessionOut
from app.security.permissions import Permission
from app.services.group_service import group_service
from app.services.weekly_service import weekly_service

router = APIRouter(prefix="/groups", tags=["groups"])


@router.post("", response_model=GroupOut, status_code=status.HTTP_201_CREATED)
async def create_group(
    body: GroupCreate, user: User = Depends(get_current_user)
) -> GroupOut:
    group = await group_service.create_group(user, body)
    return GroupOut.model_validate(group)


@router.get("", response_model=list[GroupOut])
async def my_groups(user: User = Depends(get_current_user)) -> list[GroupOut]:
    groups = await group_service.list_my_groups(user)
    return [GroupOut.model_validate(g) for g in groups]


@router.post("/join", response_model=MembershipOut)
async def join_group(
    body: JoinRequest, user: User = Depends(get_current_user)
) -> MembershipOut:
    membership = await group_service.join_by_code(user, body.invite_code)
    return MembershipOut.model_validate(membership)


@router.get("/{group_id}", response_model=GroupOut)
async def get_group(
    group_id: PydanticObjectId, _: User = Depends(get_current_user)
) -> GroupOut:
    return GroupOut.model_validate(await group_service.get_group(group_id))


@router.patch("/{group_id}", response_model=GroupOut)
async def update_group(
    group_id: PydanticObjectId, body: GroupUpdate,
    membership: Membership = Depends(require_permission(Permission.GROUP_MANAGE)),
    user: User = Depends(get_current_user),
) -> GroupOut:
    return GroupOut.model_validate(
        await group_service.update_group(group_id, body, user)
    )


@router.post("/{group_id}/leave", status_code=status.HTTP_204_NO_CONTENT, response_class=Response)
async def leave_group(
    group_id: PydanticObjectId, user: User = Depends(get_current_user)
) -> Response:
    await group_service.leave_group(user, group_id)
    return Response(status_code=status.HTTP_204_NO_CONTENT)


@router.post("/{group_id}/invite-code")
async def rotate_invite_code(
    group_id: PydanticObjectId,
    membership: Membership = Depends(require_permission(Permission.GROUP_MANAGE)),
) -> dict:
    return {"invite_code": await group_service.rotate_invite_code(group_id)}


@router.get("/{group_id}/members", response_model=list[MembershipOut])
async def list_members(
    group_id: PydanticObjectId, _: User = Depends(get_current_user)
) -> list[MembershipOut]:
    members = await group_service.list_members(group_id)
    return [MembershipOut.model_validate(m) for m in members]


# --- Weekly session sub-resource ----------------------------------------------
@router.get("/{group_id}/week/current", response_model=WeeklySessionOut)
async def current_week(
    group_id: PydanticObjectId, _: User = Depends(get_current_user)
) -> WeeklySessionOut:
    return WeeklySessionOut.model_validate(await weekly_service.get_current(group_id))


@router.get("/{group_id}/week/history", response_model=list[WeeklySessionOut])
async def week_history(
    group_id: PydanticObjectId,
    cursor: PydanticObjectId | None = None,
    limit: int = Query(default=20, le=100),
    _: User = Depends(get_current_user),
) -> list[WeeklySessionOut]:
    items, _next = await weekly_service.history(group_id, cursor, limit)
    return [WeeklySessionOut.model_validate(w) for w in items]


@router.post("/{group_id}/week/present", response_model=WeekLockResult)
async def present_weekly_recitations(
    group_id: PydanticObjectId,
    membership: Membership = Depends(require_permission(Permission.WEEK_LOCK)),
    user: User = Depends(get_current_user),
) -> WeekLockResult:
    """Friday lock: finalize the week, build Hall of Fame, open the next week."""
    locked, hall, new_week = await weekly_service.lock_week(group_id, user.id)
    return WeekLockResult(
        locked_week=WeeklySessionOut.model_validate(locked),
        hall_of_fame=hall,
        new_week=WeeklySessionOut.model_validate(new_week),
    )
