"""Scheduled jobs that now run inside the API (no Celery)."""
from __future__ import annotations

from datetime import datetime, timedelta, timezone

import pytest
from sqlalchemy import text

from app.database.postgres import session_scope
from app.models.activity import RefreshToken
from app.models.user import User
from app.tasks.scheduler import JOBS, daily_at, every_hours, run_job
from tests.helpers import count, insert

UTC = timezone.utc


def test_daily_at_next_run():
    nxt = daily_at(3, 17)
    assert nxt(datetime(2026, 1, 1, 1, 0, tzinfo=UTC)) == datetime(2026, 1, 1, 3, 17, tzinfo=UTC)
    assert nxt(datetime(2026, 1, 1, 3, 17, tzinfo=UTC)) == datetime(2026, 1, 2, 3, 17, tzinfo=UTC)


def test_every_four_hours_on_the_hour():
    nxt = every_hours(4)
    assert nxt(datetime(2026, 1, 1, 1, 30, tzinfo=UTC)) == datetime(2026, 1, 1, 4, 0, tzinfo=UTC)
    assert nxt(datetime(2026, 1, 1, 4, 0, tzinfo=UTC)) == datetime(2026, 1, 1, 8, 0, tzinfo=UTC)
    assert nxt(datetime(2026, 1, 1, 22, 5, tzinfo=UTC)) == datetime(2026, 1, 2, 0, 0, tzinfo=UTC)


@pytest.mark.asyncio
async def test_purge_job_deletes_only_expired(clean_db):
    purge = next(j for j in JOBS if j.name == "purge_expired_tokens")
    async with session_scope():
        u = await insert(User(firebase_uid="p", display_name="P"))
        now = datetime.now(UTC)
        await insert(RefreshToken(user_id=u.id, token_hash="old", expires_at=now - timedelta(days=1)))
        await insert(RefreshToken(user_id=u.id, token_hash="new", expires_at=now + timedelta(days=1)))
    assert await run_job(purge)
    async with session_scope():
        assert await count(RefreshToken) == 1


@pytest.mark.asyncio
async def test_job_skipped_while_another_instance_holds_the_lock(clean_db):
    purge = next(j for j in JOBS if j.name == "purge_expired_tokens")
    from app.database.postgres import engine
    async with engine().connect() as other:      # "another API container"
        await other.execute(text("SELECT pg_advisory_lock(:k)"), {"k": purge.lock_id})
        assert await run_job(purge) is None
        await other.execute(text("SELECT pg_advisory_unlock(:k)"), {"k": purge.lock_id})
    assert await run_job(purge) is not None


@pytest.mark.asyncio
async def test_quote_job_posts_to_community(clean_db, monkeypatch):
    from app.models.activity import ActivityItem
    from app.services import notification_service as ns
    sent = []

    async def fake_push(*a, **k):
        sent.append(a)
    monkeypatch.setattr(ns, "send_to_community", fake_push)
    quote = next(j for j in JOBS if j.name == "broadcast_islamic_quote")
    assert await run_job(quote) == "quote sent"
    async with session_scope():
        assert await count(ActivityItem, ActivityItem.type == "system.reminder") == 1
    assert len(sent) == 1
