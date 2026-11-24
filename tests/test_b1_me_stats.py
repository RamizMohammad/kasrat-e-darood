"""B1: GET /statistics/me, streaks, local (IST) days, preferences, paging."""
from __future__ import annotations

import time
from datetime import datetime, timedelta, timezone

import httpx
import pytest

from app.core import clock
from app.database.postgres import session_scope
from app.database.redis import cache
from app.models.category import Recitation
from app.models.submission import Submission
from app.models.user import User
from app.repositories.weekly_repo import weekly_repository
from app.schemas.submission import SubmissionCreate
from app.services.community_service import community_service
from app.services.stats_service import stats_service
from app.services.streak_service import streak_service
from app.services.submission_service import submission_service
from app.tasks.scheduler import daily_at
from tests.helpers import get, insert

pytestmark = pytest.mark.asyncio
UTC = timezone.utc


async def _setup():
    user = await insert(User(firebase_uid="b1", display_name="B1"))
    rec = await insert(Recitation(arabic_name="درود", english_name="Darood"))
    rec2 = await insert(Recitation(arabic_name="ذکر", english_name="Zikr"))
    gid = await community_service.resolve_group(user, None)
    week = await weekly_repository.get_active(gid)
    return user, rec, rec2, gid, week


async def _log_at(user, rec, gid, week, when: datetime, count: int = 1):
    """A log with a chosen timestamp (what the app would have sent then)."""
    s = await insert(Submission(group_id=gid, week_id=week.id, user_id=user.id,
                                recitation_id=rec.id, count=count))
    s.created_at = when
    await insert(s)
    return s


def _local(day, hour, minute):
    """A UTC instant for a local (IST) wall-clock time on ``day``."""
    return (clock.start_of_local_day(day) + timedelta(hours=hour, minutes=minute))


# ── local days ──────────────────────────────────────────────────────────────

async def test_heatmap_splits_days_at_ist_midnight_not_utc(init_db):
    user, rec, _, gid, week = await _setup()
    today = clock.local_today()
    yesterday = today - timedelta(days=1)
    await _log_at(user, rec, gid, week, _local(yesterday, 23, 59), 5)   # 18:29 UTC
    await _log_at(user, rec, gid, week, _local(today, 0, 1), 7)         # 18:31 UTC
    stats = await stats_service.me(user)
    by_day = {d.date: d.total for d in stats.days}
    assert by_day == {yesterday: 5, today: 7}
    assert stats.today == 7 and stats.lifetime == 12
    assert stats.timezone == "Asia/Kolkata" and stats.to_date == today
    assert stats.from_date == today - timedelta(days=364)


async def test_heatmap_window_is_365_days(init_db):
    user, rec, _, gid, week = await _setup()
    today = clock.local_today()
    await _log_at(user, rec, gid, week, _local(today - timedelta(days=364), 12, 0), 2)
    await _log_at(user, rec, gid, week, _local(today - timedelta(days=365), 12, 0), 3)
    stats = await stats_service.me(user)
    assert [d.total for d in stats.days] == [2]       # the 366th day back is out
    assert stats.lifetime == 5                         # but lifetime counts it


async def test_by_recitation_biggest_first(init_db):
    user, rec, rec2, gid, week = await _setup()
    now = clock.now_utc()
    await _log_at(user, rec, gid, week, now, 3)
    await _log_at(user, rec2, gid, week, now, 10)
    await _log_at(user, rec, gid, week, now, 4)
    stats = await stats_service.me(user)
    assert [(r.name, r.count) for r in stats.by_recitation] == [("Zikr", 10), ("Darood", 7)]


# ── streaks ─────────────────────────────────────────────────────────────────

async def test_streak_break_and_resume(init_db):
    user, rec, _, gid, week = await _setup()
    today = clock.local_today()
    for back in (6, 5, 4):                           # a 3-day run…
        await _log_at(user, rec, gid, week, _local(today - timedelta(days=back), 9, 0))
    s = await streak_service.compute(user.id)
    assert (s.current, s.best) == (0, 3)            # …that has broken
    await _log_at(user, rec, gid, week, _local(today - timedelta(days=1), 9, 0))
    s = await streak_service.compute(user.id)
    assert (s.current, s.best) == (1, 3)            # yesterday keeps it alive this morning
    await _log_at(user, rec, gid, week, _local(today, 9, 0))
    s = await streak_service.compute(user.id)
    assert (s.current, s.best) == (2, 3)


