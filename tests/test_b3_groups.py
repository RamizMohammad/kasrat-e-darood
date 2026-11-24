"""B3: groups exist, and one log counts on every board you belong to."""
from __future__ import annotations

import httpx
import pytest

from app.config.settings import settings
from app.core.exceptions import ConflictError, GoneError, PermissionDeniedError, ValidationAppError
from app.database.postgres import session_scope
from app.models.group import MemberRole, Privacy
from app.schemas.group import GroupDetail, GroupPreview, GroupUpdate
from app.security.tokens import create_access_token
from app.services.community_service import community_service
from app.services.dashboard_service import dashboard_service
from app.services.group_service import CODE_ALPHABET, group_service
from app.services.join_service import join_service
from app.services.member_service import member_service
from app.services.weekly_service import weekly_service
from tests import group_helpers as h

pytestmark = pytest.mark.asyncio


async def test_create_gives_a_6_character_code_and_owner_membership(init_db):
    owner = await h.user("Owner")
    g = await h.group(owner, "  Family   Circle ")
    assert g.name == "Family Circle"
    assert len(g.invite_code) == 6 and set(g.invite_code) <= set(CODE_ALPHABET)
    detail = await group_service.detail(owner, g.id)
    assert isinstance(detail, GroupDetail)
    assert detail.role == MemberRole.OWNER and detail.member_count == 1
    assert detail.invite_link.endswith(f"/j/{g.invite_code}")


async def test_one_log_counts_on_global_and_every_group(init_db):
    rec = await h.recitation()
    a = await h.user("Ali")
    g1, g2 = await h.group(a, "Family"), await h.group(a, "Masjid")
    r = await h.log(a, rec, 11)
    assert r.submission.group_id is None
    week = await weekly_service.current()
    assert week.group_total == 11
    for g in (g1, g2):
        board = await group_service.board(a, g.id, "week", False, 100)
        assert board.total == 11 and board.me.total == 11 and board.me.rank == 1
    mine = await group_service.mine(a)
    assert mine[0].is_global and mine[0].week_total == 11
    assert {m.name for m in mine[1:]} == {"Family", "Masjid"}
    assert all(m.week_total == 11 and m.my_week_total == 11 for m in mine)


async def test_board_counts_logs_only_since_joining(init_db):
    rec = await h.recitation()
    owner, late = await h.user("Owner"), await h.user("Late")
    g = await h.group(owner)
    await h.log(owner, rec, 10)
    await h.log(late, rec, 7)              # before joining: global only
    await h.add(g, late)
    await h.log(late, rec, 3)
    board = await group_service.board(owner, g.id, "week", False, 100)
    assert [(e.user.display_name, e.total) for e in board.entries] == [("Owner", 10), ("Late", 3)]
    assert board.total == 13
    all_time = await group_service.board(owner, g.id, "all", False, 100)
    assert all_time.total == 13
    glob = await weekly_service.current()
    assert glob.group_total == 20


async def test_members_who_left_drop_off_and_rejoin_from_scratch(init_db):
    rec = await h.recitation()
    owner, m = await h.user("Owner"), await h.user("M")
    g = await h.group(owner)
    await h.add(g, m)
    await h.log(m, rec, 5)
    await member_service.leave(m, g.id)
    board = await group_service.board(owner, g.id, "week", False, 100)
    assert board.total == 0 and [e.user.display_name for e in board.entries] == ["Owner"]
    await h.add(g, m)
    board = await group_service.board(owner, g.id, "week", False, 100)
    assert board.total == 0 and len(board.entries) == 2


async def test_members_with_no_logs_are_on_the_board_at_zero(init_db):
    owner, quiet = await h.user("Owner"), await h.user("Quiet")
    g = await h.group(owner)
    await h.add(g, quiet)
    rec = await h.recitation()
    await h.log(owner, rec, 2)
    board = await group_service.board(quiet, g.id, "week", False, 100)
    assert [(e.rank, e.total) for e in board.entries] == [(1, 2), (2, 0)]


