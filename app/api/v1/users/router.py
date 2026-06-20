"""User endpoints."""
from __future__ import annotations

from beanie import PydanticObjectId
from fastapi import APIRouter, Depends

from app.core.exceptions import NotFoundError
from app.dependencies.auth import get_current_user
from app.models.user import User
from app.repositories.user_repo import user_repository
from app.schemas.user import UserOut, UserUpdate

router = APIRouter(prefix="/users", tags=["users"])


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
