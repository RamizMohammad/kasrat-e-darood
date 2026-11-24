"""Running a group: members, roles, leave/transfer, announcements, activity,
goal-reached pushes and the audit log. Every change checks the roles table
(security/permissions.py)."""
from __future__ import annotations

import uuid

from loguru import logger

from app.core.exceptions import ConflictError, NotFoundError, PermissionDeniedError, ValidationAppError
from app.database.redis import cache
from app.models.activity import ActivityItem
from app.models.group import (
    Group,
    GroupAnnouncement,
    GroupAuditLog,
    MemberRole,
    Membership,
    MemberStatus,
)
from app.models.user import User
from app.models.weekly_session import WeeklySession
from app.repositories import board_repo
from app.repositories.activity_repo import activity_repository
from app.repositories.group_repo import (
    announcement_repository,
    audit_repository,
    group_repository,
    group_week_repository,
    join_request_repository,
    membership_repository,
)
from app.repositories.user_repo import user_repository
from app.schemas.group import (
    AnnouncementOut,
    AuditOut,
    GroupActivityOut,
    MemberOut,
)
from app.security.permissions import Permission, can_act_on, can_assign
from app.services import push_service
from app.services.device_service import device_service
from app.services.group_service import load_group, mini, require_member
from app.services.weekly_service import weekly_service

ACTIVITY_TYPES = {"group.join", "group.goal_reached", "week.winners", "group.announcement"}


async def audit(group: Group, actor: User, action: str, target_id: uuid.UUID | None, details: dict) -> None:
    await audit_repository.create(GroupAuditLog(
        group_id=group.id, actor_id=actor.id, action=action, target_id=target_id,
        details=details, created_by=actor.id))


