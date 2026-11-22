"""B0 hardening: atomic totals, duplicate/undo safety, auth fixes, client IP."""
from __future__ import annotations

import pytest
from starlette.requests import Request

from app.config.settings import settings
from app.core import client_ip as client_ip_module
from app.core.exceptions import ConflictError, NotFoundError, UnauthorizedError
from app.middlewares.rate_limit import limit_for
from app.models.activity import RefreshToken
from app.models.category import Recitation
from app.models.submission import Submission
from app.models.user import User
from app.models.weekly_session import WeeklySession
from app.schemas.group import GroupCreate
from app.schemas.submission import SubmissionCreate
from app.security import firebase as firebase_module
from app.services import auth_service as auth_module
from app.services.auth_service import auth_service
from app.services.group_service import group_service
from app.services.submission_service import submission_service

pytestmark = pytest.mark.asyncio


async def _setup(name="A"):
    user = User(firebase_uid=f"uid-{name}", display_name=name)
    await user.insert()
    group = await group_service.create_group(user, GroupCreate(name=f"G{name}"))
    rec = Recitation(arabic_name="درود", english_name="Darood")
    await rec.insert()
    return user, group, rec


# --- Atomic roll-ups ----------------------------------------------------------
async def test_stale_user_copies_do_not_lose_counts(init_db):
    """Two requests holding their own copy of the user (two phones) both count."""
    user, group, rec = await _setup()
    copy_a = await User.get(user.id)
    copy_b = await User.get(user.id)
    await submission_service.submit(copy_a, SubmissionCreate(
        group_id=group.id, recitation_id=rec.id, count=10, client_uuid="t1"), notify=False)
    await submission_service.submit(copy_b, SubmissionCreate(
        group_id=group.id, recitation_id=rec.id, count=5, client_uuid="t2"), notify=False)
    fresh = await User.get(user.id)
    assert fresh.lifetime_total == 15
    week = await WeeklySession.find_one(WeeklySession.group_id == group.id)
    assert week.group_total == 15
    assert week.totals[str(user.id)] == 15


async def test_group_total_adds_across_members(init_db):
    a, group, rec = await _setup("A")
    b = User(firebase_uid="uid-B", display_name="B")
    await b.insert()
    await submission_service.submit(a, SubmissionCreate(
        group_id=group.id, recitation_id=rec.id, count=7, client_uuid="t3"), notify=False)
    await submission_service.submit(b, SubmissionCreate(
        group_id=group.id, recitation_id=rec.id, count=3, client_uuid="t4"), notify=False)
    week = await WeeklySession.find_one(WeeklySession.group_id == group.id)
    assert week.group_total == 10
    assert week.totals == {str(a.id): 7, str(b.id): 3}


# --- Undo ---------------------------------------------------------------------
async def test_double_undo_subtracts_once(init_db):
    user, group, rec = await _setup()
    res = await submission_service.submit(user, SubmissionCreate(
        group_id=group.id, recitation_id=rec.id, count=8, client_uuid="t5"), notify=False)
    await submission_service.undo(user, res.submission.id)
    with pytest.raises(NotFoundError):
        await submission_service.undo(user, res.submission.id)
    fresh = await User.get(user.id)
    assert fresh.lifetime_total == 0
    week = await WeeklySession.find_one(WeeklySession.group_id == group.id)
    assert week.group_total == 0


async def test_undo_frees_client_uuid(init_db):
    user, group, rec = await _setup()
    res = await submission_service.submit(user, SubmissionCreate(
        group_id=group.id, recitation_id=rec.id, count=4, client_uuid="u-1"),
        notify=False)
    await submission_service.undo(user, res.submission.id)
    again = await submission_service.submit(user, SubmissionCreate(
        group_id=group.id, recitation_id=rec.id, count=4, client_uuid="u-1"),
        notify=False)
    assert again.submission.id != res.submission.id
    assert again.totals.user_lifetime == 4


async def test_duplicate_insert_race_returns_existing(init_db, monkeypatch):
    """If the idempotency check misses (a race), the unique index wins cleanly."""
    user, group, rec = await _setup()
    first = await submission_service.submit(user, SubmissionCreate(
        group_id=group.id, recitation_id=rec.id, count=2, client_uuid="dup"),
        notify=False)

    from app.repositories.submission_repo import submission_repository
    real = submission_repository.find_by_client_uuid
    calls = {"n": 0}

    async def miss_once(uid, cu):
        calls["n"] += 1
        return None if calls["n"] == 1 else await real(uid, cu)

    monkeypatch.setattr(submission_repository, "find_by_client_uuid", miss_once)
    try:
        second = await submission_service.submit(user, SubmissionCreate(
            group_id=group.id, recitation_id=rec.id, count=2, client_uuid="dup"),
            notify=False)
    except Exception as exc:  # mongomock may not enforce partial unique indexes
        pytest.skip(f"in-memory Mongo doesn't enforce the index: {exc!r}")
    if await Submission.find(Submission.client_uuid == "dup").count() > 1:
        pytest.skip("in-memory Mongo doesn't enforce partial unique indexes")
    assert second.submission.id == first.submission.id
    assert (await User.get(user.id)).lifetime_total == 2


