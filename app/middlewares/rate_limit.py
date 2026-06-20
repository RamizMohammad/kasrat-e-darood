"""Lightweight Redis-backed fixed-window rate limiter."""
from __future__ import annotations

from starlette.middleware.base import BaseHTTPMiddleware
from starlette.requests import Request
from starlette.responses import JSONResponse, Response

from app.database.redis import cache


class RateLimitMiddleware(BaseHTTPMiddleware):
    """Limit each client IP to `limit` requests per `window` seconds.

    Fails open if Redis is unavailable so the API stays up.
    """

    def __init__(self, app, limit: int = 120, window: int = 60) -> None:
        super().__init__(app)
        self.limit = limit
        self.window = window

    async def dispatch(self, request: Request, call_next) -> Response:
        if cache.client is None or request.url.path.startswith("/health"):
            return await call_next(request)
        client = request.client.host if request.client else "anon"
        key = f"ratelimit:{client}:{request.url.path}"
        try:
            count = await cache.client.incr(key)
            if count == 1:
                await cache.client.expire(key, self.window)
            if count > self.limit:
                return JSONResponse(
                    status_code=429,
                    content={"error": {"code": "rate_limited",
                                       "message": "Too many requests"}},
                )
        except Exception:  # pragma: no cover - fail open
            pass
        return await call_next(request)
