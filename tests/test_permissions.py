"""Unit tests for the permission matrix."""
from __future__ import annotations

import pytest

from app.core.exceptions import PermissionDeniedError
from app.models.group import MemberRole
from app.security.permissions import Permission, require, role_has


def test_owner_has_all():
    for perm in Permission:
        assert role_has(MemberRole.OWNER, perm)


def test_viewer_cannot_submit():
    assert not role_has(MemberRole.VIEWER, Permission.SUBMIT)
    with pytest.raises(PermissionDeniedError):
        require(MemberRole.VIEWER, Permission.SUBMIT)


def test_member_can_submit_not_lock():
    assert role_has(MemberRole.MEMBER, Permission.SUBMIT)
    assert not role_has(MemberRole.MEMBER, Permission.WEEK_LOCK)
