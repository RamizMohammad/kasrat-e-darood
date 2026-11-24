"""Health, readiness, liveness and metrics endpoints."""
from __future__ import annotations

from fastapi import APIRouter
from starlette.responses import PlainTextResponse

from sqlalchemy import text

from app.database.postgres import engine
from app.database.redis import cache

router = APIRouter(tags=["ops"])


@router.get("/health")
async def health() -> dict:
    return {"status": "ok"}


@router.get("/health/live")
async def live() -> dict:
    return {"status": "alive"}


@router.get("/health/ready")
async def ready() -> dict:
    """Readiness check — verifies PostgreSQL (and Redis if configured)."""
    checks = {"postgres": False, "redis": cache.client is not None}
    try:
        async with engine().connect() as conn:
            await conn.execute(text("SELECT 1"))
        checks["postgres"] = True
    except Exception:
        checks["postgres"] = False
    ready_ok = checks["postgres"]
    return {"status": "ready" if ready_ok else "degraded", "checks": checks}


@router.get("/metrics", response_class=PlainTextResponse)
async def metrics() -> str:
    """Minimal Prometheus-compatible exposition (extend with real collectors)."""
    return "# HELP noor_up 1 if the service is up\n# TYPE noor_up gauge\nnoor_up 1\n"
