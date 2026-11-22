"""Redis-backed fixed-window rate limiter, keyed on the real client IP."""
from __future__ import annotations

from starlette.middleware.base import BaseHTTPMiddleware
from starlette.requests import Request
from starlette.responses import JSONResponse, Response

from app.config.settings import settings
from app.core.client_ip import client_ip
from app.database.redis import cache

# Endpoints that take a password, an OTP or send an email get a much lower
# limit so they can't be brute-forced or used to spam inboxes.
_SENSITIVE_SUFFIXES = (
    "/auth/login",
    "/auth/register",
    "/auth/google",
    "/auth/firebase",
    "/auth/forgot-password",
    "/auth/reset-password",
    "/auth/delete-account/request",
    "/auth/delete-account/confirm",
    "/beta/signup",
)


def limit_for(path: str) -> tuple[str, int]:
    """Return (bucket, max requests per window) for a request path."""
    if path.endswith(_SENSITIVE_SUFFIXES):
        return "auth", settings.RATE_LIMIT_AUTH_PER_MINUTE
    return "api", settings.RATE_LIMIT_PER_MINUTE


class RateLimitMiddleware(BaseHTTPMiddleware):
    """Limit each client IP per path per 60-second window.

    Fails open if Redis is unavailable so the API stays up.
    """

    window = 60

    async def dispatch(self, request: Request, call_next) -> Response:
        path = request.url.path
        if cache.client is None or path.startswith("/health"):
            return await call_next(request)
        bucket, limit = limit_for(path)
        key = f"ratelimit:{bucket}:{client_ip(request)}:{path}"
        try:
            count = await cache.client.incr(key)
            if count == 1:
                await cache.client.expire(key, self.window)
            if count > limit:
                return JSONResponse(
                    status_code=429,
                    content={"error": {"code": "rate_limited",
                                       "message": "Too many requests"}},
                    headers={"Retry-After": str(self.window)},
                )
        except Exception:  # pragma: no cover - fail open
            pass
        return await call_next(request)
