"""B4: every way into a group — code, link page, Discover, requests — and
the checks on each (banned, already in, full, deleted, rate limit)."""
from __future__ import annotations

from datetime import timedelta

import httpx
import pytest
from sqlalchemy import select

from app.config.settings import settings
from app.core.exceptions import (
    ConflictError,
    GoneError,
    NotFoundError,
    PermissionDeniedError,
    TooManyError,
)
from app.database.postgres import db, session_scope
from app.models.activity import Notification
from app.models.group import JoinRule, MemberRole, Privacy
from app.repositories.group_repo import join_request_repository, membership_repository
from app.security.tokens import create_access_token
from app.services.group_service import group_service
from app.services.join_service import join_service
from app.services.member_service import member_service
from tests import group_helpers as h

pytestmark = pytest.mark.asyncio


async def _inbox(user):
    rows = await db().execute(select(Notification).where(Notification.user_id == user.id))
    return [n.type for n in rows.scalars()]


async def test_join_by_code_any_case(init_db):
    owner, a = await h.user("Owner"), await h.user("Ali")
    g = await h.group(owner)
    r = await join_service.join_code(a, g.invite_code.lower())
    assert r.status == "joined" and r.group.member_count == 2
    with pytest.raises(ConflictError) as e:
        await join_service.join_code(a, g.invite_code)
    assert e.value.code == "already_member"
    with pytest.raises(NotFoundError) as e:
        await join_service.join_code(a, "ZZZZZZ")
    assert e.value.code == "invalid_code"


async def test_preview_shows_top_three_and_your_status(init_db):
    rec = await h.recitation()
    owner = await h.user("Owner")
    g = await h.group(owner, weekly_goal=1000)
    for i, n in enumerate([5, 9, 7, 1]):
        u = await h.user(f"M{i}")
        await h.add(g, u)
        await h.log(u, rec, n)
    p = await join_service.preview_code(g.invite_code, None)
    assert [e.total for e in p.top] == [9, 7, 5]
    assert p.member_count == 5 and p.week_total == 22 and p.weekly_goal == 1000
    assert p.viewer_status == "none"
    assert (await join_service.preview_code(g.invite_code, owner)).viewer_status == "member"


async def test_approval_rule_request_approve_and_decline(init_db):
    owner, mod, a, b = (await h.user("Owner"), await h.user("Mod"),
                        await h.user("Ali"), await h.user("Bilal"))
    g = await h.group(owner, join_rule=JoinRule.APPROVAL)
    await h.add(g, mod, MemberRole.MODERATOR)
    r = await join_service.join_code(a, g.invite_code)
    assert r.status == "requested"
    assert (await join_service.join_code(a, g.invite_code)).status == "requested"   # asking twice is fine
    assert (await join_service.preview_code(g.invite_code, a)).viewer_status == "requested"
    assert "group.request" in await _inbox(owner) and "group.request" in await _inbox(mod)

    reqs, _ = await join_service.requests(mod, g.id, None, 50)
    assert [x.user.display_name for x in reqs] == ["Ali"]
    await join_service.approve(mod, g.id, a.id)
    assert (await membership_repository.get(g.id, a.id)).role == MemberRole.MEMBER
    assert "group.request.approved" in await _inbox(a)

    await join_service.join_code(b, g.invite_code)
    await join_service.decline(owner, g.id, b.id)
    assert "group.request.declined" in await _inbox(b)
    with pytest.raises(ConflictError) as e:
        await join_service.join_code(b, g.invite_code)
    assert e.value.code == "request_too_soon"
    # Seven days later they may ask again.
    declined = await join_request_repository.last_declined(g.id, b.id)
    declined.decided_at -= timedelta(days=settings.GROUP_REQUEST_RETRY_DAYS, minutes=1)
    await join_request_repository.save(declined)
    assert (await join_service.join_code(b, g.invite_code)).status == "requested"


async def test_members_cannot_decide_requests(init_db):
    owner, m, a = await h.user("Owner"), await h.user("Member"), await h.user("Ali")
    g = await h.group(owner, join_rule=JoinRule.APPROVAL)
    await h.add(g, m)
    await join_service.join_code(a, g.invite_code)
    with pytest.raises(PermissionDeniedError):
        await join_service.approve(m, g.id, a.id)
    with pytest.raises(PermissionDeniedError):
        await join_service.requests(m, g.id, None, 50)


