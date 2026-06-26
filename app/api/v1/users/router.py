"""User endpoints."""
from __future__ import annotations

from beanie import PydanticObjectId
from fastapi import APIRouter, Depends

from app.core.exceptions import NotFoundError, PermissionDeniedError, ValidationAppError
from app.dependencies.auth import get_current_user
from app.models.user import GlobalRole, User
from app.repositories.user_repo import user_repository
from app.schemas.user import RoleUpdate, UserOut, UserUpdate

router = APIRouter(prefix="/users", tags=["users"])


@router.patch("/{user_id}/role", response_model=UserOut)
async def set_user_role(
    user_id: PydanticObjectId,
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
    for key, value in body.model_dump(exclude_unset=True).items():
        setattr(user, key, value)
    await user_repository.save(user)
    return UserOut.model_validate(user)


@router.get("/search", response_model=list[UserOut])
async def search_users(
    q: str, _: User = Depends(get_current_user)
) -> list[UserOut]:
    users = await user_repository.search(q)
    return [UserOut.model_validate(u) for u in users]


@router.get("/{user_id}", response_model=UserOut)
async def get_user(
    user_id: PydanticObjectId, _: User = Depends(get_current_user)
) -> UserOut:
    target = await user_repository.get(user_id)
    if target is None:
        raise NotFoundError("User not found")
    return UserOut.model_validate(target)
