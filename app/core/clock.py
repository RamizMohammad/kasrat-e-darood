"""Local-day arithmetic. A "day" in Kasrat is a calendar day in
settings.DEFAULT_TIMEZONE (Asia/Kolkata), not UTC: a log at 00:30 IST counts
for that IST day even though it's still the previous day in UTC.
"""
from __future__ import annotations

from datetime import date, datetime, time, timedelta, timezone
from functools import lru_cache
from zoneinfo import ZoneInfo

from app.config.settings import settings


@lru_cache(maxsize=1)
def tz() -> ZoneInfo:
    return ZoneInfo(settings.DEFAULT_TIMEZONE)


def tz_name() -> str:
    return settings.DEFAULT_TIMEZONE


def now_utc() -> datetime:
    return datetime.now(timezone.utc)


def local_today(now: datetime | None = None) -> date:
    return (now or now_utc()).astimezone(tz()).date()


def start_of_local_day(day: date) -> datetime:
    """The UTC instant at which ``day`` begins locally."""
    return datetime.combine(day, time.min, tzinfo=tz()).astimezone(timezone.utc)


def start_of_today(now: datetime | None = None) -> datetime:
    return start_of_local_day(local_today(now))


def start_of_month(now: datetime | None = None) -> datetime:
    return start_of_local_day(local_today(now).replace(day=1))


def days_ago(n: int, now: datetime | None = None) -> date:
    return local_today(now) - timedelta(days=n)
