"""Tests for super-admin user listing and role changes."""
from __future__ import annotations

import pytest

from app.api.v1.users.router import list_users, set_user_role
from app.core.exceptions import PermissionDeniedError
from app.models.user import GlobalRole, User
from app.schemas.user import RoleUpdate
from tests.helpers import get, insert

pytestmark = pytest.mark.asyncio


async def _user(name: str, role: GlobalRole = GlobalRole.MEMBER) -> User:
    u = User(firebase_uid=f"uid-{name}", display_name=name, role=role)
    await insert(u)
    return u


async def test_list_users_requires_super_admin(init_db):
    admin = await _user("Admin", GlobalRole.SUPER_ADMIN)
    await _user("Member1")
    await _user("Member2")

    res = await list_users(cursor=None, limit=50, actor=admin)
    assert res.total == 3
    assert len(res.items) == 3

    member = await _user("PlainMember")
    with pytest.raises(PermissionDeniedError):
        await list_users(cursor=None, limit=50, actor=member)


async def test_super_admin_can_change_role(init_db):
    admin = await _user("Admin", GlobalRole.SUPER_ADMIN)
    target = await _user("Target")

    out = await set_user_role(target.id, RoleUpdate(role="super_member"), actor=admin)
    assert out.role == "super_member"
    refreshed = await get(User, target.id)
    assert refreshed.role == GlobalRole.SUPER_MEMBER


async def test_member_cannot_change_role(init_db):
    member = await _user("Member")
    target = await _user("Target")
    with pytest.raises(PermissionDeniedError):
        await set_user_role(target.id, RoleUpdate(role="super_admin"), actor=member)
