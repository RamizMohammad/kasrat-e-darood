"""Group, membership, join request, announcement, audit and group-week repositories."""
from __future__ import annotations

import uuid
from datetime import datetime

from sqlalchemy import and_, case, func, or_, select, update
from sqlalchemy.dialects.postgresql import insert as pg_insert

from app.database.postgres import db
from app.models.base import utcnow
from app.models.group import (
    Group,
    GroupAnnouncement,
    GroupAuditLog,
    GroupWeek,
    JoinRequest,
    MemberRole,
    Membership,
    MemberStatus,
    Privacy,
    RequestStatus,
)
from app.models.user import User
from app.repositories.base import BaseRepository
from app.repositories.counters import _sync


def _like(q: str) -> str:
    return "%" + q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"


SEARCH_VECTOR = func.to_tsvector("simple", Group.name + " " + func.coalesce(Group.description, ""))


class GroupRepository(BaseRepository[Group]):
    def __init__(self) -> None:
        super().__init__(Group)

    async def get_any(self, group_id: uuid.UUID) -> Group | None:
        """Including soft-deleted groups (so callers can say "deleted")."""
        return await db().get(Group, group_id)

    async def get_by_invite_code(self, code: str) -> Group | None:
        """Live or deleted; codes are unique for ever."""
        stmt = select(Group).where(Group.invite_code == code).limit(1)
        return (await db().execute(stmt)).scalars().first()

    async def get_global(self) -> Group | None:
        return await self.first(Group.is_global.is_(True))

    async def list_by_ids(self, ids) -> list[Group]:
        ids = list(ids)
        if not ids:
            return []
        result = await db().execute(self.live().where(Group.id.in_(ids)))
        return list(result.scalars().all())

    async def count_owned(self, user_id: uuid.UUID) -> int:
        stmt = select(func.count()).select_from(Group).where(
            Group.owner_id == user_id, Group.deleted.is_(False), Group.is_global.is_(False))
        return int((await db().execute(stmt)).scalar_one())

    async def public_ids(self) -> list[uuid.UUID]:
        stmt = select(Group.id).where(Group.deleted.is_(False), Group.is_global.is_(False),
                                      Group.privacy == Privacy.PUBLIC)
        return list((await db().execute(stmt)).scalars().all())

    async def all_live_ids(self) -> list[uuid.UUID]:
        stmt = select(Group.id).where(Group.deleted.is_(False), Group.is_global.is_(False))
        return list((await db().execute(stmt)).scalars().all())

    async def discover(self, q: str | None, limit: int = 1000) -> list[Group]:
        stmt = self.live().where(Group.is_global.is_(False), Group.privacy == Privacy.PUBLIC)
        q = (q or "").strip()
        if q:
            stmt = stmt.where(or_(
                SEARCH_VECTOR.op("@@")(func.plainto_tsquery("simple", q)),
                Group.name.ilike(_like(q), escape="\\"),
                Group.description.ilike(_like(q), escape="\\"),
            ))
        stmt = stmt.order_by(Group.created_at.desc(), Group.id.desc()).limit(limit)
        return list((await db().execute(stmt)).scalars().all())

    async def take_seat(self, group_id: uuid.UUID) -> bool:
        """member_count += 1 unless the group is full. Atomic."""
        stmt = (update(Group).where(Group.id == group_id, Group.member_count < Group.member_limit)
                .values(member_count=Group.member_count + 1, updated_at=utcnow())
                .returning(Group.member_count).execution_options(synchronize_session=False))
        n = (await db().execute(stmt)).scalar()
        if n is not None:
            _sync(Group, group_id, member_count=int(n))
        return n is not None

    async def free_seat(self, group_id: uuid.UUID) -> None:
        stmt = (update(Group).where(Group.id == group_id)
                .values(member_count=func.greatest(Group.member_count - 1, 0), updated_at=utcnow())
                .returning(Group.member_count).execution_options(synchronize_session=False))
        n = (await db().execute(stmt)).scalar()
        if n is not None:
            _sync(Group, group_id, member_count=int(n))