async def test_two_logs_same_day_count_once(init_db):
    user, rec, _, gid, week = await _setup()
    today = clock.local_today()
    await _log_at(user, rec, gid, week, _local(today, 6, 0))
    await _log_at(user, rec, gid, week, _local(today, 22, 0))
    assert (await streak_service.compute(user.id)).current == 1


async def test_submit_and_undo_update_stored_streak(init_db):
    user, rec, _, gid, _ = await _setup()
    res = await submission_service.submit(user, SubmissionCreate(
        group_id=gid, recitation_id=rec.id, count=5, client_uuid="s1"), notify=False)
    assert res.totals.streak == 1
    u = await get(User, user.id)
    assert (u.streak_days, u.best_streak) == (1, 1)
    totals = await submission_service.undo(user, res.submission.id)
    assert totals.streak == 0
    assert (await get(User, user.id)).streak_days == 0
    assert (await get(User, user.id)).best_streak == 0


async def test_bulk_returns_streak(init_db):
    from app.schemas.submission import BulkItem, BulkSubmissionCreate
    user, rec, rec2, _, _ = await _setup()
    totals = await submission_service.submit_bulk(user, BulkSubmissionCreate(items=[
        BulkItem(recitation_id=rec.id, count=1, client_uuid="a"),
        BulkItem(recitation_id=rec2.id, count=2, client_uuid="b")]))
    assert totals.streak == 1 and totals.user_lifetime == 3


async def test_nightly_job_resets_only_missed_days(clean_db):
    async with session_scope():
        user, rec, _, gid, week = await _setup()
        other = await insert(User(firebase_uid="b1-other", display_name="Other"))
        today = clock.local_today()
        await _log_at(other, rec, gid, week, _local(today - timedelta(days=1), 20, 0))
        await _log_at(user, rec, gid, week, _local(today - timedelta(days=3), 20, 0))
        user.streak_days, other.streak_days = 4, 2
        await insert(user)
        await insert(other)
        uid, oid = user.id, other.id
    async with session_scope():
        assert await streak_service.reset_missed() == 1
    async with session_scope():
        assert (await get(User, uid)).streak_days == 0      # missed yesterday
        assert (await get(User, oid)).streak_days == 2      # logged yesterday


def test_daily_job_time_is_local():
    nxt = daily_at(0, 5, clock.tz())
    # 23:30 IST (18:00 UTC) → 00:05 IST next day = 18:35 UTC the same UTC day.
    assert nxt(datetime(2026, 1, 1, 18, 0, tzinfo=UTC)) == datetime(2026, 1, 1, 18, 35, tzinfo=UTC)
    assert nxt(datetime(2026, 1, 1, 18, 35, tzinfo=UTC)) == datetime(2026, 1, 2, 18, 35, tzinfo=UTC)


# ── cache ───────────────────────────────────────────────────────────────────

class _FakeRedis:
    def __init__(self):
        self.data = {}

    async def get(self, k):
        return self.data.get(k)

    async def set(self, k, v, ex=None):
        self.data[k] = v

    async def delete(self, k):
        self.data.pop(k, None)

    async def scan_iter(self, match):
        prefix = match.rstrip("*")
        for k in [k for k in self.data if k.startswith(prefix)]:
            yield k


async def test_stats_cached_and_cleared_by_a_log(init_db, monkeypatch):
    monkeypatch.setattr(cache, "client", _FakeRedis())
    user, rec, _, gid, _ = await _setup()
    first = await stats_service.me(user)
    assert first.lifetime == 0
    assert f"stats:me:{user.id}" in cache.client.data
    await submission_service.submit(user, SubmissionCreate(
        group_id=gid, recitation_id=rec.id, count=9, client_uuid="c1"), notify=False)
    assert f"stats:me:{user.id}" not in cache.client.data    # cleared
    assert (await stats_service.me(user)).lifetime == 9


# ── HTTP: /statistics/me, preferences, paging ───────────────────────────────

