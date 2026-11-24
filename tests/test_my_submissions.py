"""The /submissions/me read-model returns enriched, recent-first submissions."""
from __future__ import annotations

import pytest

from app.models.category import Recitation
from app.models.user import User
from app.schemas.submission import BulkItem, BulkSubmissionCreate
from app.services.submission_service import submission_service
from tests.helpers import insert

pytestmark = pytest.mark.asyncio


async def _user() -> User:
    u = User(firebase_uid="uid-prof", display_name="Ramiz", email="r@x.com")
    await insert(u)
    return u


async def test_recent_for_user_enriches_names(init_db):
    user = await _user()
    yaseen = Recitation(arabic_name="يس", english_name="Surah Ya-Sin",
                        urdu_name="سورۃ یٰسین", category="Surah")
    subhan = Recitation(arabic_name="سبحان اللہ", english_name="Subhan Allah",
                        urdu_name="سبحان اللہ", category="Zikr")
    await insert(yaseen)
    await insert(subhan)

    await submission_service.submit_bulk(user, BulkSubmissionCreate(items=[
        BulkItem(recitation_id=yaseen.id, count=3, client_uuid="a"),
        BulkItem(recitation_id=subhan.id, count=33, client_uuid="b"),
    ]))

    mine = await submission_service.recent_for_user(user, limit=10)
    assert len(mine) == 2
    # Most-recent first.
    assert mine[0].recitation_name == "Subhan Allah"
    assert mine[0].count == 33
    assert mine[0].urdu_name == "سبحان اللہ"
    assert {m.recitation_name for m in mine} == {"Surah Ya-Sin", "Subhan Allah"}
