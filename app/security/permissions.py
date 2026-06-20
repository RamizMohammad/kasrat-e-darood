"""Permission-based authorization.

Roles map to permission sets rather than being checked directly in business
logic, so new roles/permissions can be added without touching call sites.
"""
from __future__ import annotations

from enum import Enum

from app.core.exceptions import PermissionDeniedError
from app.models.group import MemberRole


class Permission(str, Enum):
    GROUP_MANAGE = "group:manage"
    GROUP_DELETE = "group:delete"
    MEMBER_MANAGE = "member:manage"
    RECITATION_MANAGE = "recitation:manage"
    WEEK_LOCK = "week:lock"
    ANNOUNCE = "announce"
    SUBMIT = "submit"
    VIEW = "view"


_ROLE_PERMISSIONS: dict[MemberRole, set[Permission]] = {
    MemberRole.OWNER: set(Permission),  # all
    MemberRole.ADMIN: {
        Permission.GROUP_MANAGE, Permission.MEMBER_MANAGE,
        Permission.RECITATION_MANAGE, Permission.WEEK_LOCK,
        Permission.ANNOUNCE, Permission.SUBMIT, Permission.VIEW,
    },
    MemberRole.MODERATOR: {
        Permission.MEMBER_MANAGE, Permission.ANNOUNCE,
        Permission.SUBMIT, Permission.VIEW,
    },
    MemberRole.MEMBER: {Permission.SUBMIT, Permission.VIEW},
    MemberRole.VIEWER: {Permission.VIEW},
}


def role_has(role: MemberRole, permission: Permission) -> bool:
    return permission in _ROLE_PERMISSIONS.get(role, set())


def require(role: MemberRole, permission: Permission) -> None:
    """Raise PermissionDeniedError if `role` lacks `permission`."""
    if not role_has(role, permission):
        raise PermissionDeniedError(
            f"Role '{role.value}' lacks permission '{permission.value}'"
        )