async def test_total_only_hides_the_board_from_members(init_db):
    rec = await h.recitation()
    owner, m = await h.user("Owner"), await h.user("Member")
    g = await h.group(owner, total_only=True)
    await h.add(g, m)
    await h.log(owner, rec, 4)
    await h.log(m, rec, 6)
    seen = await group_service.board(m, g.id, "week", False, 100)
    assert seen.total_only and seen.entries == [] and seen.total == 10
    assert seen.me.total == 6 and seen.me.rank is None
    admin_view = await group_service.board(owner, g.id, "week", False, 100)
    assert len(admin_view.entries) == 2
    preview = await group_service.preview(g, await h.user("Outsider"))
    assert preview.top == [] and preview.week_total == 10


async def test_hidden_member_is_a_member_on_group_board(init_db):
    rec = await h.recitation()
    owner = await h.user("Owner")
    shy = await h.user("Shy", preferences={"hide_on_boards": True})
    g = await h.group(owner)
    await h.add(g, shy)
    await h.log(shy, rec, 3)
    board = await group_service.board(owner, g.id, "week", False, 100)
    assert board.entries[0].user.display_name == "A member"


async def test_logging_into_a_group_you_are_not_in_is_refused(init_db):
    rec = await h.recitation()
    owner, other = await h.user("Owner"), await h.user("Other")
    g = await h.group(owner)
    with pytest.raises(PermissionDeniedError):
        await h.log(other, rec, 1, group_id=g.id)
    # v2 apps send the global group's id: still fine.
    glob = await community_service.get_or_create_global_group()
    r = await h.log(other, rec, 1, group_id=glob.id)
    assert r.totals.user_week == 1


async def test_owned_group_limit(init_db, monkeypatch):
    monkeypatch.setattr(settings, "GROUPS_MAX_OWNED", 2)
    owner = await h.user("Owner")
    await h.group(owner, "One one")
    await h.group(owner, "Two two")
    with pytest.raises(ConflictError) as e:
        await h.group(owner, "Three three")
    assert e.value.code == "too_many_groups"


async def test_edit_needs_admin_and_global_is_locked(init_db):
    owner, m = await h.user("Owner"), await h.user("Member")
    g = await h.group(owner)
    await h.add(g, m)
    with pytest.raises(PermissionDeniedError):
        await group_service.update_group(g.id, GroupUpdate(name="Taken over"), m)
    d = await group_service.update_group(g.id, GroupUpdate(weekly_goal=500, privacy=Privacy.PUBLIC), owner)
    assert d.weekly_goal == 500 and d.privacy == Privacy.PUBLIC
    d = await group_service.update_group(g.id, GroupUpdate(weekly_goal=None), owner)
    assert d.weekly_goal is None
    with pytest.raises(ValidationAppError):
        await group_service.update_group(g.id, GroupUpdate(name=None), owner)
    glob = await community_service.get_or_create_global_group()
    with pytest.raises(PermissionDeniedError):
        await group_service.update_group(glob.id, GroupUpdate(name="Mine now"), owner)


async def test_non_members_get_a_preview_of_public_groups_only(init_db):
    owner, outsider = await h.user("Owner"), await h.user("Outsider")
    public = await h.group(owner, "Open Circle", privacy=Privacy.PUBLIC)
    private = await h.group(owner, "Closed Circle")
    p = await group_service.detail(outsider, public.id)
    assert isinstance(p, GroupPreview) and p.viewer_status == "none"
    assert not hasattr(p, "invite_code")
    from app.core.exceptions import NotFoundError
    with pytest.raises(NotFoundError):
        await group_service.detail(outsider, private.id)


