"""Unit tests for JWT token creation/verification."""
from __future__ import annotations

import pytest

from app.core.exceptions import UnauthorizedError
from app.security.tokens import create_access_token, decode_access_token


def test_access_token_roundtrip():
    token = create_access_token("user-123", role="member")
    payload = decode_access_token(token)
    assert payload["sub"] == "user-123"
    assert payload["role"] == "member"
    assert payload["type"] == "access"


def test_invalid_token_rejected():
    with pytest.raises(UnauthorizedError):
        decode_access_token("not-a-real-token")