async def test_approve_many_stops_when_full(init_db):
    owner = await h.user("Owner")
    g = await h.group(owner, join_rule=JoinRule.APPROVAL)
    g.member_limit = 3
    people = [await h.user(f"P{i}") for i in range(4)]
    for p in people:
        await join_service.join_code(p, g.invite_code)
    result = await join_service.approve_many(owner, g.id, [p.id for p in people])
    assert result.approved == 2 and g.member_count == 3


async def test_banned_cannot_join_and_full_is_full(init_db):
    owner, a, b = await h.user("Owner"), await h.user("Ali"), await h.user("Bilal")
    g = await h.group(owner)
    await h.add(g, a)
    await member_service.ban(owner, g.id, a.id)
    with pytest.raises(PermissionDeniedError) as e:
        await join_service.join_code(a, g.invite_code)
    assert e.value.code == "banned"
    assert (await join_service.preview_code(g.invite_code, a)).viewer_status == "banned"
    with pytest.raises(PermissionDeniedError):
        await group_service.detail(a, g.id)
    g.member_limit = 1                                      # only the owner fits now
    with pytest.raises(ConflictError) as e:
        await join_service.join_code(b, g.invite_code)
    assert e.value.code == "group_full"
    assert (await join_service.preview_code(g.invite_code, b)).full


async def test_deleted_group_code_is_gone(init_db):
    owner, a = await h.user("Owner"), await h.user("Ali")
    g = await h.group(owner, "Gone Circle")
    await group_service.delete_group(g.id, owner, "Gone Circle")
    with pytest.raises(GoneError):
        await join_service.join_code(a, g.invite_code)


async def test_reset_code_invalidates_the_old_one(init_db):
    owner, m, a = await h.user("Owner"), await h.user("Member"), await h.user("Ali")
    g = await h.group(owner)
    await h.add(g, m)
    old = g.invite_code
    with pytest.raises(PermissionDeniedError):
        await join_service.reset_code(m, g.id)               # members share, admins reset
    assert (await join_service.invite(m, g.id)).code == old
    new = await join_service.reset_code(owner, g.id)
    assert new.code != old and new.link.endswith("/j/" + new.code)
    assert f"code {new.code}" in new.share_text
    with pytest.raises(NotFoundError):
        await join_service.join_code(a, old)
    assert (await join_service.join_code(a, new.code)).status == "joined"


async def test_public_join_from_discover(init_db):
    owner, a = await h.user("Owner"), await h.user("Ali")
    public = await h.group(owner, "Open Circle", privacy=Privacy.PUBLIC)
    private = await h.group(owner, "Closed Circle")
    assert (await join_service.join_public(a, public.id)).status == "joined"
    with pytest.raises(NotFoundError):
        await join_service.join_public(a, private.id)


async def test_discover_search_and_sort(init_db):
    rec = await h.recitation()
    owner, me = await h.user("Owner"), await h.user("Me")
    quiet = await h.group(owner, "Quiet Circle", privacy=Privacy.PUBLIC, description="Friday durood")
    busy = await h.group(owner, "Busy Circle", privacy=Privacy.PUBLIC)
    urdu = await h.group(owner, "درود حلقہ", privacy=Privacy.PUBLIC)
    await h.group(owner, "Secret Circle")
    await h.log(owner, rec, 50)                    # owner is in all: equal activity...
    lone = await h.user("Lone")
    await h.add(busy, lone)
    await h.log(lone, rec, 30)                     # ...but busy has more
    items, nxt = await join_service.discover(me, None, "active", 0, 20)
    assert [g.name for g in items][0] == "Busy Circle" and nxt is None
    assert "Secret Circle" not in {g.name for g in items}
    assert [g.name for g in (await join_service.discover(me, "durood", "new", 0, 20))[0]] == ["Quiet Circle"]
    assert [g.name for g in (await join_service.discover(me, "درود", "new", 0, 20))[0]] == ["درود حلقہ"]
    assert [g.name for g in (await join_service.discover(me, "bus", "new", 0, 20))[0]] == ["Busy Circle"]
    page1, nxt = await join_service.discover(me, None, "new", 0, 2)
    assert [g.name for g in page1] == ["درود حلقہ", "Busy Circle"] and nxt == 2
    await join_service.join_public(me, quiet.id)
    statuses = {g.name: g.viewer_status for g in (await join_service.discover(me, None, "new", 0, 20))[0]}
    assert statuses["Quiet Circle"] == "member" and statuses["Busy Circle"] == "none"
    assert urdu.id