class MembershipRepository(BaseRepository[Membership]):
    def __init__(self) -> None:
        super().__init__(Membership)

    async def get(self, group_id: uuid.UUID, user_id: uuid.UUID):  # type: ignore[override]
        """The live membership (active, banned...) of a user in a group."""
        return await self.first(Membership.group_id == group_id, Membership.user_id == user_id)

    async def list_for_user(self, user_id: uuid.UUID) -> list[Membership]:
        result = await db().execute(self.live().where(
            Membership.user_id == user_id, Membership.status == MemberStatus.ACTIVE))
        return list(result.scalars().all())

    async def active_group_ids(self, user_id: uuid.UUID) -> list[uuid.UUID]:
        stmt = (select(Membership.group_id)
                .join(Group, and_(Group.id == Membership.group_id, Group.deleted.is_(False)))
                .where(Membership.user_id == user_id, Membership.deleted.is_(False),
                       Membership.status == MemberStatus.ACTIVE, Group.is_global.is_(False)))
        return list((await db().execute(stmt)).scalars().all())

    async def list_for_group(self, group_id: uuid.UUID) -> list[Membership]:
        result = await db().execute(self.live().where(Membership.group_id == group_id)
                                    .order_by(Membership.created_at))
        return list(result.scalars().all())

    async def search(self, group_id: uuid.UUID, status: MemberStatus, q: str | None,
                     offset: int, limit: int) -> list[tuple[Membership, User]]:
        stmt = (select(Membership, User).join(User, User.id == Membership.user_id)
                .where(Membership.group_id == group_id, Membership.deleted.is_(False),
                       Membership.status == status, User.deleted.is_(False)))
        if q and q.strip():
            stmt = stmt.where(User.display_name.ilike(_like(q.strip()), escape="\\"))
        role_order = case({"owner": 0, "admin": 1, "moderator": 2, "member": 3},
                          value=Membership.role, else_=4)
        stmt = (stmt.order_by(role_order, func.lower(User.display_name), Membership.id)
                .offset(offset).limit(limit))
        return [(m, u) for m, u in (await db().execute(stmt)).all()]

    async def user_ids_with_roles(self, group_id: uuid.UUID, roles: set[MemberRole]) -> list[uuid.UUID]:
        stmt = select(Membership.user_id).where(
            Membership.group_id == group_id, Membership.deleted.is_(False),
            Membership.status == MemberStatus.ACTIVE, Membership.role.in_(list(roles)))
        return list((await db().execute(stmt)).scalars().all())

    async def active_user_ids(self, group_id: uuid.UUID) -> list[uuid.UUID]:
        stmt = select(Membership.user_id).where(
            Membership.group_id == group_id, Membership.deleted.is_(False),
            Membership.status == MemberStatus.ACTIVE)
        return list((await db().execute(stmt)).scalars().all())

    async def joins_since(self, user_id: uuid.UUID, since: datetime) -> int:
        """Memberships this user created themselves since ``since`` (incl. left ones)."""
        stmt = select(func.count()).select_from(Membership).join(
            Group, and_(Group.id == Membership.group_id, Group.is_global.is_(False))).where(
            Membership.user_id == user_id, Membership.created_by == user_id,
            Membership.created_at >= since)
        return int((await db().execute(stmt)).scalar_one())


class JoinRequestRepository(BaseRepository[JoinRequest]):
    def __init__(self) -> None:
        super().__init__(JoinRequest)

    async def pending(self, group_id: uuid.UUID, user_id: uuid.UUID) -> JoinRequest | None:
        return await self.first(JoinRequest.group_id == group_id, JoinRequest.user_id == user_id,
                                JoinRequest.status == RequestStatus.PENDING)

    async def last_declined(self, group_id: uuid.UUID, user_id: uuid.UUID) -> JoinRequest | None:
        stmt = (self.live().where(JoinRequest.group_id == group_id, JoinRequest.user_id == user_id,
                                  JoinRequest.status == RequestStatus.DECLINED)
                .order_by(JoinRequest.decided_at.desc()).limit(1))
        return (await db().execute(stmt)).scalars().first()

    async def list_pending(self, group_id: uuid.UUID, cursor: str | None, limit: int):
        return await self.list_paginated(JoinRequest.group_id == group_id,
                                         JoinRequest.status == RequestStatus.PENDING,
                                         cursor=cursor, limit=limit)

    async def count_pending(self, group_id: uuid.UUID) -> int:
        stmt = select(func.count()).select_from(JoinRequest).where(
            JoinRequest.group_id == group_id, JoinRequest.deleted.is_(False),
            JoinRequest.status == RequestStatus.PENDING)
        return int((await db().execute(stmt)).scalar_one())

    async def created_since(self, user_id: uuid.UUID, since: datetime) -> int:
        stmt = select(func.count()).select_from(JoinRequest).where(
            JoinRequest.user_id == user_id, JoinRequest.created_at >= since)
        return int((await db().execute(stmt)).scalar_one())

    async def pending_group_ids(self, user_id: uuid.UUID) -> set[uuid.UUID]:
        stmt = select(JoinRequest.group_id).where(
            JoinRequest.user_id == user_id, JoinRequest.deleted.is_(False),
            JoinRequest.status == RequestStatus.PENDING)
        return set((await db().execute(stmt)).scalars().all())