async def _client_and_token(client):
    r = await client.post("/api/v1/auth/register", json={
        "email": "b1@example.com", "password": "password123", "display_name": "B1"})
    assert r.status_code == 201, r.text
    return {"Authorization": f"Bearer {r.json()['access_token']}"}


async def test_http_preferences_round_trip_and_validation(clean_db):
    from app.main import app
    async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app), base_url="http://t") as c:
        h = await _client_and_token(c)
        r = await c.get("/api/v1/users/me", headers=h)
        prefs = r.json()["preferences"]
        assert prefs["hide_on_boards"] is False and prefs["notify"]["groups"] is True

        r = await c.patch("/api/v1/users/me", headers=h, json={"preferences": {
            "hide_on_boards": True, "reminders": {"enabled": True, "time": "05:45"}}})
        assert r.status_code == 200, r.text
        r = await c.patch("/api/v1/users/me", headers=h, json={"preferences": {
            "notify": {"activity": False}}})          # partial: earlier changes stay
        p = (await c.get("/api/v1/users/me", headers=h)).json()["preferences"]
        assert p["hide_on_boards"] is True
        assert p["reminders"] == {"enabled": True, "time": "05:45", "days": [0, 1, 2, 3, 4, 5, 6]}
        assert p["notify"]["activity"] is False and p["notify"]["system"] is True

        for bad in ({"anything": 1}, {"reminders": {"time": "24:00"}},
                    {"reminders": {"days": [7]}}, {"prayer_method": 99},
                    {"notify": {"marketing": True}}):
            r = await c.patch("/api/v1/users/me", headers=h, json={"preferences": bad})
            assert r.status_code == 422, (bad, r.text)


async def test_http_stats_me_and_submission_paging(clean_db):
    from app.main import app

    async def seed():
        rec = await insert(Recitation(arabic_name="درود", english_name="Darood"))
        return rec.id
    async with session_scope():
        rid = await seed()

    async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app), base_url="http://t") as c:
        h = await _client_and_token(c)
        for i in range(5):
            r = await c.post("/api/v1/submissions/bulk", headers=h, json={
                "items": [{"recitation_id": str(rid), "count": i + 1, "client_uuid": f"p{i}"}]})
            assert r.status_code == 200, r.text

        r = await c.get("/api/v1/statistics/me", headers=h)
        assert r.status_code == 200, r.text
        s = r.json()
        assert s["lifetime"] == 15 and s["today"] == 15 and s["week"] == 15
        assert s["current_streak"] == 1 and s["best_streak"] == 1
        assert s["days"] == [{"date": str(clock.local_today()), "total": 15}]
        assert s["by_recitation"][0]["count"] == 15

        seen, cursor, pages = [], None, 0
        while True:
            params = {"limit": 2} | ({"before": cursor} if cursor else {})
            r = await c.get("/api/v1/submissions/me", headers=h, params=params)
            assert r.status_code == 200, r.text
            seen += [x["count"] for x in r.json()]
            pages += 1
            cursor = r.headers.get("x-next-cursor")
            if not cursor:
                break
        assert seen == [5, 4, 3, 2, 1] and pages == 3

        r = await c.get("/api/v1/submissions/me", headers=h, params={"before": "nonsense"})
        assert r.status_code == 422


async def test_stats_me_is_fast_with_5000_submissions(init_db):
    user, rec, rec2, gid, week = await _setup()
    from sqlalchemy import insert as sa_insert
    from app.database.postgres import db
    import uuid as _uuid
    now = clock.now_utc()
    rows = [dict(id=_uuid.uuid4(), group_id=gid, week_id=week.id, user_id=user.id,
                 recitation_id=(rec.id if i % 3 else rec2.id), count=(i % 50) + 1,
                 created_at=now - timedelta(hours=i * 1.7))      # spread over ~354 days
            for i in range(5000)]
    await db().execute(sa_insert(Submission), rows)
    t = time.perf_counter()
    stats = await stats_service.me(user)
    ms = (time.perf_counter() - t) * 1000
    assert stats.lifetime == sum(r["count"] for r in rows)
    assert ms < 300, f"took {ms:.0f} ms"
