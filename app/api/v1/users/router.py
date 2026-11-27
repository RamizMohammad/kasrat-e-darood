"""User endpoints."""
from __future__ import annotations

import uuid
from fastapi import APIRouter, Depends, Query

from app.core.exceptions import NotFoundError, PermissionDeniedError, ValidationAppError
from app.dependencies.auth import get_current_user
from app.models.user import GlobalRole, User
from app.repositories.user_repo import user_repository
from app.schemas.auth import OkResponse
from app.schemas.preferences import Preferences
from app.services.device_service import device_service
from app.schemas.user import (
    DeviceIn, PublicUserOut, RoleUpdate, UserListResponse, UserOut, UserUpdate,
)

router = APIRouter(prefix="/users", tags=["users"])


def _require_super_admin(actor: User) -> None:
    if actor.role != GlobalRole.SUPER_ADMIN:
        raise PermissionDeniedError("Super admin access required")


@router.get("", response_model=UserListResponse)
async def list_users(
    cursor: str | None = None,
    limit: int = Query(default=50, le=200),
    actor: User = Depends(get_current_user),
) -> UserListResponse:
    """List all users (super admin only), cursor-paginated, for the admin panel."""
    _require_super_admin(actor)
    items, next_cursor = await user_repository.list_paginated(cursor=cursor, limit=limit)
    total = await user_repository.count_all()
    return UserListResponse(
        items=[UserOut.model_validate(u) for u in items],
        next_cursor=next_cursor,
        total=total,
    )


@router.patch("/{user_id}/role", response_model=UserOut)
async def set_user_role(
    user_id: uuid.UUID,
    body: RoleUpdate,
    actor: User = Depends(get_current_user),
) -> UserOut:
    """Change a user's global role. Restricted to super admins."""
    if actor.role != GlobalRole.SUPER_ADMIN:
        raise PermissionDeniedError("Only a super admin can change roles")
    try:
        new_role = GlobalRole(body.role)
    except ValueError:
        raise ValidationAppError(
            "Invalid role. Use one of: member, super_member, super_admin"
        )
    target = await user_repository.get(user_id)
    if target is None:
        raise NotFoundError("User not found")
    target.role = new_role
    await user_repository.save(target)
    return UserOut.model_validate(target)


@router.get("/me", response_model=UserOut)
async def get_me(user: User = Depends(get_current_user)) -> UserOut:
    return UserOut.model_validate(user)


@router.patch("/me", response_model=UserOut)
async def update_me(
    body: UserUpdate, user: User = Depends(get_current_user)
) -> UserOut:
    changes = body.model_dump(exclude_unset=True, exclude={"preferences"})
    groups_before = Preferences.from_stored(user.preferences).notify.groups
    if body.preferences is not None:
        current = Preferences.from_stored(user.preferences)
        changes["preferences"] = body.preferences.apply_to(current).model_dump()
    if changes:
        # The ORM only writes the columns that changed, so totals that a
        # concurrent submission just incremented are left alone.
        for key, value in changes.items():
            setattr(user, key, value)
        await user_repository.save(user)
    if Preferences.from_stored(user.preferences).notify.groups != groups_before:
        # Group pushes are topics: follow or drop every group topic.
        await device_service.sync_user(user)
    return UserOut.model_validate(user)


@router.post("/me/devices", response_model=OkResponse)
async def register_device(body: DeviceIn, user: User = Depends(get_current_user)) -> OkResponse:
    """Call on sign-in and whenever FCM gives the app a new token. The token is
    subscribed to the topics of every group you're in."""
    await device_service.register(user, body.token, body.platform)
    return OkResponse()


@router.delete("/me/devices/{token}", response_model=OkResponse)
async def unregister_device(token: str, user: User = Depends(get_current_user)) -> OkResponse:
    """Call on log out, so this phone stops getting your group pushes."""
    await device_service.unregister(user, token)
    return OkResponse()


@router.get("/search", response_model=list[PublicUserOut])
async def search_users(
    q: str = Query(min_length=1, max_length=80), _: User = Depends(get_current_user)
) -> list[PublicUserOut]:
    users = await user_repository.search(q)
    return [PublicUserOut.model_validate(u) for u in users]


@router.get("/{user_id}", response_model=UserOut | PublicUserOut)
async def get_user(
    user_id: uuid.UUID, actor: User = Depends(get_current_user)
) -> UserOut | PublicUserOut:
    target = await user_repository.get(user_id)
    if target is None:
        raise NotFoundError("User not found")
    # The user themselves and super admins get the full record (email,
    # preferences); everyone else only what boards and search already show.
    if actor.id == target.id or actor.role == GlobalRole.SUPER_ADMIN:
        return UserOut.model_validate(target)
    return PublicUserOut.model_validate(target)