async def test_join_rate_limit(init_db, monkeypatch):
    monkeypatch.setattr(settings, "GROUP_JOINS_PER_HOUR", 2)
    owner, a = await h.user("Owner"), await h.user("Ali")
    groups = [await h.group(owner, f"Circle {i}") for i in range(3)]
    await join_service.join_code(a, groups[0].invite_code)
    await member_service.leave(a, groups[0].id)               # leaving doesn't give the join back
    await join_service.join_code(a, groups[1].invite_code)
    with pytest.raises(TooManyError):
        await join_service.join_code(a, groups[2].invite_code)


# ── HTTP: preview before sign-in, the link page, assetlinks ────────────────

async def test_http_preview_signed_out_link_page_and_assetlinks(clean_db, monkeypatch):
    from app.main import app
    monkeypatch.setattr(settings, "ANDROID_CERT_SHA256", "aa:bb, CC:DD")
    async with session_scope():
        owner, a = await h.user("Owner"), await h.user("Ali")
        g = await h.group(owner, "Noor <b>Circle</b>", join_rule=JoinRule.APPROVAL)
        code, aid, gid = g.invite_code, a.id, g.id
    hdr = {"Authorization": f"Bearer {create_access_token(str(aid))}"}
    async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app), base_url="http://t") as c:
        r = await c.get(f"/api/v1/invites/{code}")
        assert r.status_code == 200 and r.json()["viewer_status"] == "none"
        assert "invite_code" not in r.json()
        r = await c.get(f"/api/v1/invites/{code}", headers={"Authorization": "Bearer junk"})
        assert r.status_code == 200                                  # bad token = signed out
        r = await c.post(f"/api/v1/invites/{code}/join")
        assert r.status_code == 401
        r = await c.post(f"/api/v1/invites/{code}/join", headers=hdr)
        assert r.json()["status"] == "requested"
        r = await c.get(f"/api/v1/invites/{code}", headers=hdr)
        assert r.json()["viewer_status"] == "requested"
        r = await c.get("/api/v1/invites/no!pe")
        assert r.status_code == 422

        r = await c.get(f"/j/{code}")
        assert r.status_code == 200 and "text/html" in r.headers["content-type"]
        assert "Noor &lt;b&gt;Circle&lt;/b&gt;" in r.text and "<b>Circle" not in r.text
        assert f"intent://t/j/{code}" not in r.text and settings.PLAY_STORE_URL in r.text.replace("&amp;", "&")
        r = await c.get("/j/ZZZZZZ")
        assert r.status_code == 404 and "valid any more" in r.text and "ZZZZZZ" not in r.text

        r = await c.get("/.well-known/assetlinks.json")
        body = r.json()
        assert body[0]["target"]["package_name"] == settings.ANDROID_PACKAGE
        assert body[0]["target"]["sha256_cert_fingerprints"] == ["AA:BB", "CC:DD"]

        owner_hdr = {"Authorization": f"Bearer {create_access_token(str(owner.id))}"}
        r = await c.get(f"/api/v1/groups/{gid}/requests", headers=owner_hdr)
        assert [x["user"]["display_name"] for x in r.json()] == ["Ali"]
        r = await c.post(f"/api/v1/groups/{gid}/requests/approve", headers=owner_hdr,
                         json={"user_ids": [str(aid)]})
        assert r.json() == {"approved": 1, "skipped": 0}
        r = await c.get("/api/v1/notifications/inbox", headers=hdr)
        assert r.json()[0]["type"] == "group.request.approved"
