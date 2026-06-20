"""Firebase Authentication token verification.

In production a service-account file is provided and the Firebase Admin SDK
verifies the client ID token. In development (no credentials configured) a
deterministic stub identity is returned so the rest of the stack is runnable
without external dependencies.
"""
from __future__ import annotations

from loguru import logger

from app.config.settings import settings
from app.core.exceptions import UnauthorizedError

_firebase_app = None


def init_firebase() -> None:
    """Initialize the Firebase Admin SDK if credentials are configured."""
    global _firebase_app
    if not settings.FIREBASE_CREDENTIALS_FILE:
        logger.warning("Firebase credentials not set — using DEV identity stub")
        return
    try:  # pragma: no cover - requires real credentials
        import firebase_admin
        from firebase_admin import credentials

        cred = credentials.Certificate(settings.FIREBASE_CREDENTIALS_FILE)
        _firebase_app = firebase_admin.initialize_app(cred)
        logger.info("Firebase Admin initialized")
    except Exception as exc:  # pragma: no cover
        logger.error("Firebase init failed: {}", exc)


def verify_id_token(id_token: str) -> dict:
    """Verify a Firebase ID token. Returns a normalized identity dict.

    Output: {uid, name, email, phone, picture}.
    """
    if not settings.FIREBASE_CREDENTIALS_FILE:
        # Development stub: treat the token text as the uid.
        if not id_token:
            raise UnauthorizedError("Missing id_token")
        return {
            "uid": f"dev-{id_token[:24]}",
            "name": "Dev User",
            "email": None,
            "phone": None,
            "picture": None,
        }
    try:  # pragma: no cover - requires real credentials
        from firebase_admin import auth as fb_auth

        decoded = fb_auth.verify_id_token(id_token)
        return {
            "uid": decoded["uid"],
            "name": decoded.get("name") or decoded.get("email") or "User",
            "email": decoded.get("email"),
            "phone": decoded.get("phone_number"),
            "picture": decoded.get("picture"),
        }
    except Exception as exc:  # pragma: no cover
        raise UnauthorizedError("Invalid Firebase token") from exc
