"""Behaviour that only a real PostgreSQL shows: concurrency, transactions, HTTP wiring."""
from __future__ import annotations

import asyncio
import contextvars

import httpx
import pytest

from app.core.exceptions import ValidationAppError
from app.database.postgres import session_scope
from app.models.activity import PasswordResetCode
from app.models.category import Recitation
from app.models.user import User
from app.models.weekly_session import WeeklySession
from app.repositories.submission_repo import submission_repository
from app.schemas.submission import SubmissionCreate
from app.services.auth_service import auth_service
from app.services.community_service import community_service
from app.services.submission_service import submission_service
from tests.helpers import find_one, get, insert

pytestmark = pytest.mark.asyncio


def _in_own_request(coro_fn):
    """Run in a fresh context so session_scope() opens its own session/connection."""
    return asyncio.create_task(_scoped(coro_fn), context=contextvars.Context())


async def _scoped(coro_fn):
    async with session_scope():
        return await coro_fn()


async def test_concurrent_logs_on_separate_connections_all_count(clean_db):
    async def setup():
        user = await insert(User(firebase_uid="uid-c", display_name="C"))
        rec = await insert(Recitation(arabic_name="درود", english_name="Darood"))
        gid = await community_service.resolve_group(user, None)
        return user.id, rec.id, gid
    uid, rid, gid = await _in_own_request(setup)

    async def one_log(i: int):
        user = await get(User, uid)
        await submission_service.submit(user, SubmissionCreate(
            group_id=gid, recitation_id=rid, count=3, client_uuid=f"c{i}"), notify=False)

    # 20 logs at once, each on its own connection and transaction.
    await asyncio.gather(*[_in_own_request(lambda i=i: one_log(i)) for i in range(20)])

    async def check():
        user = await get(User, uid)
        week = await find_one(WeeklySession, WeeklySession.group_id == gid)
        return user.lifetime_total, week.group_total, await submission_repository.sum_for_user_week(week.id, uid)
    lifetime, group_total, summed = await _in_own_request(check)
    assert (lifetime, group_total, summed) == (60, 60, 60)


async def test_same_client_uuid_twice_at_once_counts_once(clean_db):
    async def setup():
        user = await insert(User(firebase_uid="uid-d", display_name="D"))
        rec = await insert(Recitation(arabic_name="ذکر", english_name="Zikr"))
        gid = await community_service.resolve_group(user, None)
        return user.id, rec.id, gid
    uid, rid, gid = await _in_own_request(setup)

    async def log():
        user = await get(User, uid)
        return await submission_service.submit(user, SubmissionCreate(
            group_id=gid, recitation_id=rid, count=5, client_uuid="same"), notify=False)

    results = await asyncio.gather(*[_in_own_request(log) for _ in range(5)],
                                   return_exceptions=True)
    ok = [r for r in results if not isinstance(r, BaseException)]
    assert ok, results
    assert len({r.submission.id for r in ok}) == 1

    async def check():
        return (await get(User, uid)).lifetime_total
    assert await _in_own_request(check) == 5


async def test_wrong_otp_attempt_is_counted_even_though_request_fails(clean_db):
    async def setup():
        await auth_service.register(email="otp@example.com", password="password123",
                                    display_name="O")
        await auth_service.create_reset_code("otp@example.com")
    await _in_own_request(setup)

    async def wrong():
        await auth_service.reset_password("otp@example.com", "000000", "newpassword9")
    with pytest.raises(ValidationAppError):
        await _in_own_request(wrong)   # this "request" rolls back…

    async def check():
        code = await find_one(PasswordResetCode, PasswordResetCode.email == "otp@example.com")
        return code.attempts
    assert await _in_own_request(check) == 1   # …but the miss was kept


async def test_http_register_login_me_and_duplicate(clean_db):
    from app.main import app
    transport = httpx.ASGITransport(app=app)
    async with httpx.AsyncClient(transport=transport, base_url="http://t") as client:
        r = await client.post("/api/v1/auth/register", json={
            "email": "Http@Example.com", "password": "password123", "display_name": "Http"})
        assert r.status_code == 201, r.text
        body = r.json()
        assert body["user"]["email"] == "http@example.com"

        # Committed by the first request: a second request on a new connection sees it.
        r = await client.post("/api/v1/auth/login", json={
            "email": "http@example.com", "password": "password123"})
        assert r.status_code == 200, r.text
        token = r.json()["access_token"]

        r = await client.get("/api/v1/auth/me", headers={"Authorization": f"Bearer {token}"})
        assert r.status_code == 200 and r.json()["display_name"] == "Http"

        r = await client.post("/api/v1/auth/register", json={
            "email": "http@example.com", "password": "password123", "display_name": "Again"})
        assert r.status_code == 409
        assert r.json()["error"]["code"] == "email_taken"

        # A token for a user that doesn't exist (e.g. an old Mongo id) is a clean 401.
        from app.security.tokens import create_access_token
        r = await client.get("/api/v1/auth/me", headers={
            "Authorization": f"Bearer {create_access_token('64b7f0c2a1b2c3d4e5f60718')}"})
        assert r.status_code == 401


async def test_http_submit_and_dashboard(clean_db):
    from app.main import app

    async def seed():
        rec = await insert(Recitation(arabic_name="درود", english_name="Darood Shareef"))
        return rec.id
    rid = await _in_own_request(seed)

    transport = httpx.ASGITransport(app=app)
    async with httpx.AsyncClient(transport=transport, base_url="http://t") as client:
        r = await client.post("/api/v1/auth/register", json={
            "email": "s@example.com", "password": "password123", "display_name": "S"})
        h = {"Authorization": f"Bearer {r.json()['access_token']}"}
        r = await client.post("/api/v1/submissions/bulk", headers=h, json={
            "items": [{"recitation_id": str(rid), "count": 11, "client_uuid": "x1"}]})
        assert r.status_code == 200, r.text
        assert r.json()["user_lifetime"] == 11
        r = await client.get("/api/v1/dashboard", headers=h)
        assert r.status_code == 200, r.text
        d = r.json()
        assert d["lifetime_total"] == 11 and d["group_total"] == 11
        r = await client.get("/api/v1/leaderboards", headers=h)
        assert r.status_code == 200 and r.json()["entries"][0]["total"] == 11
        r = await client.get("/api/v1/recitations?q=shareef", headers=h)
        assert r.status_code == 200 and len(r.json()) == 1
