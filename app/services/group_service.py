"""Groups: create, read, edit, delete, boards and group-vs-group ranks.

Joining lives in join_service, running a group (members, roles,
announcements, activity, audit) in member_service.
"""
from __future__ import annotations

import secrets
import uuid

from app.config.settings import settings
from app.core.exceptions import (
    AppError,
    ConflictError,
    GoneError,
    NotFoundError,
    PermissionDeniedError,
    ValidationAppError,
)
from app.database.redis import cache
from app.models.group import Group, MemberRole, Membership, MemberStatus, Privacy
from app.models.user import User
from app.repositories import board_repo
from app.repositories.group_repo import (
    announcement_repository,
    group_repository,
    group_week_repository,
    join_request_repository,
    membership_repository,
)
from app.repositories.submission_repo import submission_repository
from app.repositories.user_repo import user_repository
from app.schemas.board import BoardEntry, BoardResponse, MiniUser
from app.schemas.group import (
    AnnouncementOut,
    GroupCard,
    GroupCreate,
    GroupDetail,
    GroupPreview,
    GroupRankEntry,
    GroupRanks,
    GroupsThisWeek,
    GroupUpdate,
    LastWeek,
    MyGroup,
)
from app.security.permissions import Permission, require, role_has
from app.services import push_service
from app.services.board_service import board_user, build_board, ranked, week_info
from app.services.weekly_service import weekly_service

# Invite codes: 6 characters, no look-alikes (0/O, 1/I/L).
CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
CODE_LENGTH = 6


def new_invite_code() -> str:
    return "".join(secrets.choice(CODE_ALPHABET) for _ in range(CODE_LENGTH))


def normalize_code(code: str) -> str:
    return "".join(code.split()).upper()


def invite_link(code: str) -> str:
    return f"{settings.INVITE_LINK_BASE.rstrip('/')}/j/{code}"


def share_text(group: Group) -> str:
    return (f"Join my Kasrat-e-Darood circle {group.name} — code {group.invite_code} — "
            f"{invite_link(group.invite_code)}")


# ── Loading with the right error ────────────────────────────────────────────

async def load_group(group_id: uuid.UUID) -> Group:
    """A live group; 410 if it was deleted, 404 if it never existed."""
    group = await group_repository.get_any(group_id)
    if group is None:
        raise NotFoundError("Group not found", code="group_not_found")
    if group.deleted:
        raise GoneError("This group was deleted", code="group_deleted")
    return group


async def membership_or_none(group: Group, user: User) -> Membership | None:
    return await membership_repository.get(group.id, user.id)


async def require_member(group: Group, user: User, permission: Permission = Permission.VIEW) -> Membership:
    """The caller's active membership with ``permission``, or the right error."""
    if group.is_global:
        from app.services.community_service import community_service
        await community_service.ensure_member(user, group)
    m = await membership_repository.get(group.id, user.id)
    if m is None:
        raise PermissionDeniedError("You are not a member of this group", code="not_member")
    if m.status == MemberStatus.BANNED:
        raise PermissionDeniedError("You can't access this group", code="banned")
    if m.status != MemberStatus.ACTIVE:
        raise PermissionDeniedError("You are not a member of this group", code="not_member")
    require(MemberRole(m.role), permission)
    return m


def mini(user: User | None) -> MiniUser | None:
    return MiniUser.model_validate(user) if user is not None else None


def card(group: Group, week_total: int = 0) -> GroupCard:
    return GroupCard.model_validate(group).model_copy(update={"week_total": week_total})


async def _subscribe_user(user_id: uuid.UUID, group_id: uuid.UUID) -> None:
    from app.services.device_service import device_service
    await device_service.subscribe_user(user_id, group_id)


