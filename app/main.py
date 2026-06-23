"""Noor API application entrypoint.

Run locally:  uvicorn app.main:app --reload
"""
from __future__ import annotations

import os
from contextlib import asynccontextmanager
from pathlib import Path

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse
from fastapi.staticfiles import StaticFiles

from app.api.health import router as health_router
from app.api.router import api_router
from app.config.settings import settings
from app.core.exceptions import register_exception_handlers
from app.core.logging import configure_logging
from app.database.mongo import close_mongo_connection, connect_to_mongo
from app.database.redis import cache
from app.middlewares.rate_limit import RateLimitMiddleware
from app.middlewares.request_context import RequestContextMiddleware
from app.security.firebase import init_firebase
from app.websocket.router import router as ws_router


def _cors_origins() -> list[str]:
    """Allowed browser origins, comma-separated, from the environment.

    The Android app is a native client and is not subject to CORS. Only the
    web frontend's origin(s) need to be listed here.
    """
    raw = os.getenv("CORS_ALLOW_ORIGINS", "https://kasrat-e-darood.mohammadramiz.in")
    return [o.strip() for o in raw.split(",") if o.strip()]


# Optional regex to allow Vercel preview deployments, e.g.
#   CORS_ALLOW_ORIGIN_REGEX=https://.*\.vercel\.app
_CORS_ORIGIN_REGEX = os.getenv("CORS_ALLOW_ORIGIN_REGEX", "") or None


@asynccontextmanager
async def lifespan(_: FastAPI):
    """Manage startup/shutdown of external resources."""
    configure_logging()
    init_firebase()
    await connect_to_mongo()
    await cache.connect()
    yield
    await cache.close()
    await close_mongo_connection()


def create_app() -> FastAPI:
    # Interactive docs and the OpenAPI schema are disabled in production so the
    # API surface isn't publicly browsable.
    docs_enabled = not settings.is_production
    app = FastAPI(
        title=settings.APP_NAME,
        version="1.0.0",
        description="Kasrat-e-Darood — Islamic community recitation tracking platform.",
        docs_url="/docs" if docs_enabled else None,
        redoc_url="/redoc" if docs_enabled else None,
        openapi_url="/openapi.json" if docs_enabled else None,
        lifespan=lifespan,
    )

    # Browser (web) access is restricted to the configured frontend origin(s).
    # No cookies are used (the web frontend only calls public/Bearer endpoints),
    # so credentials are disabled — which also lets a regex allowlist be used.
    app.add_middleware(
        CORSMiddleware,
        allow_origins=_cors_origins(),
        allow_origin_regex=_CORS_ORIGIN_REGEX,
        allow_credentials=False,
        allow_methods=["GET", "POST", "PATCH", "DELETE", "OPTIONS"],
        allow_headers=["Authorization", "Content-Type"],
    )
    app.add_middleware(RateLimitMiddleware)
    app.add_middleware(RequestContextMiddleware)

    register_exception_handlers(app)

    app.include_router(health_router)
    app.include_router(ws_router)
    app.include_router(api_router, prefix=settings.API_V1_PREFIX)

    # --- Optional local landing page -----------------------------------------
    # In production the web frontend is deployed separately (e.g. Vercel), so
    # the API does not serve it. For local development the bundled copy under
    # ./web is served at "/" when SERVE_WEB=1 (and not in production).
    serve_web = os.getenv("SERVE_WEB", "1") == "1" and not settings.is_production
    web_dir = Path(__file__).resolve().parent.parent / "web"
    if serve_web and web_dir.exists():
        app.mount("/static", StaticFiles(directory=str(web_dir)), name="static")

        @app.get("/", include_in_schema=False)
        async def landing() -> FileResponse:
            """Serve the bundled signup one-pager (local development only)."""
            return FileResponse(str(web_dir / "index.html"))

    return app


app = create_app()
