"""Request-context middleware: correlation id + structured access logging."""
from __future__ import annotations

import time
import uuid

from loguru import logger
from starlette.middleware.base import BaseHTTPMiddleware
from starlette.requests import Request
from starlette.responses import Response


class RequestContextMiddleware(BaseHTTPMiddleware):
    """Attach a correlation id to each request and log latency."""

    async def dispatch(self, request: Request, call_next) -> Response:
        correlation_id = request.headers.get("X-Request-ID", str(uuid.uuid4()))
        start = time.perf_counter()
        with logger.contextualize(request_id=correlation_id):
            response = await call_next(request)
            elapsed_ms = (time.perf_counter() - start) * 1000
            logger.info(
                "{method} {path} -> {status} ({ms:.1f}ms)",
                method=request.method, path=request.url.path,
                status=response.status_code, ms=elapsed_ms,
            )
        response.headers["X-Request-ID"] = correlation_id
        return response
