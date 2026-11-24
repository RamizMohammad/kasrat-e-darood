"""FastAPI dependencies for authentication and per-group authorization."""
from __future__ import annotations

import uuid

from fastapi import Depends, Header
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.exceptions import NotFoundError, PermissionDeniedError, UnauthorizedError
from app.models.group import MemberRole, Membership, MemberStatus
from app.database.postgres import use_db
from app.models.user import User
from app.repositories.group_repo import membership_repository
from app.repositories.user_repo import user_repository
from app.security.permissions import Permission, require
from app.security.tokens import decode_access_token


async def get_current_user(
    authorization: str | None = Header(default=None),
    _session: AsyncSession = Depends(use_db),  # the request's DB session opens first
) -> User:
    """Resolve the authenticated user from the Bearer access token."""
    if not authorization or not authorization.lower().startswith("bearer "):
        raise UnauthorizedError("Missing bearer token")
    token = authorization.split(" ", 1)[1].strip()
    payload = decode_access_token(token)
    try:
        user_id = uuid.UUID(str(payload.get("sub")))
    except Exception as exc:  # malformed subject in an otherwise valid token
        raise UnauthorizedError("Invalid access token") from exc
    user = await user_repository.get(user_id)
    if user is None:
        raise UnauthorizedError("User not found")
    return user


async def get_optional_user(
    authorization: str | None = Header(default=None),
    _session: AsyncSession = Depends(use_db),
) -> User | None:
    """The signed-in user, or None (for pages that also work signed out)."""
    if not authorization:
        return None
    try:
        return await get_current_user(authorization, _session)
    except UnauthorizedError:
        return None


async def get_membership(
    group_id: uuid.UUID, user: User
) -> Membership:
    """Return the active membership of `user` in `group_id` or raise."""
    membership = await membership_repository.get(group_id, user.id)
    if membership is None:
        raise NotFoundError("You are not a member of this group")
    if membership.status == MemberStatus.BANNED:
        raise PermissionDeniedError("You are banned from this group")
    return membership


def require_permission(permission: Permission):
    """Factory producing a dependency that enforces a group permission.

    Usage in a route: ``membership = Depends(require_permission(Permission.SUBMIT))``
    The route must declare a ``group_id`` path/query parameter.
    """

    async def _dep(
        group_id: uuid.UUID,
        user: User = Depends(get_current_user),
    ) -> Membership:
        membership = await get_membership(group_id, user)
        require(MemberRole(membership.role), permission)
        return membership

    return _dep
