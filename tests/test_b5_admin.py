"""B5: running a group. Every row of the roles table, leave/transfer,
announcements, FCM topics, activity, goal-reached once, audit log."""
from __future__ import annotations

import httpx
import pytest

from app.core.exceptions import AppError, ConflictError, PermissionDeniedError
from app.database.postgres import session_scope
from app.models.group import JoinRule, MemberRole, MemberStatus
from app.repositories.group_repo import group_week_repository, membership_repository
from app.schemas.group import GroupUpdate
from app.security.permissions import Permission, can_assign, role_has
from app.security.tokens import create_access_token
from app.services import push_service
from app.services.device_service import device_service
from app.services.group_service import group_service
from app.services.join_service import join_service
from app.services.member_service import member_service
from app.services.weekly_service import weekly_service
from tests import group_helpers as h

pytestmark = pytest.mark.asyncio

O, A, MOD, MEM = MemberRole.OWNER, MemberRole.ADMIN, MemberRole.MODERATOR, MemberRole.MEMBER


# ── The roles table, as data ────────────────────────────────────────────────

ROLES_TABLE = {
    # action:                          owner  admin  moderator  member
    Permission.SUBMIT:                 (1,    1,     1,         1),
    Permission.INVITE_SHARE:           (1,    1,     1,         1),
    Permission.REQUEST_DECIDE:         (1,    1,     1,         0),
    Permission.MEMBER_MANAGE:          (1,    1,     1,         0),   # moderator: members only (rank)
    Permission.ANNOUNCE:               (1,    1,     1,         0),
    Permission.GROUP_MANAGE:           (1,    1,     0,         0),
    Permission.ROLE_ASSIGN:            (1,    1,     0,         0),
    Permission.GROUP_DELETE:           (1,    0,     0,         0),
    Permission.AUDIT_VIEW:             (1,    0,     0,         0),
}


@pytest.mark.parametrize("perm,row", list(ROLES_TABLE.items()))
def test_permission_matrix(perm, row):
    for role, allowed in zip((O, A, MOD, MEM), row):
        assert role_has(role, perm) is bool(allowed), (perm, role)


@pytest.mark.parametrize("actor,target,new,ok", [
    (O, MEM, A, True), (O, A, MEM, True), (O, MOD, A, True), (O, MEM, O, False),
    (A, MEM, A, True), (A, MEM, MOD, True), (A, MOD, MEM, True),
    (A, A, MEM, False), (A, O, MEM, False), (A, MEM, O, False),
    (MOD, MEM, MOD, False), (MEM, MEM, MOD, False),
])
def test_role_assignment_rules(actor, target, new, ok):
    assert can_assign(actor, target, new) is ok


async def _crew():
    """A group with two of each role."""
    people = {k: await h.user(k) for k in
              ("owner", "admin", "admin2", "mod", "mod2", "member", "member2")}
    g = await h.group(people["owner"], "Crew Circle")
    for k, role in (("admin", A), ("admin2", A), ("mod", MOD), ("mod2", MOD),
                    ("member", MEM), ("member2", MEM)):
        await h.add(g, people[k], role)
    return g, people


# actor → target: may they remove / ban?
REMOVE_CASES = [
    ("owner", "admin", True), ("owner", "mod", True), ("owner", "member", True),
    ("admin", "admin2", False), ("admin", "owner", False), ("admin", "mod", True), ("admin", "member", True),
    ("mod", "admin", False), ("mod", "mod2", False), ("mod", "owner", False), ("mod", "member", True),
    ("member", "member2", False), ("member", "mod", False),
]


@pytest.mark.parametrize("actor,target,ok", REMOVE_CASES)
async def test_remove_follows_the_roles_table(init_db, actor, target, ok):
    g, p = await _crew()
    if ok:
        await member_service.remove(p[actor], g.id, p[target].id)
        assert await membership_repository.get(g.id, p[target].id) is None
        assert g.member_count == 6
    else:
        with pytest.raises(PermissionDeniedError):
            await member_service.remove(p[actor], g.id, p[target].id)
        assert (await membership_repository.get(g.id, p[target].id)).status == MemberStatus.ACTIVE