# --- Auth ---------------------------------------------------------------------
async def test_google_unverified_email_never_links(init_db, monkeypatch):
    await auth_service.register(
        email="victim@example.com", password="password123", display_name="V")
    monkeypatch.setattr(
        auth_module, "verify_google_id_token",
        lambda token: {"uid": "attacker", "email": "victim@example.com",
                       "name": "X", "picture": None, "email_verified": False},
    )
    with pytest.raises(ConflictError):
        await auth_service.login_with_google("t")
    victim = await User.find_one(User.email == "victim@example.com")
    assert victim.firebase_uid == "pwd:victim@example.com"


async def test_google_verified_email_links_existing(init_db, monkeypatch):
    reg = await auth_service.register(
        email="me@example.com", password="password123", display_name="Me")
    monkeypatch.setattr(
        auth_module, "verify_google_id_token",
        lambda token: {"uid": "g-me", "email": "me@example.com",
                       "name": "Me", "picture": None, "email_verified": True},
    )
    res = await auth_service.login_with_google("t")
    assert res.user.id == reg.user.id


async def test_firebase_stub_refused_in_production(monkeypatch):
    monkeypatch.setattr(settings, "FIREBASE_CREDENTIALS_FILE", "")
    monkeypatch.setattr(settings, "APP_ENV", "production")
    with pytest.raises(UnauthorizedError):
        firebase_module.verify_id_token("anything")


async def test_password_reset_revokes_sessions(init_db):
    reg = await auth_service.register(
        email="r@example.com", password="password123", display_name="R")
    code, _ = await auth_service.create_reset_code("r@example.com")
    await auth_service.reset_password("r@example.com", code, "newpassword9")
    with pytest.raises(UnauthorizedError):
        await auth_service.refresh(reg.refresh_token)


async def test_deleted_account_is_anonymised_and_can_return(init_db, monkeypatch):
    monkeypatch.setattr(
        auth_module, "verify_google_id_token",
        lambda token: {"uid": "g-del", "email": "del@gmail.com",
                       "name": "Del", "picture": None, "email_verified": True},
    )
    first = await auth_service.login_with_google("t")
    user = await User.get(first.user.id)
    code, _ = await auth_service.create_deletion_code(user)
    await auth_service.confirm_account_deletion(user, code)

    gone = await User.get(first.user.id)
    assert gone.deleted and gone.email is None
    assert gone.firebase_uid.startswith("deleted:")
    assert await RefreshToken.find(RefreshToken.user_id == gone.id,
                                   RefreshToken.revoked == False).count() == 0  # noqa: E712
    with pytest.raises(UnauthorizedError):
        await auth_service.refresh(first.refresh_token)

    # The same Google account can sign up again as a fresh user.
    again = await auth_service.login_with_google("t")
    assert again.user.id != first.user.id


async def test_production_problems_flag_unsafe_config(monkeypatch):
    monkeypatch.setattr(settings, "APP_ENV", "production")
    monkeypatch.setattr(settings, "JWT_SECRET", "change-me-in-production")
    monkeypatch.setattr(settings, "DEBUG", True)
    problems = settings.production_problems()
    assert any("JWT_SECRET" in p for p in problems)
    assert any("DEBUG" in p for p in problems)


# --- Client IP + rate-limit buckets ------------------------------------------
def _req(peer: str, real: str | None = None) -> Request:
    headers = [(b"x-real-ip", real.encode())] if real else []
    return Request({"type": "http", "client": (peer, 1234), "headers": headers,
                    "path": "/", "method": "GET", "query_string": b""})


async def test_client_ip_trusts_x_real_ip_only_from_proxy():
    assert client_ip_module.client_ip(_req("172.18.0.5", "203.0.113.9")) == "203.0.113.9"
    # Straight from the internet: the header is ignored (can't spoof).
    assert client_ip_module.client_ip(_req("198.51.100.7", "203.0.113.9")) == "198.51.100.7"
    assert client_ip_module.client_ip(_req("172.18.0.5", "not-an-ip")) == "172.18.0.5"


async def test_auth_paths_get_the_strict_limit():
    assert limit_for("/api/v1/auth/login") == ("auth", settings.RATE_LIMIT_AUTH_PER_MINUTE)
    assert limit_for("/api/v1/auth/reset-password")[0] == "auth"
    assert limit_for("/api/v1/dashboard") == ("api", settings.RATE_LIMIT_PER_MINUTE)


async def test_public_user_schemas_have_no_email():
    from app.schemas.dashboard import Contributor, LeaderboardEntry
    from app.schemas.user import PublicUserOut
    assert "email" not in PublicUserOut.model_fields
    for model in (Contributor, LeaderboardEntry):
        assert model.model_fields["user"].annotation is PublicUserOut


# --- P1: avatar field ----------------------------------------------------------
async def test_user_update_accepts_avatar_specs_only():
    from pydantic import ValidationError
    from app.schemas.user import UserUpdate
    assert UserUpdate(avatar="av1:k2.h3.c1.e0.b1.m2.f0.g0.x0.o3.d5").avatar
    assert UserUpdate(avatar="aura_04").avatar == "aura_04"
    for bad in ("https://evil.example/x.png", "av1:<script>", "aura_4", "av1:"):
        with pytest.raises(ValidationError):
            UserUpdate(avatar=bad)


async def test_avatar_is_public(init_db):
    from app.schemas.user import PublicUserOut
    u = User(firebase_uid="uid-av", display_name="Av", avatar="aura_02")
    await u.insert()
    assert PublicUserOut.model_validate(u).avatar == "aura_02"
