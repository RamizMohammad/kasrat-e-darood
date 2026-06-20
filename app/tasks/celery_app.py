"""Celery application and scheduled (beat) jobs.

Background jobs keep the request path fast: leaderboard/statistics
recomputation, push notifications, streak/goal/achievement evaluation,
report generation, cleanup and backups. Task bodies delegate to the same
service layer used by the API so business rules live in exactly one place.
"""
from __future__ import annotations

from celery import Celery
from celery.schedules import crontab

from app.config.settings import settings

celery_app = Celery(
    "noor",
    broker=settings.REDIS_URL,
    backend=settings.REDIS_URL,
)
celery_app.conf.update(
    task_serializer="json",
    result_serializer="json",
    accept_content=["json"],
    timezone="UTC",
    enable_utc=True,
)

# Periodic schedule (extend as features land).
celery_app.conf.beat_schedule = {
    "friday-reminder": {
        "task": "app.tasks.jobs.send_friday_reminders",
        "schedule": crontab(hour=15, minute=0, day_of_week="thu"),
    },
    "daily-streak-update": {
        "task": "app.tasks.jobs.update_streaks",
        "schedule": crontab(hour=0, minute=5),
    },
    "recompute-leaderboards": {
        "task": "app.tasks.jobs.recompute_leaderboards",
        "schedule": crontab(minute="*/15"),
    },
}