@pytest.mark.parametrize("actor,target,ok", REMOVE_CASES)
async def test_ban_follows_the_roles_table(init_db, actor, target, ok):
    g, p = await _crew()
    call = member_service.ban(p[actor], g.id, p[target].id)
    if ok:
        await call
        assert (await membership_repository.get(g.id, p[target].id)).status == MemberStatus.BANNED
    else:
        with pytest.raises(PermissionDeniedError):
            await call


async def test_moderator_cannot_remove_an_admin(init_db):
    g, p = await _crew()
    with pytest.raises(PermissionDeniedError) as e:
        await member_service.remove(p["mod"], g.id, p["admin"].id)
    assert e.value.code == "role_too_low"


@pytest.mark.parametrize("actor,target,role,ok", [
    ("owner", "member", "admin", True), ("admin", "member", "admin", True),
    ("admin", "member", "moderator", True), ("admin", "mod", "member", True),
    ("admin", "admin2", "member", False), ("mod", "member", "moderator", False),
    ("member", "member2", "moderator", False), ("owner", "member", "owner", False),
])
async def test_change_role(init_db, actor, target, role, ok):
    g, p = await _crew()
    if ok:
        out = await member_service.set_role(p[actor], g.id, p[target].id, role)
        assert out.role.value == role
    else:
        with pytest.raises(PermissionDeniedError):
            await member_service.set_role(p[actor], g.id, p[target].id, role)


@pytest.mark.parametrize("who,ok", [("owner", True), ("admin", True), ("mod", True), ("member", False)])
async def test_announce_and_approve(init_db, who, ok):
    g, p = await _crew()
    g.join_rule = JoinRule.APPROVAL
    asker = await h.user("Asker")
    await join_service.join_code(asker, g.invite_code)
    if ok:
        await member_service.announce(p[who], g.id, "Jumu'ah khatam tonight")
        await join_service.approve(p[who], g.id, asker.id)
    else:
        with pytest.raises(PermissionDeniedError):
            await member_service.announce(p[who], g.id, "hi")
        with pytest.raises(PermissionDeniedError):
            await join_service.approve(p[who], g.id, asker.id)


@pytest.mark.parametrize("who,ok", [("owner", True), ("admin", True), ("mod", False), ("member", False)])
async def test_edit_and_reset_code(init_db, who, ok):
    g, p = await _crew()
    if ok:
        await group_service.update_group(g.id, GroupUpdate(weekly_goal=99), p[who])
        await join_service.reset_code(p[who], g.id)
    else:
        with pytest.raises(PermissionDeniedError):
            await group_service.update_group(g.id, GroupUpdate(weekly_goal=99), p[who])
        with pytest.raises(PermissionDeniedError):
            await join_service.reset_code(p[who], g.id)


@pytest.mark.parametrize("who", ["admin", "mod", "member"])
async def test_only_the_owner_transfers_deletes_and_reads_audit(init_db, who):
    g, p = await _crew()
    for call in (member_service.transfer(p[who], g.id, p["member2"].id),
                 group_service.delete_group(g.id, p[who], "Crew Circle"),
                 member_service.audit_log(p[who], g.id, None, 50)):
        with pytest.raises(PermissionDeniedError):
            await call


# ── Leave / transfer ────────────────────────────────────────────────────────

async def test_owner_must_transfer_before_leaving(init_db):
    g, p = await _crew()
    with pytest.raises(ConflictError) as e:
        await member_service.leave(p["owner"], g.id)
    assert e.value.code == "owner_must_transfer"
    await member_service.transfer(p["owner"], g.id, p["member"].id)
    assert g.owner_id == p["member"].id
    assert (await membership_repository.get(g.id, p["owner"].id)).role == A
    await member_service.leave(p["owner"], g.id)
    assert (await membership_repository.get(g.id, p["member"].id)).role == O


