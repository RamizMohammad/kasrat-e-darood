"""Tests for email/password registration and login."""
from __future__ import annotations

import pytest

from app.core.exceptions import ConflictError, UnauthorizedError
from app.security.passwords import hash_password, verify_password
from app.services.auth_service import auth_service

pytestmark = pytest.mark.asyncio


def test_password_hash_roundtrip():
    h = hash_password("correct horse battery")
    assert h != "correct horse battery"
    assert verify_password("correct horse battery", h)
    assert not verify_password("wrong", h)
    assert not verify_password("anything", None)


async def test_register_then_login(init_db):
    res = await auth_service.register(
        email="User@Example.com", password="secret123", display_name="Ramiz"
    )
    assert res.access_token and res.refresh_token
    assert res.user.email == "user@example.com"  # normalized
    assert res.user.display_name == "Ramiz"

    login = await auth_service.login_with_password("user@example.com", "secret123")
    assert login.access_token
    assert login.user.id == res.user.id


async def test_register_duplicate_email_rejected(init_db):
    await auth_service.register(
        email="dup@example.com", password="secret123", display_name="A"
    )
    with pytest.raises(ConflictError):
        await auth_service.register(
            email="DUP@example.com", password="secret123", display_name="B"
        )


async def test_login_wrong_password_rejected(init_db):
    await auth_service.register(
        email="x@example.com", password="secret123", display_name="X"
    )
    with pytest.raises(UnauthorizedError):
        await auth_service.login_with_password("x@example.com", "nope")


async def test_login_unknown_email_rejected(init_db):
    with pytest.raises(UnauthorizedError):
        await auth_service.login_with_password("ghost@example.com", "whatever")
