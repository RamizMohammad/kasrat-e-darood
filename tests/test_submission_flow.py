"""Integration tests for the core submission roll-up and the Friday lock.

These exercise the real service layer against a real PostgreSQL test database.
"""
from __future__ import annotations

import pytest

from app.models.category import Recitation
from app.models.user import User
from app.schemas.group import GroupCreate
from app.schemas.submission import SubmissionCreate
from app.services.group_service import group_service
from app.services.submission_service import submission_service
from app.services.weekly_service import weekly_service
from tests.helpers import insert

pytestmark = pytest.mark.asyncio


async def _make_user(name: str = "Tester") -> User:
    user = User(firebase_uid=f"uid-{name}", display_name=name)
    await insert(user)
    return user


async def test_submission_rolls_up_totals_and_is_idempotent(init_db):
    user = await _make_user()
    group = await group_service.create_group(user, GroupCreate(name="Family"))
    rec = Recitation(arabic_name="يس", english_name="Surah Yaseen")
    await insert(rec)

    result = await submission_service.submit(user, SubmissionCreate(
        group_id=group.id, recitation_id=rec.id, count=3, client_uuid="abc",
    ))
    assert result.totals.user_week == 3
    assert result.totals.group_week == 3
    assert result.totals.user_lifetime == 3

    # Idempotency: replaying the same client_uuid must not double-count.
    again = await submission_service.submit(user, SubmissionCreate(
        group_id=group.id, recitation_id=rec.id, count=3, client_uuid="abc",
    ))
    assert again.totals.user_week == 3


async def test_friday_lock_archives_week_and_opens_next(init_db):
    user = await _make_user("Imam")
    group = await group_service.create_group(user, GroupCreate(name="Masjid"))
    rec = Recitation(arabic_name="درود", english_name="Darood")
    await insert(rec)
    await submission_service.submit(user, SubmissionCreate(
        group_id=group.id, recitation_id=rec.id, count=100,
    ))

    locked, hall, new_week = await weekly_service.close_week(user.id)
    assert locked.status.value == "ARCHIVED"
    assert locked.group_total == 100
    assert hall[0].total == 100 and hall[0].rank == 1
    assert new_week.status.value == "ACTIVE"
    assert new_week.week_number == locked.week_number + 1