async def test_unban_lets_them_join_again(init_db):
    g, p = await _crew()
    await member_service.ban(p["admin"], g.id, p["member"].id)
    banned, _ = await member_service.members(p["admin"], g.id, None, "banned", 0, 50)
    assert [m.user.display_name for m in banned] == ["member"]
    with pytest.raises(PermissionDeniedError):
        await member_service.members(p["member2"], g.id, None, "banned", 0, 50)
    await member_service.unban(p["admin"], g.id, p["member"].id)
    assert (await join_service.join_code(p["member"], g.invite_code)).status == "joined"


async def test_members_list_order_and_search(init_db):
    g, p = await _crew()
    items, nxt = await member_service.members(p["member"], g.id, None, "active", 0, 3)
    assert [m.role for m in items] == [O, A, A] and nxt == 3
    items, _ = await member_service.members(p["member"], g.id, "mod", "active", 0, 50)
    assert [m.user.display_name for m in items] == ["mod", "mod2"]


# ── Topics, pushes, activity, goal ──────────────────────────────────────────

async def test_topics_follow_joins_leaves_bans_and_preferences(init_db):
    push_service.outbox.clear()
    owner, a = await h.user("Owner"), await h.user("Ali")
    g1 = await h.group(owner, "First Circle")
    await device_service.register(a, "tok-ali-0000000000000000")
    await join_service.join_code(a, g1.invite_code)
    topic = f"group_{g1.id}"
    assert h.outbox("subscribe", topic)[-1]["tokens"] == ["tok-ali-0000000000000000"]

    g2 = await h.group(owner, "Second Circle")
    await join_service.join_code(a, g2.invite_code)
    await device_service.register(a, "tok-ali-1111111111111111")     # a second phone
    subs = {m["topic"] for m in h.outbox("subscribe") if "tok-ali-1111111111111111" in m["tokens"]}
    assert subs == {topic, f"group_{g2.id}"}

    await member_service.leave(a, g1.id)
    assert h.outbox("unsubscribe", topic)
    await member_service.ban(owner, g2.id, a.id)
    assert h.outbox("unsubscribe", f"group_{g2.id}")

    # A phone that changes hands stops getting the old user's groups.
    g3 = await h.group(owner, "Third Circle")
    await join_service.join_code(a, g3.invite_code)
    b = await h.user("Bilal")
    push_service.outbox.clear()
    await device_service.register(b, "tok-ali-0000000000000000")
    assert h.outbox("unsubscribe", f"group_{g3.id}")[0]["tokens"] == ["tok-ali-0000000000000000"]


async def test_goal_reached_pushes_once_per_week(init_db):
    push_service.outbox.clear()
    rec = await h.recitation()
    owner, a = await h.user("Owner"), await h.user("Ali")
    g = await h.group(owner, weekly_goal=10)
    await h.add(g, a)
    topic = f"group_{g.id}"
    goal_pushes = lambda: [m for m in h.outbox("topic", topic) if m["data"].get("type") == "group.goal_reached"]
    await h.log(owner, rec, 6)
    assert goal_pushes() == []
    await h.log(a, rec, 5)                       # 11 ≥ 10
    assert len(goal_pushes()) == 1
    await h.log(a, rec, 3)
    await h.log(owner, rec, 30)
    assert len(goal_pushes()) == 1               # not on every later log
    week = await weekly_service.current()
    detail = await group_service.detail(a, g.id)
    assert detail.goal_reached
    await weekly_service.close_week(owner.id)
    await h.log(a, rec, 10)                      # a new week: may push again
    assert len(goal_pushes()) == 2
    last = await group_week_repository.latest_closed(g.id)
    assert last.week_id == week.id and last.total == 44 and last.goal_reached_at is not None
    assert [t["total"] for t in last.top] == [36, 8]


async def test_activity_and_week_winners(init_db):
    rec = await h.recitation()
    owner, a = await h.user("Owner"), await h.user("Ali")
    g = await h.group(owner, weekly_goal=5)
    await join_service.join_code(a, g.invite_code)
    await h.log(a, rec, 7)
    await member_service.announce(owner, g.id, "Khatam on Thursday")
    await weekly_service.close_week(owner.id)
    items, _ = await member_service.activity(a, g.id, None, 50)
    assert [i.type for i in items] == ["week.winners", "group.announcement",
                                        "group.goal_reached", "group.join"]
    assert items[0].payload["top"][0]["total"] == 7
    latest = await member_service.latest_announcement(a, g.id)
    assert latest.text == "Khatam on Thursday" and latest.author.display_name == "Owner"
    detail = await group_service.detail(a, g.id)
    assert detail.last_week.top[0].user.display_name == "Ali"
    assert detail.announcement.text == "Khatam on Thursday"


