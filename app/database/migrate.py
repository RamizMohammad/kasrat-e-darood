"""Run Alembic migrations from inside the app (used at API startup)."""
from __future__ import annotations

import asyncio
from pathlib import Path

from alembic import command
from alembic.config import Config
from loguru import logger

_ROOT = Path(__file__).resolve().parents[2]


def alembic_config(url: str | None = None) -> Config:
    cfg = Config(str(_ROOT / "alembic.ini"))
    cfg.set_main_option("script_location", str(_ROOT / "migrations"))
    cfg.attributes["skip_logging"] = True
    if url:
        cfg.attributes["url"] = url
    return cfg


def upgrade_head_sync(url: str | None = None) -> None:
    command.upgrade(alembic_config(url), "head")


async def upgrade_head(url: str | None = None) -> None:
    """`alembic upgrade head`, in a worker thread (Alembic runs its own event loop)."""
    logger.info("Applying database migrations …")
    await asyncio.to_thread(upgrade_head_sync, url)
    logger.info("Database schema is up to date")
