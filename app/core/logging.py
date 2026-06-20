"""Structured logging configuration using Loguru."""
from __future__ import annotations

import sys

from loguru import logger

from app.config.settings import settings


def configure_logging() -> None:
    """Configure Loguru sinks. JSON in production, pretty in development."""
    logger.remove()
    logger.add(
        sys.stdout,
        level="DEBUG" if settings.DEBUG else "INFO",
        serialize=settings.is_production,  # JSON logs in prod
        backtrace=settings.DEBUG,
        diagnose=settings.DEBUG,
        enqueue=True,
    )
    logger.info("Logging configured (env={})", settings.APP_ENV)
