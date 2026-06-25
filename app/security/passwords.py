"""Password hashing for email/password auth.

Uses PBKDF2-HMAC-SHA256 from the standard library so no extra dependency is
required. Stored format: ``pbkdf2_sha256$<iterations>$<salt_hex>$<hash_hex>``.
"""
from __future__ import annotations

import hashlib
import hmac
import secrets

_ALGO = "pbkdf2_sha256"
_ITERATIONS = 200_000


def hash_password(password: str) -> str:
    """Return a salted PBKDF2 hash string for ``password``."""
    if not password:
        raise ValueError("password must not be empty")
    salt = secrets.token_hex(16)
    dk = hashlib.pbkdf2_hmac(
        "sha256", password.encode("utf-8"), bytes.fromhex(salt), _ITERATIONS
    )
    return f"{_ALGO}${_ITERATIONS}${salt}${dk.hex()}"


def verify_password(password: str, stored: str | None) -> bool:
    """Constant-time check of ``password`` against a stored hash string."""
    if not stored:
        return False
    try:
        algo, iterations, salt, expected = stored.split("$")
        if algo != _ALGO:
            return False
        dk = hashlib.pbkdf2_hmac(
            "sha256", password.encode("utf-8"), bytes.fromhex(salt), int(iterations)
        )
    except (ValueError, AttributeError):
        return False
    return hmac.compare_digest(dk.hex(), expected)