class GroupService:
    # ── Create / read ────────────────────────────────────────────────────

    async def create_group(self, owner: User, data: GroupCreate) -> Group:
        if await group_repository.count_owned(owner.id) >= settings.GROUPS_MAX_OWNED:
            raise ConflictError(f"You can own at most {settings.GROUPS_MAX_OWNED} groups",
                                code="too_many_groups")
        group = None
        for _ in range(8):   # 31^6 ≈ 887M codes: a collision is rare, retry if so
            candidate = Group(
                name=data.name, description=data.description, avatar=data.avatar,
                privacy=data.privacy, join_rule=data.join_rule, weekly_goal=data.weekly_goal,
                total_only=data.total_only, invite_code=new_invite_code(), owner_id=owner.id,
                member_count=1, member_limit=settings.GROUP_MEMBER_LIMIT, created_by=owner.id,
            )
            group = await group_repository.create_unique(candidate)
            if group is not None:
                break
        if group is None:  # pragma: no cover
            raise AppError("Could not make an invite code, try again", code="code_exhausted")
        await membership_repository.create(Membership(
            group_id=group.id, user_id=owner.id, role=MemberRole.OWNER,
            status=MemberStatus.ACTIVE, created_by=owner.id))
        await _subscribe_user(owner.id, group.id)
        return group

    async def get_group(self, group_id: uuid.UUID) -> Group:
        return await load_group(group_id)

    async def mine(self, user: User) -> list[MyGroup]:
        """Global first, then your groups by this week's total."""
        from app.services.community_service import community_service
        week = await weekly_service.current()
        global_group = await community_service.get_or_create_global_group()
        await community_service.ensure_member(user, global_group)

        memberships = {m.group_id: m for m in await membership_repository.list_for_user(user.id)}
        groups = [g for g in await group_repository.list_by_ids(memberships) if not g.is_global]
        totals = await board_repo.group_totals([g.id for g in groups], week.id)

        out: list[MyGroup] = []
        g_member = memberships.get(global_group.id)
        out.append(MyGroup(
            **card(global_group, week.group_total).model_dump(exclude={"member_count"}),
            member_count=await user_repository.count_all(),
            role=MemberRole.MEMBER,
            my_week_total=await submission_repository.sum_for_user_week(week.id, user.id),
            joined_at=g_member.created_at if g_member else None,
        ))
        mine = []
        for g in groups:
            m = memberships[g.id]
            mine.append(MyGroup(**card(g, totals.get(g.id, 0)).model_dump(), role=MemberRole(m.role),
                                my_week_total=await board_repo.member_total(g.id, user.id, week.id),
                                joined_at=m.created_at))
        mine.sort(key=lambda x: (-x.week_total, x.name.lower()))
        return out + mine

    async def list_my_groups(self, user: User) -> list[Group]:      # v2 helper
        ids = [m.group_id for m in await membership_repository.list_for_user(user.id)]
        return await group_repository.list_by_ids(ids)

    async def detail(self, user: User, group_id: uuid.UUID) -> GroupDetail | GroupPreview:
        """Members get everything; outsiders get a preview of public groups."""
        group = await load_group(group_id)
        m = await membership_or_none(group, user)
        if group.is_global or (m is not None and m.status == MemberStatus.ACTIVE):
            return await self._detail(user, group, await require_member(group, user))
        if m is not None and m.status == MemberStatus.BANNED:
            raise PermissionDeniedError("You can't join this group", code="banned")
        if group.privacy != Privacy.PUBLIC:
            raise NotFoundError("Group not found", code="group_not_found")
        return await self.preview(group, user)

    async def _detail(self, user: User, group: Group, m: Membership) -> GroupDetail:
        week = await weekly_service.current()
        total = week.group_total if group.is_global else (await board_repo.group_totals([group.id], week.id))[group.id]
        role = MemberRole(m.role)
        pinned = await announcement_repository.pinned(group.id)
        author = await user_repository.get(pinned.author_id) if pinned else None
        last = None if group.is_global else await group_week_repository.latest_closed(group.id)
        last_week = None
        hide_top = group.total_only and MemberRole(m.role) not in (MemberRole.OWNER, MemberRole.ADMIN)
        if last is not None and hide_top:
            last_week = LastWeek(week_number=last.week_number, total=last.total,
                                 goal_reached=last.goal_reached_at is not None, top=[])
        elif last is not None:
            people = await user_repository.get_many({uuid.UUID(t["user_id"]) for t in last.top})
            last_week = LastWeek(
                week_number=last.week_number, total=last.total,
                goal_reached=last.goal_reached_at is not None,
                top=[BoardEntry(rank=int(t["rank"]), total=int(t["total"]),
                                user=board_user(people.get(uuid.UUID(t["user_id"])), user.id),
                                is_me=t["user_id"] == str(user.id)) for t in last.top])
        goal_reached = False
        if not group.is_global and group.weekly_goal:
            goal_reached = total >= group.weekly_goal
        return GroupDetail(
            **card(group, total).model_dump(exclude={"member_count"}),
            member_count=(await user_repository.count_all()) if group.is_global else group.member_count,
            role=role,
            my_week_total=(await submission_repository.sum_for_user_week(week.id, user.id) if group.is_global
                           else await board_repo.member_total(group.id, user.id, week.id)),
            joined_at=m.created_at,
            owner_id=group.owner_id,
            invite_code=group.invite_code,
            invite_link=invite_link(group.invite_code),
            week=week_info(week),
            goal_reached=goal_reached,
            pending_requests=(await join_request_repository.count_pending(group.id)
                              if role_has(role, Permission.REQUEST_DECIDE) else None),
            announcement=(AnnouncementOut(id=pinned.id, text=pinned.text, author=mini(author),
                                          created_at=pinned.created_at) if pinned else None),
            last_week=last_week,
        )

    async def preview(self, group: Group, viewer: User | None) -> GroupPreview:
        week = await weekly_service.current()
        rows = await board_repo.group_board(group.id, week.id)
        total = sum(t for _, t in rows)
        top: list[BoardEntry] = []
        if not group.total_only:
            ranks = [r for r in ranked(rows) if r[1] > 0][:3]
            people = await user_repository.get_many({uid for uid, _, _ in ranks})
            vid = viewer.id if viewer else None
            top = [BoardEntry(rank=r, total=t, user=board_user(people.get(uid), vid), is_me=uid == vid)
                   for uid, t, r in ranks]
        status = "none"
        if viewer is not None:
            m = await membership_or_none(group, viewer)
            if m is not None and m.status == MemberStatus.BANNED:
                status = "banned"
            elif m is not None and m.status == MemberStatus.ACTIVE:
                status = "member"
            elif await join_request_repository.pending(group.id, viewer.id):
                status = "requested"
        return GroupPreview(**card(group, total).model_dump(), top=top, viewer_status=status,
                            full=group.member_count >= group.member_limit)

    # ── Edit / delete ────────────────────────────────────────────────────

    async def update_group(self, group_id: uuid.UUID, data: GroupUpdate, by: User) -> GroupDetail:
        group = await load_group(group_id)
        if group.is_global:
            raise PermissionDeniedError("The global circle can't be edited", code="global_group")
        m = await require_member(group, by, Permission.GROUP_MANAGE)
        patch = data.model_dump(exclude_unset=True)
        for key in ("name", "privacy", "join_rule", "total_only"):
            if key in patch and patch[key] is None:
                raise ValidationAppError(f"{key} can't be empty")
        for key, value in patch.items():
            setattr(group, key, value)
        group.updated_by = by.id
        await group_repository.save(group)
        await cache.delete_prefix(f"board:{group.id}")
        from app.services.member_service import audit
        if patch:
            await audit(group, by, "group.edit", None, {k: str(v) for k, v in patch.items()})
        return await self._detail(by, group, m)

    async def delete_group(self, group_id: uuid.UUID, by: User, confirm_name: str) -> None:
        group = await load_group(group_id)
        if group.is_global:
            raise PermissionDeniedError("The global circle can't be deleted", code="global_group")
        await require_member(group, by, Permission.GROUP_DELETE)
        if " ".join(confirm_name.split()).casefold() != group.name.casefold():
            raise ValidationAppError("Type the group's name exactly to delete it", code="name_mismatch")
        group.deleted = True
        group.updated_by = by.id
        await group_repository.save(group)
        await cache.delete_prefix(f"board:{group.id}")
        await cache.delete_prefix("board:groups")
        # Members hear about it; everyone's logs stay in their own stats.
        await push_service.send_to_topic(
            push_service.group_topic(group.id), "Group deleted",
            f"{group.name} was deleted by its owner. Your recitations stay in your own stats.",
            {"type": "group.deleted", "group_id": str(group.id)})

    async def rotate_invite_code(self, group_id: uuid.UUID, by: User | None = None) -> str:
        group = await load_group(group_id)
        if group.is_global:
            raise PermissionDeniedError("The global circle has no invite code", code="global_group")
        if by is not None:
            await require_member(group, by, Permission.GROUP_MANAGE)
        for _ in range(8):
            code = new_invite_code()
            if await group_repository.get_by_invite_code(code) is None:
                break
        group.invite_code = code
        await group_repository.save(group)
        if by is not None:
            from app.services.member_service import audit
            await audit(group, by, "invite.reset", None, {})
        return code

    # ── Boards ───────────────────────────────────────────────────────────

    async def board(self, user: User, group_id: uuid.UUID, scope: str,
                    around_me: bool, limit: int) -> BoardResponse:
        group = await load_group(group_id)
        if group.is_global:
            from app.services.board_service import board_service
            return await board_service.global_board(user, scope, around_me, limit)
        m = await require_member(group, user)
        week = await weekly_service.current()
        if scope not in ("week", "all"):
            raise ValidationAppError("scope must be week or all", code="invalid_scope")
        week_id = week.id if scope == "week" else None
        key = f"board:{group.id}:{scope}:{week.id}"
        cached = await cache.get_json(key)
        if cached is not None:
            rows = [(uuid.UUID(u), int(t)) for u, t in cached]
        else:
            rows = await board_repo.group_board(group.id, week_id)
            await cache.set_json(key, [[str(u), t] for u, t in rows],
                                 ttl=settings.GROUP_BOARD_CACHE_SECONDS)
        total = sum(t for _, t in rows)
        entries, me = await build_board(rows, user.id, around_me=around_me, limit=limit)
        hide = group.total_only and MemberRole(m.role) not in (MemberRole.OWNER, MemberRole.ADMIN)
        if hide:
            entries = []
            if me is not None:
                me = me.model_copy(update={"rank": None})
        return BoardResponse(scope=scope, week=week_info(week), total=total, entries=entries, me=me,
                             total_only=group.total_only, goal=group.weekly_goal,
                             member_count=group.member_count)

    async def ranks(self, user: User, metric: str, limit: int = 100) -> GroupRanks:
        """Public groups plus your own, by this week's total or the average
        per member (so small groups aren't buried)."""
        if metric not in ("total", "avg"):
            raise ValidationAppError("metric must be total or avg", code="invalid_metric")
        week = await weekly_service.current()
        mine = set(await membership_repository.active_group_ids(user.id))
        ids = list(set(await group_repository.public_ids()) | mine)
        groups = await group_repository.list_by_ids(ids)
        totals = await board_repo.group_totals([g.id for g in groups], week.id)
        rows = []
        for g in groups:
            t = totals.get(g.id, 0)
            avg = round(t / max(g.member_count, 1), 1)
            rows.append((g, t, avg))
        key = (lambda r: r[1]) if metric == "total" else (lambda r: r[2])
        rows.sort(key=lambda r: (-key(r), r[0].created_at))
        entries: list[GroupRankEntry] = []
        prev, rank = None, 0
        for i, (g, t, avg) in enumerate(rows, start=1):
            v = key((g, t, avg))
            if v != prev:
                rank, prev = i, v
            entries.append(GroupRankEntry(rank=rank, group=card(g, t), total=t, average=avg,
                                          is_mine=g.id in mine))
        return GroupRanks(metric=metric, week=week_info(week), entries=entries[:limit],
                          mine=[e for e in entries if e.is_mine])

    async def this_week(self, user: User) -> list[GroupsThisWeek]:
        """For the Home carousel."""
        week = await weekly_service.current()
        memberships = {m.group_id: m for m in await membership_repository.list_for_user(user.id)}
        groups = [g for g in await group_repository.list_by_ids(memberships) if not g.is_global]
        totals = await board_repo.group_totals([g.id for g in groups], week.id)
        out = [GroupsThisWeek(id=g.id, name=g.name, avatar=g.avatar, week_total=totals.get(g.id, 0),
                              weekly_goal=g.weekly_goal, member_count=g.member_count,
                              role=MemberRole(memberships[g.id].role)) for g in groups]
        out.sort(key=lambda x: (-x.week_total, x.name.lower()))
        return out

    async def list_members(self, group_id: uuid.UUID) -> list[Membership]:   # v2 helper
        return await membership_repository.list_for_group(group_id)


group_service = GroupService()