class MemberService:
    # ── Members ──────────────────────────────────────────────────────────

    async def members(self, user: User, group_id: uuid.UUID, q: str | None, status: str,
                      offset: int, limit: int):
        group = await load_group(group_id)
        await require_member(group, user, Permission.MEMBER_MANAGE if status == "banned" else Permission.VIEW)
        st = MemberStatus.BANNED if status == "banned" else MemberStatus.ACTIVE
        rows = await membership_repository.search(group.id, st, q, offset, limit + 1)
        week = await weekly_service.current()
        totals = dict(await board_repo.group_board(group.id, week.id)) if st == MemberStatus.ACTIVE else {}
        items = [MemberOut(user=mini(u), role=MemberRole(m.role), status=m.status.value,
                           joined_at=m.created_at, week_total=totals.get(u.id, 0))
                 for m, u in rows[:limit]]
        return items, (offset + limit if len(rows) > limit else None)

    async def _target(self, group: Group, target_id: uuid.UUID, *, include_banned: bool = False) -> Membership:
        t = await membership_repository.get(group.id, target_id)
        if t is None or (t.status != MemberStatus.ACTIVE and not
                         (include_banned and t.status == MemberStatus.BANNED)):
            raise NotFoundError("That person isn't in this group", code="not_member")
        return t

    async def set_role(self, actor: User, group_id: uuid.UUID, target_id: uuid.UUID, role: str) -> MemberOut:
        group = await load_group(group_id)
        me = await require_member(group, actor, Permission.ROLE_ASSIGN)
        if target_id == actor.id:
            raise PermissionDeniedError("You can't change your own role", code="self")
        t = await self._target(group, target_id)
        new_role = MemberRole(role)
        if not can_assign(MemberRole(me.role), MemberRole(t.role), new_role):
            raise PermissionDeniedError("Your role can't make that change", code="role_too_low")
        old = MemberRole(t.role)
        t.role = new_role
        await membership_repository.save(t)
        await audit(group, actor, "member.role", target_id, {"from": old.value, "to": new_role.value})
        if new_role != old:
            await device_service.notify([target_id], "group.role", group.name,
                                        f"You're now {new_role.value} of {group.name}.",
                                        {"group_id": str(group.id), "role": new_role.value})
        u = await user_repository.get(target_id)
        return MemberOut(user=mini(u), role=new_role, status=t.status.value, joined_at=t.created_at)

    async def remove(self, actor: User, group_id: uuid.UUID, target_id: uuid.UUID) -> None:
        group, t = await self._managed_target(actor, group_id, target_id)
        await membership_repository.soft_delete(t)
        await self._after_leave(group, target_id)
        await audit(group, actor, "member.remove", target_id, {})

    async def ban(self, actor: User, group_id: uuid.UUID, target_id: uuid.UUID) -> None:
        group, t = await self._managed_target(actor, group_id, target_id)
        t.status = MemberStatus.BANNED            # the row stays: it blocks re-joining
        await membership_repository.save(t)
        req = await join_request_repository.pending(group.id, target_id)
        if req is not None:
            await join_request_repository.soft_delete(req)
        await self._after_leave(group, target_id)
        await audit(group, actor, "member.ban", target_id, {})

    async def unban(self, actor: User, group_id: uuid.UUID, target_id: uuid.UUID) -> None:
        group = await load_group(group_id)
        me = await require_member(group, actor, Permission.MEMBER_MANAGE)
        t = await membership_repository.get(group.id, target_id)
        if t is None or t.status != MemberStatus.BANNED:
            raise NotFoundError("That person isn't banned", code="not_banned")
        if not can_act_on(MemberRole(me.role), MemberRole(t.role)):
            raise PermissionDeniedError("Your role can't make that change", code="role_too_low")
        await membership_repository.soft_delete(t)   # they may join again
        await audit(group, actor, "member.unban", target_id, {})

    async def _managed_target(self, actor: User, group_id: uuid.UUID, target_id: uuid.UUID):
        group = await load_group(group_id)
        if group.is_global:
            raise PermissionDeniedError("Nobody can be removed from the global circle", code="global_group")
        me = await require_member(group, actor, Permission.MEMBER_MANAGE)
        if target_id == actor.id:
            raise PermissionDeniedError("Use leave instead", code="self")
        t = await self._target(group, target_id)
        if not can_act_on(MemberRole(me.role), MemberRole(t.role)):
            raise PermissionDeniedError("Your role can't make that change", code="role_too_low")
        return group, t

    async def _after_leave(self, group: Group, user_id: uuid.UUID) -> None:
        await group_repository.free_seat(group.id)
        await device_service.unsubscribe_user(user_id, group.id)
        await cache.delete_prefix(f"board:{group.id}")
        await cache.delete_prefix("board:groups")

    async def leave(self, user: User, group_id: uuid.UUID) -> None:
        group = await load_group(group_id)
        if group.is_global:
            raise PermissionDeniedError("Everyone stays in the global circle", code="global_group")
        m = await require_member(group, user)
        if MemberRole(m.role) == MemberRole.OWNER:
            raise ConflictError("Transfer ownership before leaving", code="owner_must_transfer")
        await membership_repository.soft_delete(m)
        await self._after_leave(group, user.id)

    async def transfer(self, actor: User, group_id: uuid.UUID, target_id: uuid.UUID) -> None:
        group = await load_group(group_id)
        me = await require_member(group, actor, Permission.GROUP_DELETE)
        if target_id == actor.id:
            raise ValidationAppError("You already own this group", code="self")
        t = await self._target(group, target_id)
        t.role = MemberRole.OWNER
        me.role = MemberRole.ADMIN
        group.owner_id = target_id
        await membership_repository.save(t)
        await membership_repository.save(me)
        await group_repository.save(group)
        await audit(group, actor, "owner.transfer", target_id, {})
        await device_service.notify([target_id], "group.role", group.name,
                                    f"You're now the owner of {group.name}.",
                                    {"group_id": str(group.id), "role": "owner"})

    # ── Announcements ────────────────────────────────────────────────────

    async def announce(self, actor: User, group_id: uuid.UUID, text: str) -> AnnouncementOut:
        group = await load_group(group_id)
        await require_member(group, actor, Permission.ANNOUNCE)
        await announcement_repository.unpin_all(group.id)       # one pinned at a time
        a = await announcement_repository.create(GroupAnnouncement(
            group_id=group.id, author_id=actor.id, text=text, pinned=True, created_by=actor.id))
        await activity_repository.create(ActivityItem(
            group_id=group.id, actor_id=actor.id, type="group.announcement", text=text,
            payload={"announcement_id": str(a.id)}, created_by=actor.id))
        await audit(group, actor, "announcement.post", None, {"announcement_id": str(a.id)})
        await push_service.send_to_topic(push_service.group_topic(group.id), group.name, text,
                                         {"type": "group.announcement", "group_id": str(group.id)})
        return AnnouncementOut(id=a.id, text=a.text, author=mini(actor), created_at=a.created_at)

    async def latest_announcement(self, user: User, group_id: uuid.UUID) -> AnnouncementOut | None:
        group = await load_group(group_id)
        await require_member(group, user)
        a = await announcement_repository.pinned(group.id)
        if a is None:
            return None
        return AnnouncementOut(id=a.id, text=a.text, author=mini(await user_repository.get(a.author_id)),
                               created_at=a.created_at)

    # ── Activity + audit ─────────────────────────────────────────────────

    async def activity(self, user: User, group_id: uuid.UUID, cursor: str | None, limit: int):
        group = await load_group(group_id)
        await require_member(group, user)
        rows, next_cursor = await activity_repository.list_paginated(
            ActivityItem.group_id == group.id, ActivityItem.type.in_(ACTIVITY_TYPES),
            cursor=cursor, limit=limit)
        people = await user_repository.get_many({r.actor_id for r in rows})
        items = [GroupActivityOut(id=r.id, type=r.type, text=r.text, actor=mini(people.get(r.actor_id)),
                                  payload=r.payload, created_at=r.created_at) for r in rows]
        return items, next_cursor

    async def audit_log(self, user: User, group_id: uuid.UUID, cursor: str | None, limit: int):
        group = await load_group(group_id)
        await require_member(group, user, Permission.AUDIT_VIEW)
        rows, next_cursor = await audit_repository.list_paginated(
            GroupAuditLog.group_id == group.id, cursor=cursor, limit=limit)
        people = await user_repository.get_many({r.actor_id for r in rows} | {r.target_id for r in rows})
        items = [AuditOut(id=r.id, action=r.action, actor=mini(people.get(r.actor_id)),
                          target=mini(people.get(r.target_id)) if r.target_id else None,
                          details=r.details, created_at=r.created_at) for r in rows]
        return items, next_cursor

    # ── Goal reached (called after every log) ────────────────────────────

    async def check_goals(self, user: User, week: WeeklySession) -> None:
        """For each group of this user with a weekly goal: if this week's total
        has reached it, push once. The claim on group_weeks.goal_reached_at
        makes it once per group per week, however many logs follow."""
        group_ids = await membership_repository.active_group_ids(user.id)
        groups = [g for g in await group_repository.list_by_ids(group_ids) if g.weekly_goal]
        if not groups:
            return
        totals = await board_repo.group_totals([g.id for g in groups], week.id)
        for g in groups:
            total = totals.get(g.id, 0)
            if total < g.weekly_goal:
                continue
            await group_week_repository.ensure(g.id, week.id, week.week_number)
            if not await group_week_repository.claim_goal(g.id, week.id, g.weekly_goal):
                continue
            text = f"{g.name} reached this week's goal of {g.weekly_goal:,}!"
            await activity_repository.create(ActivityItem(
                group_id=g.id, actor_id=user.id, type="group.goal_reached", text=text,
                payload={"goal": g.weekly_goal, "total": total, "week_number": week.week_number}))
            try:
                await push_service.send_to_topic(push_service.group_topic(g.id), "Goal reached", text,
                                                 {"type": "group.goal_reached", "group_id": str(g.id)})
            except Exception:  # pragma: no cover
                logger.exception("goal push failed")


member_service = MemberService()
