"""Every way into a group: invite code, link, QR, Discover, and requests.

All of them end in ``_join``, which applies the same checks: deleted,
banned, already a member, full, 20 joins an hour, and the join rule
(``approval`` → a request an admin or moderator decides).
"""
from __future__ import annotations

import uuid
from datetime import timedelta

from app.config.settings import settings
from app.core import clock
from app.core.exceptions import (
    ConflictError,
    GoneError,
    NotFoundError,
    PermissionDeniedError,
    TooManyError,
)
from app.database.redis import cache
from app.models.activity import ActivityItem
from app.models.group import (
    Group,
    JoinRequest,
    JoinRule,
    MemberRole,
    Membership,
    MemberStatus,
    Privacy,
    RequestStatus,
)
from app.models.user import User
from app.repositories import board_repo
from app.repositories.activity_repo import activity_repository
from app.repositories.group_repo import (
    group_repository,
    join_request_repository,
    membership_repository,
)
from app.repositories.user_repo import user_repository
from app.schemas.group import (
    DecideResult,
    GroupPreview,
    InviteOut,
    JoinRequestOut,
    JoinResult,
)
from app.security.permissions import Permission
from app.services.device_service import device_service
from app.services.group_service import (
    card,
    group_service,
    invite_link,
    load_group,
    mini,
    normalize_code,
    require_member,
    share_text,
)
from app.services.weekly_service import weekly_service

DECIDERS = {MemberRole.OWNER, MemberRole.ADMIN, MemberRole.MODERATOR}


async def _by_code(code: str) -> Group:
    group = await group_repository.get_by_invite_code(normalize_code(code))
    if group is None or group.is_global:
        raise NotFoundError("That code doesn't match a group", code="invalid_code")
    if group.deleted:
        raise GoneError("This group was deleted", code="group_deleted")
    return group


