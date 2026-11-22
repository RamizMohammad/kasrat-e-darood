"""Tests for roles, password-reset OTP, and Google login."""
from __future__ import annotations

import pytest

from app.core.exceptions import ValidationAppError
from app.models.user import GlobalRole, User
from app.services import auth_service as auth_module
from app.services.auth_service import auth_service

pytestmark = pytest.mark.asyncio


async def test_roles_enum_has_three_values():
    assert {r.value for r in GlobalRole} == {"member", "super_member", "super_admin"}


async def test_register_defaults_to_member(init_db):
    res = await auth_service.register(
        email="m@example.com", password="secret123", display_name="M")
    user = await User.get(res.user.id)
    assert user.role == GlobalRole.MEMBER


# --- Password reset ----------------------------------------------------------
async def test_password_reset_flow(init_db):
    await auth_service.register(
        email="reset@example.com", password="oldpassword1", display_name="R")

    result = await auth_service.create_reset_code("Reset@example.com")
    assert result is not None
    code, lang = result
    assert len(code) == 6

    await auth_service.reset_password("reset@example.com", code, "newpassword9")
    # Old password no longer works; new one does.
    from app.core.exceptions import UnauthorizedError
    with pytest.raises(UnauthorizedError):
        await auth_service.login_with_password("reset@example.com", "oldpassword1")
    ok = await auth_service.login_with_password("reset@example.com", "newpassword9")
    assert ok.access_token


async def test_reset_code_unknown_email_returns_none(init_db):
    assert await auth_service.create_reset_code("ghost@example.com") is None


async def test_reset_wrong_code_rejected(init_db):
    await auth_service.register(
        email="w@example.com", password="oldpassword1", display_name="W")
    await auth_service.create_reset_code("w@example.com")
    with pytest.raises(ValidationAppError):
        await auth_service.reset_password("w@example.com", "000000", "newpassword9")


# --- Google login ------------------------------------------------------------
async def test_google_login_creates_member(init_db, monkeypatch):
    monkeypatch.setattr(
        auth_module, "verify_google_id_token",
        lambda token: {"uid": "g-123", "email": "G@gmail.com",
                       "name": "Google User", "picture": None},
    )
    res = await auth_service.login_with_google("fake-token")
    assert res.access_token
    assert res.user.email == "g@gmail.com"
    user = await User.get(res.user.id)
    assert user.role == GlobalRole.MEMBER
    assert user.firebase_uid == "google:g-123"

    # Logging in again returns the same user (no duplicate).
    res2 = await auth_service.login_with_google("fake-token")
    assert res2.user.id == res.user.id