class AnnouncementRepository(BaseRepository[GroupAnnouncement]):
    def __init__(self) -> None:
        super().__init__(GroupAnnouncement)

    async def pinned(self, group_id: uuid.UUID) -> GroupAnnouncement | None:
        return await self.first(GroupAnnouncement.group_id == group_id, GroupAnnouncement.pinned.is_(True))

    async def unpin_all(self, group_id: uuid.UUID) -> None:
        await db().execute(update(GroupAnnouncement)
                           .where(GroupAnnouncement.group_id == group_id, GroupAnnouncement.pinned.is_(True))
                           .values(pinned=False, updated_at=utcnow())
                           .execution_options(synchronize_session=False))


class AuditRepository(BaseRepository[GroupAuditLog]):
    def __init__(self) -> None:
        super().__init__(GroupAuditLog)


class GroupWeekRepository(BaseRepository[GroupWeek]):
    def __init__(self) -> None:
        super().__init__(GroupWeek)

    async def ensure(self, group_id: uuid.UUID, week_id: uuid.UUID, week_number: int) -> None:
        stmt = (pg_insert(GroupWeek)
                .values(id=uuid.uuid4(), group_id=group_id, week_id=week_id, week_number=week_number)
                .on_conflict_do_nothing(index_elements=["group_id", "week_id"]))
        await db().execute(stmt)

    async def claim_goal(self, group_id: uuid.UUID, week_id: uuid.UUID, goal: int) -> bool:
        """Mark the goal reached for this group-week. True only the first time."""
        stmt = (update(GroupWeek)
                .where(GroupWeek.group_id == group_id, GroupWeek.week_id == week_id,
                       GroupWeek.goal_reached_at.is_(None))
                .values(goal_reached_at=utcnow(), goal=goal, updated_at=utcnow())
                .returning(GroupWeek.id).execution_options(synchronize_session=False))
        return (await db().execute(stmt)).first() is not None

    async def close(self, group_id: uuid.UUID, week_id: uuid.UUID, week_number: int,
                    total: int, top: list[dict], goal: int | None) -> None:
        now = utcnow()
        stmt = (pg_insert(GroupWeek)
                .values(id=uuid.uuid4(), group_id=group_id, week_id=week_id, week_number=week_number,
                        total=total, top=top, goal=goal, closed_at=now)
                .on_conflict_do_update(index_elements=["group_id", "week_id"],
                                       set_={"total": total, "top": top, "closed_at": now,
                                             "updated_at": now}))
        await db().execute(stmt)

    async def latest_closed(self, group_id: uuid.UUID) -> GroupWeek | None:
        stmt = (self.live().where(GroupWeek.group_id == group_id, GroupWeek.closed_at.is_not(None))
                .order_by(GroupWeek.week_number.desc()).limit(1))
        return (await db().execute(stmt)).scalars().first()

    async def history(self, group_id: uuid.UUID, cursor: str | None, limit: int):
        return await self.list_paginated(GroupWeek.group_id == group_id,
                                         GroupWeek.closed_at.is_not(None), cursor=cursor, limit=limit)


group_repository = GroupRepository()
membership_repository = MembershipRepository()
join_request_repository = JoinRequestRepository()
announcement_repository = AnnouncementRepository()
audit_repository = AuditRepository()
group_week_repository = GroupWeekRepository()
