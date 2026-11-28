"""Gzip for API responses, except sign-in endpoints.

Arabic text with diacritics and JSON compress ~5-8x; on a mobile connection
that's the difference between a list arriving now or seconds later (one
HTTP/2 connection carries every request, so a big uncompressed body holds up
all the small ones behind it). Clients that send Accept-Encoding: gzip get it
(OkHttp does automatically and unzips transparently).

/auth responses carry tokens and aren't compressed: compression next to a
secret in the same body is what BREACH-style attacks exploit.
"""
from __future__ import annotations

from starlette.middleware.gzip import GZipMiddleware
from starlette.types import ASGIApp, Receive, Scope, Send

from app.config.settings import settings


class SelectiveGZipMiddleware:
    def __init__(self, app: ASGIApp, minimum_size: int = 1024) -> None:
        self.app = app
        self.gzip = GZipMiddleware(app, minimum_size=minimum_size, compresslevel=6)
        self._skip = settings.API_V1_PREFIX + "/auth"

    async def __call__(self, scope: Scope, receive: Receive, send: Send) -> None:
        if scope["type"] == "http" and not scope.get("path", "").startswith(self._skip):
            await self.gzip(scope, receive, send)
        else:
            await self.app(scope, receive, send)
