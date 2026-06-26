"""Google Sign-In ID-token verification (no Firebase dependency).

The Android app signs in with Google and sends the resulting Google ID token to
``POST /auth/google``. We verify it directly against Google's public keys and
check the audience equals our OAuth *web* client id. ``google-auth`` is imported
lazily so the rest of the app (and tests that stub this function) don't require
the package to be installed.
"""
from __future__ import annotations

from app.config.settings import settings
from app.core.exceptions import UnauthorizedError

_ALLOWED_ISSUERS = {"accounts.google.com", "https://accounts.google.com"}


def verify_google_id_token(id_token: str) -> dict:
    """Verify a Google ID token. Returns {uid, name, email, picture}.

    Raises UnauthorizedError on any problem.
    """
    if not id_token:
        raise UnauthorizedError("Missing Google id_token")
    try:  # pragma: no cover - exercised against real Google tokens only
        from google.auth.transport import requests as google_requests
        from google.oauth2 import id_token as google_id_token

        info = google_id_token.verify_oauth2_token(
            id_token,
            google_requests.Request(),
            settings.GOOGLE_CLIENT_ID,
        )
    except Exception as exc:  # pragma: no cover
        raise UnauthorizedError("Invalid Google token") from exc

    if info.get("iss") not in _ALLOWED_ISSUERS:
        raise UnauthorizedError("Invalid Google token issuer")
    if not info.get("sub"):
        raise UnauthorizedError("Invalid Google token (no subject)")

    return {
        "uid": info["sub"],
        "email": info.get("email"),
        "name": info.get("name") or info.get("email") or "User",
        "picture": info.get("picture"),
    }