async def test_one_pinned_announcement(init_db):
    owner = await h.user("Owner")
    g = await h.group(owner)
    await member_service.announce(owner, g.id, "first")
    await member_service.announce(owner, g.id, "second")
    assert (await member_service.latest_announcement(owner, g.id)).text == "second"


async def test_audit_log_records_who_did_what(init_db):
    g, p = await _crew()
    await member_service.remove(p["admin"], g.id, p["member"].id)
    await member_service.ban(p["mod"], g.id, p["member2"].id)
    await member_service.set_role(p["owner"], g.id, p["mod2"].id, "admin")
    log, _ = await member_service.audit_log(p["owner"], g.id, None, 50)
    assert [(x.action, x.actor.display_name, x.target.display_name) for x in log] == [
        ("member.role", "owner", "mod2"), ("member.ban", "mod", "member2"),
        ("member.remove", "admin", "member")]


async def test_global_circle_is_not_managed(init_db):
    from app.services.community_service import community_service
    a, b = await h.user("Ali"), await h.user("Bilal")
    glob = await community_service.get_or_create_global_group()
    await community_service.ensure_member(a, glob)
    await community_service.ensure_member(b, glob)
    for call in (member_service.leave(a, glob.id), member_service.remove(a, glob.id, b.id)):
        with pytest.raises(AppError):
            await call


# ── HTTP ────────────────────────────────────────────────────────────────────

async def test_http_members_devices_and_preferences(clean_db):
    from app.main import app
    push_service.outbox.clear()
    async with session_scope():
        g, p = await _crew()
        gid, owner_id, mod_id, admin_id = g.id, p["owner"].id, p["mod"].id, p["admin"].id
    tok = lambda uid: {"Authorization": f"Bearer {create_access_token(str(uid))}"}
    async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app), base_url="http://t") as c:
        r = await c.delete(f"/api/v1/groups/{gid}/members/{admin_id}", headers=tok(mod_id))
        assert r.status_code == 403 and r.json()["error"]["code"] == "role_too_low"
        r = await c.patch(f"/api/v1/groups/{gid}/members/{mod_id}", headers=tok(owner_id),
                          json={"role": "owner"})
        assert r.status_code == 422                                   # use /transfer
        r = await c.get(f"/api/v1/groups/{gid}/members?limit=2", headers=tok(mod_id))
        assert r.headers["X-Next-Cursor"] == "2"
        r = await c.post(f"/api/v1/groups/{gid}/announcements", headers=tok(mod_id), json={"text": "  "})
        assert r.status_code == 422
        r = await c.post(f"/api/v1/groups/{gid}/announcements", headers=tok(mod_id), json={"text": "Salam"})
        assert r.status_code == 201
        r = await c.get(f"/api/v1/groups/{gid}/activity", headers=tok(admin_id))
        assert r.json()[0]["type"] == "group.announcement"
        r = await c.get(f"/api/v1/groups/{gid}/audit", headers=tok(owner_id))
        assert r.json()[0]["action"] == "announcement.post"

        r = await c.post("/api/v1/users/me/devices", headers=tok(mod_id),
                         json={"token": "fcm-token-of-the-moderator-phone"})
        assert r.status_code == 200
        assert h.outbox("subscribe", f"group_{gid}")[-1]["tokens"] == ["fcm-token-of-the-moderator-phone"]
        r = await c.patch("/api/v1/users/me", headers=tok(mod_id),
                          json={"preferences": {"notify": {"groups": False}}})
        assert r.status_code == 200
        assert h.outbox("unsubscribe", f"group_{gid}")[-1]["tokens"] == ["fcm-token-of-the-moderator-phone"]
        r = await c.delete("/api/v1/users/me/devices/fcm-token-of-the-moderator-phone", headers=tok(mod_id))
        assert r.status_code == 200
