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


# ── Week boundaries ─────────────────────────────────────────────────────────
# A Kasrat week closes every Friday at WEEK_CLOSE_LOCAL_TIME (local), and the
# next week starts at that same instant.

def _close_time() -> time:
    h, m = settings.WEEK_CLOSE_LOCAL_TIME.split(":")
    return time(int(h), int(m))


def next_week_close(now: datetime | None = None) -> datetime:
    """The first scheduled close strictly after ``now`` (UTC instant)."""
    now = now or now_utc()
    local = now.astimezone(tz())
    days = (settings.WEEK_LOCK_WEEKDAY - local.weekday()) % 7
    candidate = datetime.combine(local.date() + timedelta(days=days), _close_time(), tzinfo=tz())
    if candidate <= local:
        candidate = datetime.combine(candidate.date() + timedelta(days=7), _close_time(), tzinfo=tz())
    return candidate.astimezone(timezone.utc)


def previous_week_close(now: datetime | None = None) -> datetime:
    """The latest scheduled close at or before ``now``."""
    return next_week_close(now) - timedelta(days=7)
