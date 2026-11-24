"""In-process scheduled jobs (replaces Celery worker + beat).

The API starts these as background asyncio tasks in its lifespan, so the
whole backend is one container. Each run:
  * opens its own DB transaction (session_scope), and
  * takes a Postgres advisory lock first, so if you ever run more than one
    API container only one of them does the job at a time.
A failing run is logged and the loop keeps going.
"""
from __future__ import annotations

import asyncio
import contextvars
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from typing import Awaitable, Callable

from loguru import logger
from sqlalchemy import text

from app.core import clock
from app.database.postgres import db, session_scope


# ---------------------------------------------------------------- the jobs --

async def purge_expired_tokens() -> str:
    """Delete expired refresh tokens and one-time codes."""
    from app.repositories.auth_repo import otp_repository, refresh_token_repository
    tokens = await refresh_token_repository.purge_expired()
    codes = await otp_repository.purge_expired()
    return f"purged {tokens} refresh tokens, {codes} codes"


async def update_streaks() -> str:
    """Just after local midnight: break the streak of everyone who didn't log
    yesterday. (Logging keeps streaks up to date during the day.)"""
    from app.services.streak_service import streak_service
    n = await streak_service.reset_missed()
    return f"reset {n} streaks"


async def close_week_if_due() -> str:
    """Friday around Maghrib: close the week, fill Hall of Fame, open the next.

    Runs every few minutes and does nothing until the active week's closing
    time has come, so a restart at 18:29 can't make it miss the close, and a
    second run right after finds the new week not yet due.
    """
    from app.services.weekly_service import weekly_service
    archived = await weekly_service.close_due()
    return f"closed week {archived.week_number}" if archived else "not due"


async def broadcast_islamic_quote() -> str:
    """Push an Islamic quote / motivation to the whole community."""
    from app.services.notification_service import notification_service
    await notification_service.broadcast_quote()
    return "quote sent"


# ------------------------------------------------------------ the schedule --

@dataclass(frozen=True)
class Job:
    name: str
    lock_id: int                       # Postgres advisory-lock key
    run: Callable[[], Awaitable[str]]
    next_after: Callable[[datetime], datetime]


def daily_at(hour: int, minute: int, tz=None) -> Callable[[datetime], datetime]:
    """Every day at hour:minute in ``tz`` (UTC when None). Returns UTC instants."""
    def nxt(now: datetime) -> datetime:
        local = now.astimezone(tz) if tz else now
        t = local.replace(hour=hour, minute=minute, second=0, microsecond=0)
        if t <= local:
            t = t + timedelta(days=1)   # same wall-clock time tomorrow
        return t.astimezone(timezone.utc)
    return nxt


def every_minutes(step: int) -> Callable[[datetime], datetime]:
    """At minutes divisible by ``step`` (UTC): :00, :05, :10 ... for 5."""
    def nxt(now: datetime) -> datetime:
        t = now.replace(second=0, microsecond=0)
        t += timedelta(minutes=step - t.minute % step)
        return t
    return nxt


def every_hours(step: int) -> Callable[[datetime], datetime]:
    """On the hour, at hours divisible by ``step`` (UTC): 0, 4, 8, ... for 4."""
    def nxt(now: datetime) -> datetime:
        t = now.replace(minute=0, second=0, microsecond=0)
        t += timedelta(hours=step - t.hour % step)
        while t <= now:
            t += timedelta(hours=step)
        return t
    return nxt


JOBS: tuple[Job, ...] = (
    Job("purge_expired_tokens", 834250201, purge_expired_tokens, daily_at(3, 17)),
    Job("broadcast_islamic_quote", 834250202, broadcast_islamic_quote, every_hours(4)),
    Job("update_streaks", 834250203, update_streaks, daily_at(0, 5, clock.tz())),
    Job("close_week_if_due", 834250204, close_week_if_due, every_minutes(5)),
)


# ------------------------------------------------------------- the runner --

async def run_job(job: Job) -> str | None:
    """Run once inside its own transaction; None if another instance holds it."""
    async with session_scope():
        got = (await db().execute(
            text("SELECT pg_try_advisory_xact_lock(:k)"), {"k": job.lock_id})).scalar_one()
        if not got:
            logger.info("[job] {} skipped: running elsewhere", job.name)
            return None
        result = await job.run()
    logger.info("[job] {}: {}", job.name, result)
    return result


async def _loop(job: Job) -> None:
    while True:
        now = datetime.now(timezone.utc)
        due = job.next_after(now)
        await asyncio.sleep((due - now).total_seconds())
        try:
            await run_job(job)
        except asyncio.CancelledError:
            raise
        except Exception:  # noqa: BLE001 - never let one bad run stop the loop
            logger.exception("[job] {} failed", job.name)


class Scheduler:
    def __init__(self) -> None:
        self._tasks: list[asyncio.Task] = []

    def start(self) -> None:
        for job in JOBS:
            # Fresh context: a job must never see a request's DB session.
            self._tasks.append(asyncio.create_task(
                _loop(job), name=f"job:{job.name}", context=contextvars.Context()))
        logger.info("Scheduler started: {}", ", ".join(j.name for j in JOBS))

    async def stop(self) -> None:
        for t in self._tasks:
            t.cancel()
        await asyncio.gather(*self._tasks, return_exceptions=True)
        self._tasks.clear()


scheduler = Scheduler()
