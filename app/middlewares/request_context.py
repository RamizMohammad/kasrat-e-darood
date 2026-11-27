"""Request-context middleware: correlation id, access log, security headers."""
from __future__ import annotations

import re
import time
import uuid

from loguru import logger
from starlette.middleware.base import BaseHTTPMiddleware
from starlette.requests import Request
from starlette.responses import Response

from app.config.settings import settings
from app.core.client_ip import client_ip
from app.core.logging import access_log

# A client-supplied X-Request-ID is only reused when it looks like an id, so
# nobody can write newlines or fake entries into the logs through it.
_SAFE_ID = re.compile(r"^[A-Za-z0-9._-]{1,64}$")

_SECURITY_HEADERS = {
    "X-Content-Type-Options": "nosniff",
    "X-Frame-Options": "DENY",
    "Referrer-Policy": "no-referrer",
}
# Production only: the API returns JSON and the /j/ invite page is static HTML
# with inline CSS and no scripts. (In development /docs needs its scripts.)
_PRODUCTION_HEADERS = {
    "Strict-Transport-Security": "max-age=31536000; includeSubDomains",
    "Content-Security-Policy": "default-src 'none'; style-src 'unsafe-inline'; "
                               "img-src 'self' data:; frame-ancestors 'none'; base-uri 'none'; "
                               "form-action 'none'",
}


class RequestContextMiddleware(BaseHTTPMiddleware):
    """Attach a correlation id to each request, log latency, add security headers."""

    async def dispatch(self, request: Request, call_next) -> Response:
        supplied = request.headers.get("X-Request-ID", "")
        correlation_id = supplied if _SAFE_ID.match(supplied) else str(uuid.uuid4())
        start = time.perf_counter()
        ip = client_ip(request)
        with logger.contextualize(request_id=correlation_id):
            try:
                response = await call_next(request)
            except Exception:
                # The 500 handler answers the client; this keeps the access line.
                access_log(request.method, request.url.path, 500,
                           (time.perf_counter() - start) * 1000, ip)
                raise
            access_log(request.method, request.url.path, response.status_code,
                       (time.perf_counter() - start) * 1000, ip)
        response.headers["X-Request-ID"] = correlation_id
        if request.url.path.startswith(settings.API_V1_PREFIX):
            # Personal data: no proxy, CDN or browser may keep a copy.
            response.headers.setdefault("Cache-Control", "no-store, private")
            response.headers.setdefault("Vary", "Authorization")
        for name, value in _SECURITY_HEADERS.items():
            response.headers.setdefault(name, value)
        if settings.is_production:
            for name, value in _PRODUCTION_HEADERS.items():
                response.headers.setdefault(name, value)
        return response
