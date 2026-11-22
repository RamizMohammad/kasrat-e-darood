"""Atomic counter updates for submission roll-ups.

Totals are changed with MongoDB ``$inc`` on the server instead of
load → add → save in Python. Two people logging at the same moment (or one
person on two phones) both land; nothing is overwritten by a stale copy.
"""
from __future__ import annotations

from beanie import PydanticObjectId
from pymongo import ReturnDocument

from app.models.base import utcnow
from app.models.submission import Submission
from app.models.user import User
from app.models.weekly_session import WeeklySession


async def inc_user_lifetime(user_id: PydanticObjectId, delta: int) -> int:
    """Add ``delta`` to a user's lifetime total; return the new total (never < 0)."""
    coll = User.get_motor_collection()
    doc = await coll.find_one_and_update(
        {"_id": user_id},
        {"$inc": {"lifetime_total": delta}, "$set": {"updated_at": utcnow()}},
        return_document=ReturnDocument.AFTER,
        projection={"lifetime_total": 1},
    )
    total = int(doc["lifetime_total"]) if doc else 0
    if total < 0:  # only possible if old data was already inconsistent
        await coll.update_one({"_id": user_id, "lifetime_total": {"$lt": 0}},
                              {"$set": {"lifetime_total": 0}})
        total = 0
    return total


async def inc_week(week_id: PydanticObjectId, user_id: PydanticObjectId,
                   delta: int) -> None:
    """Add ``delta`` to a week's group total and to the user's tally in it."""
    key = f"totals.{user_id}"
    coll = WeeklySession.get_motor_collection()
    await coll.update_one(
        {"_id": week_id},
        {"$inc": {"group_total": delta, key: delta}, "$set": {"updated_at": utcnow()}},
    )
    if delta < 0:
        await coll.update_one({"_id": week_id, "group_total": {"$lt": 0}},
                              {"$set": {"group_total": 0}})
        await coll.update_one({"_id": week_id, key: {"$lt": 0}},
                              {"$set": {key: 0}})


async def claim_undo(submission_id: PydanticObjectId) -> bool:
    """Soft-delete a submission exactly once.

    Returns False when it was already deleted (e.g. a double-tapped undo),
    so the caller does not subtract the count twice. ``client_uuid`` is
    cleared so the unique (user_id, client_uuid) index doesn't block a later
    log that reuses it.
    """
    result = await Submission.get_motor_collection().update_one(
        {"_id": submission_id, "deleted": False},
        {"$set": {"deleted": True, "client_uuid": None, "updated_at": utcnow()},
         "$inc": {"version": 1}},
    )
    return result.modified_count == 1
