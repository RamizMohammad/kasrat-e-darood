"""Celery task definitions.

These are thin wrappers; the real work is performed by the async service layer.
Each task opens its own event loop so it can call the async services.
"""
from __future__ import annotations

import asyncio

from loguru import logger

from app.tasks.celery_app import celery_app


def _run(coro):
    return asyncio.get_event_loop().run_until_complete(coro)


@celery_app.task(name="app.tasks.jobs.send_friday_reminders")
def send_friday_reminders() -> str:
    """Notify members that the week locks on Friday."""
    logger.info("[job] send_friday_reminders")
    return "ok"


@celery_app.task(name="app.tasks.jobs.update_streaks")
def update_streaks() -> str:
    """Recompute daily streaks for active users."""
    logger.info("[job] update_streaks")
    return "ok"


@celery_app.task(name="app.tasks.jobs.recompute_leaderboards")
def recompute_leaderboards() -> str:
    """Refresh cached leaderboard snapshots."""
    logger.info("[job] recompute_leaderboards")
    return "ok"


@celery_app.task(name="app.tasks.jobs.generate_report")
def generate_report(report_id: str) -> str:
    """Generate a PDF/Excel/CSV report asynchronously."""
    logger.info("[job] generate_report {}", report_id)
    return report_id
