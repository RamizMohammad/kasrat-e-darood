"""FastAPI dependencies for authentication and per-group authorization."""
from __future__ import annotations

from beanie import PydanticObjectId
from fastapi import Depends, Header

from app.core.exceptions import NotFoundError, PermissionDeniedError, UnauthorizedError
from app.models.group import MemberRole, Membership, MemberStatus
from app.models.user import User
from app.security.permissions import Permission, require
from app.security.tokens import decode_access_token


async def get_current_user(
    authorization: str | None = Header(default=None),
) -> User:
    """Resolve the authenticated user from the Bearer access token."""
    if not authorization or not authorization.lower().startswith("bearer "):
        raise UnauthorizedError("Missing bearer token")
    token = authorization.split(" ", 1)[1].strip()
    payload = decode_access_token(token)
    user = await User.get(PydanticObjectId(payload["sub"]))
    if user is None or user.deleted:
        raise UnauthorizedError("User not found")
    return user


async def get_membership(
    group_id: PydanticObjectId, user: User
) -> Membership:
    """Return the active membership of `user` in `group_id` or raise."""
    membership = await Membership.find_one(
        Membership.group_id == group_id,
        Membership.user_id == user.id,
        Membership.deleted == False,  # noqa: E712
    )
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
        group_id: PydanticObjectId,
        user: User = Depends(get_current_user),
    ) -> Membership:
        membership = await get_membership(group_id, user)
        require(MemberRole(membership.role), permission)
        return membership

    return _dep