class JoinService:
    # ── Invites ──────────────────────────────────────────────────────────

    async def invite(self, user: User, group_id: uuid.UUID) -> InviteOut:
        group = await load_group(group_id)
        await require_member(group, user, Permission.INVITE_SHARE)
        return InviteOut(code=group.invite_code, link=invite_link(group.invite_code),
                         share_text=share_text(group))

    async def reset_code(self, user: User, group_id: uuid.UUID) -> InviteOut:
        await group_service.rotate_invite_code(group_id, by=user)
        return await self.invite(user, group_id)

    async def preview_code(self, code: str, viewer: User | None) -> GroupPreview:
        """Join preview; works before sign-in."""
        return await group_service.preview(await _by_code(code), viewer)

    async def join_code(self, user: User, code: str) -> JoinResult:
        # Per-account cap on wrong codes (the IP limit alone can be dodged by
        # rotating IPs while guessing codes of private groups).
        key = f"invite-fail:{user.id}"
        if await cache.count(key) >= settings.INVITE_CODE_MAX_FAILURES_PER_HOUR:
            raise TooManyError("Too many wrong codes. Try again in an hour.")
        try:
            group = await _by_code(code)
        except NotFoundError:
            await cache.hit(key, 3600)
            raise
        return await self._join(user, group)

    async def join_public(self, user: User, group_id: uuid.UUID) -> JoinResult:
        group = await load_group(group_id)
        if group.privacy != Privacy.PUBLIC or group.is_global:
            raise NotFoundError("Group not found", code="group_not_found")
        return await self._join(user, group)

    # ── The one join path ────────────────────────────────────────────────

    async def _join(self, user: User, group: Group) -> JoinResult:
        existing = await membership_repository.get(group.id, user.id)
        if existing is not None:
            if existing.status == MemberStatus.BANNED:
                raise PermissionDeniedError("You can't join this group", code="banned")
            raise ConflictError("You're already in this group", code="already_member")
        if group.member_count >= group.member_limit:
            raise ConflictError("This group is full", code="group_full")
        await self._rate_limit(user)

        if group.join_rule == JoinRule.APPROVAL:
            await self._request(user, group)
            return JoinResult(status="requested", group=card(group, await self._week_total(group)))

        await self.add_member(group, user, by=user)
        return JoinResult(status="joined", group=card(group, await self._week_total(group)))

    async def add_member(self, group: Group, user: User, by: User) -> Membership:
        """Seat + membership + topic + activity. Raises if full or already in."""
        if not await group_repository.take_seat(group.id):
            raise ConflictError("This group is full", code="group_full")
        m = await membership_repository.create_unique(Membership(
            group_id=group.id, user_id=user.id, role=MemberRole.MEMBER,
            status=MemberStatus.ACTIVE, created_by=by.id))
        if m is None:
            await group_repository.free_seat(group.id)
            raise ConflictError("You're already in this group", code="already_member")
        await activity_repository.create(ActivityItem(
            group_id=group.id, actor_id=user.id, type="group.join",
            text=f"{user.display_name} joined", payload={"user_id": str(user.id)}, created_by=by.id))
        await device_service.subscribe_user(user.id, group.id)
        await cache.delete_prefix(f"board:{group.id}")
        await cache.delete_prefix("board:groups")
        return m

    async def _rate_limit(self, user: User) -> None:
        since = clock.now_utc() - timedelta(hours=1)
        n = (await membership_repository.joins_since(user.id, since)
             + await join_request_repository.created_since(user.id, since))
        if n >= settings.GROUP_JOINS_PER_HOUR:
            raise TooManyError("Too many joins in the last hour. Try again later.")

    async def _week_total(self, group: Group) -> int:
        week = await weekly_service.current()
        return (await board_repo.group_totals([group.id], week.id))[group.id]

    # ── Requests ─────────────────────────────────────────────────────────

    async def _request(self, user: User, group: Group) -> JoinRequest:
        pending = await join_request_repository.pending(group.id, user.id)
        if pending is not None:
            return pending                                  # asking twice is fine
        declined = await join_request_repository.last_declined(group.id, user.id)
        retry = timedelta(days=settings.GROUP_REQUEST_RETRY_DAYS)
        if declined is not None and declined.decided_at and clock.now_utc() - declined.decided_at < retry:
            raise ConflictError(
                f"Your request was declined. You can ask again {settings.GROUP_REQUEST_RETRY_DAYS} days after.",
                code="request_too_soon")
        req = await join_request_repository.create_unique(JoinRequest(
            group_id=group.id, user_id=user.id, created_by=user.id))
        if req is None:                                     # a double tap raced us
            req = await join_request_repository.pending(group.id, user.id)
            assert req is not None
            return req
        admins = await membership_repository.user_ids_with_roles(group.id, DECIDERS)
        await device_service.notify(
            admins, "group.request", group.name, f"{user.display_name} asked to join",
            {"group_id": str(group.id), "user_id": str(user.id)})
        return req

    async def requests(self, user: User, group_id: uuid.UUID, cursor: str | None, limit: int):
        group = await load_group(group_id)
        await require_member(group, user, Permission.REQUEST_DECIDE)
        rows, next_cursor = await join_request_repository.list_pending(group.id, cursor, limit)
        people = await user_repository.get_many({r.user_id for r in rows})
        return [JoinRequestOut(id=r.id, user=mini(people[r.user_id]), created_at=r.created_at)
                for r in rows if r.user_id in people], next_cursor

    async def approve(self, user: User, group_id: uuid.UUID, target_id: uuid.UUID) -> None:
        group = await load_group(group_id)
        await require_member(group, user, Permission.REQUEST_DECIDE)
        await self._approve(group, user, target_id)

    async def approve_many(self, user: User, group_id: uuid.UUID, target_ids: list[uuid.UUID]) -> DecideResult:
        group = await load_group(group_id)
        await require_member(group, user, Permission.REQUEST_DECIDE)
        result = DecideResult()
        for tid in dict.fromkeys(target_ids):
            try:
                await self._approve(group, user, tid)
                result.approved += 1
            except (NotFoundError, ConflictError, PermissionDeniedError):
                result.skipped += 1
                if group.member_count >= group.member_limit:
                    break
        return result

    async def _approve(self, group: Group, by: User, target_id: uuid.UUID) -> None:
        req = await join_request_repository.pending(group.id, target_id)
        if req is None:
            raise NotFoundError("No pending request from this person", code="no_request")
        target = await user_repository.get(target_id)
        if target is None:
            raise NotFoundError("User not found")
        existing = await membership_repository.get(group.id, target_id)
        if existing is not None and existing.status == MemberStatus.BANNED:
            raise PermissionDeniedError("This person is banned", code="banned")
        if existing is None:
            await self.add_member(group, target, by=by)
        req.status, req.decided_by, req.decided_at = RequestStatus.APPROVED, by.id, clock.now_utc()
        await join_request_repository.save(req)
        from app.services.member_service import audit
        await audit(group, by, "request.approve", target_id, {})
        await device_service.notify([target_id], "group.request.approved", group.name,
                                    f"You're in! Welcome to {group.name}.", {"group_id": str(group.id)})

    async def decline(self, user: User, group_id: uuid.UUID, target_id: uuid.UUID) -> None:
        group = await load_group(group_id)
        await require_member(group, user, Permission.REQUEST_DECIDE)
        req = await join_request_repository.pending(group.id, target_id)
        if req is None:
            raise NotFoundError("No pending request from this person", code="no_request")
        req.status, req.decided_by, req.decided_at = RequestStatus.DECLINED, user.id, clock.now_utc()
        await join_request_repository.save(req)
        from app.services.member_service import audit
        await audit(group, user, "request.decline", target_id, {})
        await device_service.notify([target_id], "group.request.declined", group.name,
                                    f"Your request to join {group.name} wasn't accepted.",
                                    {"group_id": str(group.id)})

    # ── Discover ─────────────────────────────────────────────────────────

    async def discover(self, user: User, q: str | None, sort: str, offset: int, limit: int):
        groups = await group_repository.discover(q)
        if sort == "active":
            active = await board_repo.active_since(clock.now_utc() - timedelta(days=7), [g.id for g in groups])
            groups.sort(key=lambda g: (-active.get(g.id, 0), -g.member_count, g.name.lower()))
        page = groups[offset:offset + limit]
        week = await weekly_service.current()
        totals = await board_repo.group_totals([g.id for g in page], week.id)
        mine = set(await membership_repository.active_group_ids(user.id))
        requested = await join_request_repository.pending_group_ids(user.id)
        items = []
        for g in page:
            status = "member" if g.id in mine else "requested" if g.id in requested else "none"
            items.append(GroupPreview(**card(g, totals.get(g.id, 0)).model_dump(), viewer_status=status,
                                      full=g.member_count >= g.member_limit))
        next_offset = offset + limit if offset + limit < len(groups) else None
        return items, next_offset


join_service = JoinService()
