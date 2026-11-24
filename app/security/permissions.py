"""Group roles → permissions (the v3 roles table).

| Action                                   | Owner | Admin            | Moderator    | Member |
|------------------------------------------|-------|------------------|--------------|--------|
| Log and appear on the board              | yes   | yes              | yes          | yes    |
| Share the invite code                    | yes   | yes              | yes          | yes    |
| Approve or decline requests              | yes   | yes              | yes          | —      |
| Remove or ban members                    | yes   | yes              | members only | —      |
| Post announcements                       | yes   | yes              | yes          | —      |
| Edit name, goal, privacy; reset code     | yes   | yes              | —            | —      |
| Promote to admin or moderator            | yes   | yes (not owner)  | —            | —      |
| Transfer ownership, delete group         | yes   | —                | —            | —      |

Removing, banning and changing roles also need the actor to OUTRANK the
target (owner > admin > moderator > member): see ``can_act_on``.
"""
from __future__ import annotations

from enum import Enum

from app.core.exceptions import PermissionDeniedError
from app.models.group import MemberRole


class Permission(str, Enum):
    SUBMIT = "submit"
    VIEW = "view"
    INVITE_SHARE = "invite:share"
    REQUEST_DECIDE = "request:decide"
    MEMBER_MANAGE = "member:manage"          # remove / ban (subject to rank)
    ANNOUNCE = "announce"
    GROUP_MANAGE = "group:manage"            # edit settings, reset code
    ROLE_ASSIGN = "role:assign"
    GROUP_DELETE = "group:delete"            # also transfer ownership
    AUDIT_VIEW = "audit:view"
    RECITATION_MANAGE = "recitation:manage"
    WEEK_LOCK = "week:lock"


_MEMBER = {Permission.SUBMIT, Permission.VIEW, Permission.INVITE_SHARE}
_MODERATOR = _MEMBER | {Permission.REQUEST_DECIDE, Permission.MEMBER_MANAGE, Permission.ANNOUNCE}
_ADMIN = _MODERATOR | {Permission.GROUP_MANAGE, Permission.ROLE_ASSIGN,
                       Permission.RECITATION_MANAGE, Permission.WEEK_LOCK}

_ROLE_PERMISSIONS: dict[MemberRole, set[Permission]] = {
    MemberRole.OWNER: set(Permission),
    MemberRole.ADMIN: _ADMIN,
    MemberRole.MODERATOR: _MODERATOR,
    MemberRole.MEMBER: _MEMBER,
    MemberRole.VIEWER: {Permission.VIEW},
}

RANK: dict[MemberRole, int] = {
    MemberRole.OWNER: 4, MemberRole.ADMIN: 3, MemberRole.MODERATOR: 2,
    MemberRole.MEMBER: 1, MemberRole.VIEWER: 0,
}


def role_has(role: MemberRole, permission: Permission) -> bool:
    return permission in _ROLE_PERMISSIONS.get(MemberRole(role), set())


def require(role: MemberRole, permission: Permission) -> None:
    """Raise PermissionDeniedError if `role` lacks `permission`."""
    if not role_has(role, permission):
        raise PermissionDeniedError(
            f"Role '{MemberRole(role).value}' lacks permission '{permission.value}'")


def can_act_on(actor: MemberRole, target: MemberRole) -> bool:
    """Remove / ban / change the role of someone: only people you outrank."""
    return RANK[MemberRole(actor)] > RANK[MemberRole(target)]


def can_assign(actor: MemberRole, target: MemberRole, new_role: MemberRole) -> bool:
    """Change ``target``'s role to ``new_role``. Owners give any role but
    owner (that's a transfer); admins up to admin; nobody else."""
    actor, target, new_role = MemberRole(actor), MemberRole(target), MemberRole(new_role)
    if not role_has(actor, Permission.ROLE_ASSIGN) or not can_act_on(actor, target):
        return False
    if new_role in (MemberRole.OWNER, MemberRole.VIEWER):
        return False
    return RANK[new_role] <= RANK[actor] if actor == MemberRole.ADMIN else RANK[new_role] < RANK[actor]
