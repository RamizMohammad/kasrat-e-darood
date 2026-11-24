"""Tests for user language and account-deletion OTP flow."""
from __future__ import annotations

import pytest

from app.core.exceptions import ValidationAppError
from app.models.user import User
from app.services.auth_service import auth_service
from tests.helpers import get

pytestmark = pytest.mark.asyncio


async def test_register_stores_language(init_db):
    res = await auth_service.register(
        email="u@example.com", password="secret123", display_name="U", lang="ur")
    user = await get(User, res.user.id)
    assert user.lang == "ur"
    assert res.user.lang == "ur"


async def test_register_defaults_language_en(init_db):
    res = await auth_service.register(
        email="e@example.com", password="secret123", display_name="E")
    assert res.user.lang == "en"


async def test_account_deletion_flow(init_db):
    res = await auth_service.register(
        email="del@example.com", password="secret123", display_name="D", lang="hi")
    user = await get(User, res.user.id)

    result = await auth_service.create_deletion_code(user)
    assert result is not None
    code, lang = result
    assert lang == "hi"

    email, lang2 = await auth_service.confirm_account_deletion(user, code)
    assert email == "del@example.com"

    # Account is soft-deleted and can no longer log in.
    from app.core.exceptions import UnauthorizedError
    with pytest.raises(UnauthorizedError):
        await auth_service.login_with_password("del@example.com", "secret123")


async def test_deletion_wrong_code_rejected(init_db):
    res = await auth_service.register(
        email="w2@example.com", password="secret123", display_name="W")
    user = await get(User, res.user.id)
    await auth_service.create_deletion_code(user)
    with pytest.raises(ValidationAppError):
        await auth_service.confirm_account_deletion(user, "000000")


async def test_deletion_code_not_usable_for_password_reset(init_db):
    """A deletion OTP must not work on the password-reset path (purpose-scoped)."""
    res = await auth_service.register(
        email="x2@example.com", password="secret123", display_name="X")
    user = await get(User, res.user.id)
    code, _ = await auth_service.create_deletion_code(user)
    with pytest.raises(ValidationAppError):
        await auth_service.reset_password("x2@example.com", code, "newpassword9")
