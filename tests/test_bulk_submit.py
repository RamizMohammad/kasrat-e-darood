"""Bulk submission defaults to the community group and rolls up totals."""
from __future__ import annotations

import pytest

from app.models.category import Recitation
from app.models.user import User
from app.schemas.submission import BulkItem, BulkSubmissionCreate
from app.services.community_service import community_service
from app.services.submission_service import submission_service
from tests.helpers import insert

pytestmark = pytest.mark.asyncio


async def _user(name="Ramiz") -> User:
    u = User(firebase_uid=f"uid-{name}", display_name=name, email=f"{name}@x.com")
    await insert(u)
    return u


async def _recitation(name) -> Recitation:
    r = Recitation(arabic_name=name, english_name=name, category="Zikr")
    await insert(r)
    return r


async def test_bulk_submit_defaults_to_community_group(init_db):
    user = await _user()
    r1 = await _recitation("Subhan Allah")
    r2 = await _recitation("Surah Yaseen")

    totals = await submission_service.submit_bulk(user, BulkSubmissionCreate(
        items=[
            BulkItem(recitation_id=r1.id, count=33, client_uuid="a"),
            BulkItem(recitation_id=r2.id, count=3, client_uuid="b"),
        ],
    ))

    assert totals.user_lifetime == 36
    assert totals.user_week == 36
    assert totals.group_week == 36

    # The user was auto-joined to the global community group.
    group = await community_service.get_or_create_global_group()
    gid = await community_service.resolve_group(user, None)
    assert gid == group.id