async def test_deleted_group_is_hidden_everywhere(init_db):
    rec = await h.recitation()
    owner, m = await h.user("Owner"), await h.user("Member")
    g = await h.group(owner, "Doomed Circle", privacy=Privacy.PUBLIC)
    await h.add(g, m)
    await h.log(m, rec, 5)
    with pytest.raises(ValidationAppError):
        await group_service.delete_group(g.id, owner, "wrong name")
    with pytest.raises(PermissionDeniedError):
        await group_service.delete_group(g.id, m, "Doomed Circle")
    await group_service.delete_group(g.id, owner, "doomed   circle")
    assert [x.name for x in await group_service.mine(m)] == ["Noor Community"]
    assert (await group_service.ranks(m, "total")).entries == []
    assert (await join_service.discover(m, None, "new", 0, 20))[0] == []
    assert (await dashboard_service.build(m, None)).groups_this_week == []
    for call in (group_service.detail(m, g.id), join_service.preview_code(g.invite_code, m)):
        with pytest.raises(GoneError):
            await call
    assert h.outbox("topic", f"group_{g.id}")[-1]["data"]["type"] == "group.deleted"
    # Their recitations stay in their own stats.
    assert (await weekly_service.current()).group_total == 5


async def test_groups_leaderboard_total_and_average(init_db):
    rec = await h.recitation()
    a, b, c, me = await h.user("A"), await h.user("B"), await h.user("C"), await h.user("Me")
    big = await h.group(a, "Big Circle", privacy=Privacy.PUBLIC)
    await h.add(big, b)
    await h.add(big, c)
    small = await h.group(me, "Small Circle")             # private, but mine
    hidden = await h.group(b, "Private Other")             # private, not mine
    await h.log(a, rec, 10)
    await h.log(b, rec, 10)
    await h.log(c, rec, 10)                                # big: 30 / 3 = 10
    await h.log(me, rec, 12)                               # small: 12 / 1 = 12
    total = await group_service.ranks(me, "total")
    assert [e.group.name for e in total.entries] == ["Big Circle", "Small Circle"]
    assert hidden.id not in {e.group.id for e in total.entries}
    avg = await group_service.ranks(me, "avg")
    assert [(e.group.name, e.average, e.rank) for e in avg.entries] == [
        ("Small Circle", 12.0, 1), ("Big Circle", 10.0, 2)]
    assert [e.group.name for e in avg.mine] == ["Small Circle"]


async def test_dashboard_lists_groups_this_week(init_db):
    rec = await h.recitation()
    a = await h.user("Ali")
    g = await h.group(a, "Family", weekly_goal=100)
    await h.log(a, rec, 40)
    d = await dashboard_service.build(a, None)
    assert [(x.name, x.week_total, x.weekly_goal) for x in d.groups_this_week] == [("Family", 40, 100)]
    assert d.today_total == 40 and d.group_total == 40


async def test_http_group_lifecycle(clean_db):
    from app.main import app
    async with session_scope():
        u = await h.user("Ali")
        uid = u.id
    hdr = {"Authorization": f"Bearer {create_access_token(str(uid))}"}
    async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app), base_url="http://t") as c:
        r = await c.post("/api/v1/groups", headers=hdr, json={
            "name": "حلقہ درود", "privacy": "public", "weekly_goal": 11000})
        assert r.status_code == 201, r.text
        gid = r.json()["id"]
        assert r.json()["name"] == "حلقہ درود" and r.json()["role"] == "owner"
        r = await c.post("/api/v1/groups", headers=hdr, json={"name": "ab"})
        assert r.status_code == 422
        r = await c.post("/api/v1/groups", headers=hdr, json={"name": "Okay", "colour": "red"})
        assert r.status_code == 422                                     # unknown keys
        r = await c.get("/api/v1/groups/mine", headers=hdr)
        assert [x["is_global"] for x in r.json()] == [True, False]
        r = await c.get(f"/api/v1/groups/{gid}/board?scope=all", headers=hdr)
        assert r.status_code == 200 and r.json()["goal"] == 11000
        r = await c.patch(f"/api/v1/groups/{gid}", headers=hdr, json={"total_only": True})
        assert r.json()["total_only"] is True
        r = await c.get("/api/v1/leaderboards/groups?metric=avg", headers=hdr)
        assert r.status_code == 200 and r.json()["mine"][0]["group"]["id"] == gid
        r = await c.request("DELETE", f"/api/v1/groups/{gid}", headers=hdr,
                            json={"confirm_name": "حلقہ درود"})
        assert r.status_code == 200, r.text
        r = await c.get(f"/api/v1/groups/{gid}", headers=hdr)
        assert r.status_code == 410 and r.json()["error"]["code"] == "group_deleted"
