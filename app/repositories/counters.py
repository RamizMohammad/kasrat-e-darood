"""Atomic counter updates for submission roll-ups.

Totals change with one SQL UPDATE (``SET n = n + :delta``) instead of
load → add → save in Python, so two people logging at the same moment (or one
person on two phones) both land and nothing is overwritten by a stale copy.
"""
from __future__ import annotations

import uuid

from sqlalchemy import func, inspect, update
from sqlalchemy.orm.attributes import set_committed_value

from app.database.postgres import db
from app.models.base import utcnow
from app.models.submission import Submission
from app.models.user import User
from app.models.weekly_session import WeeklySession


def _sync(model, row_id: uuid.UUID, **values) -> None:
    """Show the new value on an already-loaded object WITHOUT marking it dirty.

    Assigning it normally would make the ORM flush `SET n = <value>` later and
    undo a concurrent increment; set_committed_value only updates memory.
    """
    key = inspect(model).identity_key_from_primary_key((row_id,))
    obj = db().sync_session.identity_map.get(key)
    if obj is not None:
        for name, value in values.items():
            set_committed_value(obj, name, value)


async def inc_user_lifetime(user_id: uuid.UUID, delta: int) -> int:
    """Add ``delta`` to a user's lifetime total; return the new total (never < 0)."""
    stmt = (update(User).where(User.id == user_id)
            .values(lifetime_total=func.greatest(User.lifetime_total + delta, 0),
                    updated_at=utcnow())
            .returning(User.lifetime_total)
            .execution_options(synchronize_session=False))
    total = int((await db().execute(stmt)).scalar() or 0)
    _sync(User, user_id, lifetime_total=total)
    return total


async def inc_week(week_id: uuid.UUID, user_id: uuid.UUID, delta: int) -> int:
    """Add ``delta`` to a week's live group total; return the new total.

    Per-user weekly totals are summed from submissions, so only the group
    total is stored live.
    """
    stmt = (update(WeeklySession).where(WeeklySession.id == week_id)
            .values(group_total=func.greatest(WeeklySession.group_total + delta, 0),
                    updated_at=utcnow())
            .returning(WeeklySession.group_total)
            .execution_options(synchronize_session=False))
    total = int((await db().execute(stmt)).scalar() or 0)
    _sync(WeeklySession, week_id, group_total=total)
    return total


async def claim_undo(submission_id: uuid.UUID) -> bool:
    """Soft-delete a submission exactly once.

    Returns False when it was already deleted (e.g. a double-tapped undo), so
    the caller doesn't subtract the count twice. ``client_uuid`` is cleared so
    the unique (user_id, client_uuid) index doesn't block a later log reusing it.
    """
    stmt = (update(Submission)
            .where(Submission.id == submission_id, Submission.deleted.is_(False))
            .values(deleted=True, client_uuid=None, updated_at=utcnow(),
                    version=Submission.version + 1)
            .returning(Submission.id)
            .execution_options(synchronize_session=False))
    claimed = (await db().execute(stmt)).first() is not None
    if claimed:
        _sync(Submission, submission_id, deleted=True, client_uuid=None)
    return claimed
