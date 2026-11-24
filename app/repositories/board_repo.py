"""Leaderboard aggregations.

Logs belong to users. The global board sums everyone's logs; a group board
sums its active members' logs made since they joined (membership.created_at),
so one log moves the global board and every group board the user is in.
"""
from __future__ import annotations

import uuid
from datetime import datetime

from sqlalchemy import and_, func, literal, select

from app.database.postgres import db
from app.models.group import Membership, MemberStatus
from app.models.submission import Submission
from app.models.user import User

Row = tuple[uuid.UUID, int]          # (user_id, total), already in board order


def _member_logs_join(week_id: uuid.UUID | None):
    """ON clause: a member's live logs since joining (in one week, if given)."""
    cond = [Submission.user_id == Membership.user_id,
            Submission.deleted.is_(False),
            Submission.created_at >= Membership.created_at]
    if week_id is not None:
        cond.append(Submission.week_id == week_id)
    return and_(*cond)


def _active_members(group_id: uuid.UUID):
    return (Membership.group_id == group_id, Membership.deleted.is_(False),
            Membership.status == MemberStatus.ACTIVE)


async def global_week(week_id: uuid.UUID) -> list[Row]:
    """Everyone with a log this week; ties broken by who got there first."""
    total = func.sum(Submission.count)
    stmt = (select(Submission.user_id, total)
            .join(User, and_(User.id == Submission.user_id, User.deleted.is_(False)))
            .where(Submission.deleted.is_(False), Submission.week_id == week_id)
            .group_by(Submission.user_id)
            .order_by(total.desc(), func.min(Submission.created_at).asc()))
    return [(u, int(t)) for u, t in (await db().execute(stmt)).all()]


async def global_all_time() -> list[Row]:
    stmt = (select(User.id, User.lifetime_total)
            .where(User.deleted.is_(False), User.lifetime_total > 0)
            .order_by(User.lifetime_total.desc(), User.created_at.asc()))
    return [(u, int(t)) for u, t in (await db().execute(stmt)).all()]


async def group_board(group_id: uuid.UUID, week_id: uuid.UUID | None) -> list[Row]:
    """Every active member (0 if they haven't logged), biggest first.

    ``week_id`` None = all time since joining.
    """
    total = func.coalesce(func.sum(Submission.count), 0)
    stmt = (select(Membership.user_id, total)
            .select_from(Membership)
            .join(User, and_(User.id == Membership.user_id, User.deleted.is_(False)))
            .outerjoin(Submission, _member_logs_join(week_id))
            .where(*_active_members(group_id))
            .group_by(Membership.user_id, Membership.created_at)
            .order_by(total.desc(), func.min(Submission.created_at).asc().nulls_last(),
                      Membership.created_at.asc()))
    return [(u, int(t)) for u, t in (await db().execute(stmt)).all()]


async def group_totals(group_ids: list[uuid.UUID], week_id: uuid.UUID | None) -> dict[uuid.UUID, int]:
    """{group_id: total} for several groups at once (missing = 0)."""
    if not group_ids:
        return {}
    stmt = (select(Membership.group_id, func.coalesce(func.sum(Submission.count), 0))
            .select_from(Membership)
            .join(Submission, _member_logs_join(week_id))
            .where(Membership.group_id.in_(group_ids), Membership.deleted.is_(False),
                   Membership.status == MemberStatus.ACTIVE)
            .group_by(Membership.group_id))
    out = {g: 0 for g in group_ids}
    out.update({g: int(t) for g, t in (await db().execute(stmt)).all()})
    return out


async def member_total(group_id: uuid.UUID, user_id: uuid.UUID, week_id: uuid.UUID | None) -> int:
    stmt = (select(func.coalesce(func.sum(Submission.count), 0))
            .select_from(Membership)
            .join(Submission, _member_logs_join(week_id))
            .where(*_active_members(group_id), Membership.user_id == user_id))
    return int((await db().execute(stmt)).scalar_one())


async def week_total(week_id: uuid.UUID) -> int:
    stmt = select(func.coalesce(func.sum(Submission.count), literal(0))).where(
        Submission.deleted.is_(False), Submission.week_id == week_id)
    return int((await db().execute(stmt)).scalar_one())


async def active_since(since: datetime, group_ids: list[uuid.UUID]) -> dict[uuid.UUID, int]:
    """Logs by members since ``since``, per group (Discover's "active" sort)."""
    if not group_ids:
        return {}
    stmt = (select(Membership.group_id, func.sum(Submission.count))
            .select_from(Membership)
            .join(Submission, and_(_member_logs_join(None), Submission.created_at >= since))
            .where(Membership.group_id.in_(group_ids), Membership.deleted.is_(False),
                   Membership.status == MemberStatus.ACTIVE)
            .group_by(Membership.group_id))
    return {g: int(t) for g, t in (await db().execute(stmt)).all()}
