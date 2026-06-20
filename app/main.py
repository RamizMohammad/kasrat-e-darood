"""Noor API application entrypoint.

Run locally:  uvicorn app.main:app --reload
"""
from __future__ import annotations

from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

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
    app = FastAPI(
        title=settings.APP_NAME,
        version="1.0.0",
        description="Noor — Islamic community recitation tracking platform.",
        docs_url="/docs",
        openapi_url="/openapi.json",
        lifespan=lifespan,
    )

    app.add_middleware(
        CORSMiddleware,
        allow_origins=settings.CORS_ORIGINS,
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )
    app.add_middleware(RateLimitMiddleware)
    app.add_middleware(RequestContextMiddleware)

    register_exception_handlers(app)

    app.include_router(health_router)
    app.include_router(ws_router)
    app.include_router(api_router, prefix=settings.API_V1_PREFIX)
    return app


app = create_app()
