"""Community stats breakdown + role-gated manual week close."""
from __future__ import annotations

import pytest

from app.core.exceptions import PermissionDeniedError
from app.models.category import Recitation
from app.models.user import GlobalRole, User
from app.models.weekly_session import WeeklySession
from app.schemas.submission import BulkItem, BulkSubmissionCreate
from app.services.community_service import community_service
from app.services.stats_service import can_manage_weeks, stats_service
from app.services.submission_service import submission_service
from app.services.weekly_service import weekly_service

pytestmark = pytest.mark.asyncio


async def _user(name="Ramiz", role=GlobalRole.MEMBER) -> User:
    u = User(firebase_uid=f"uid-{name}", display_name=name,
             email=f"{name}@x.com", role=role)
    await u.insert()
    return u


async def _seed_submissions(user):
    yaseen = Recitation(arabic_name="يس", english_name="Surah Ya-Sin",
                        category="Surah")
    darood = Recitation(arabic_name="درود", english_name="Darood Sharif",
                        category="Darood")
    await yaseen.insert()
    await darood.insert()
    await submission_service.submit_bulk(user, BulkSubmissionCreate(items=[
        BulkItem(recitation_id=yaseen.id, count=5, client_uuid="a"),
        BulkItem(recitation_id=darood.id, count=100, client_uuid="b"),
    ]))


async def test_community_breakdown(init_db):
    user = await _user()
    await _seed_submissions(user)

    stats = await stats_service.community(user)
    assert stats.total == 105
    names = {r.name: r.count for r in stats.by_recitation}
    assert names["Darood Sharif"] == 100 and names["Surah Ya-Sin"] == 5
    cats = {c.category: c.count for c in stats.by_category}
    assert cats["Darood"] == 100 and cats["Surah"] == 5
    # Sorted desc by count.
    assert stats.by_recitation[0].name == "Darood Sharif"
    assert stats.can_manage is False  # plain member


async def test_member_cannot_close_week(init_db):
    member = await _user()
    assert can_manage_weeks(member) is False


async def test_super_member_can_close_and_resets(init_db):
    admin = await _user("Admin", GlobalRole.SUPER_MEMBER)
    await _seed_submissions(admin)
    assert can_manage_weeks(admin) is True

    before = await stats_service.community(admin)
    assert before.total == 105

    gid = await community_service.resolve_group(admin, None)
    # mongomock enforces the partial-unique active-week index as a plain unique
    # index (real MongoDB respects the status filter); drop it so the reopen
    # insert isn't a false duplicate in this test.
    await WeeklySession.get_motor_collection().drop_indexes()
    await weekly_service.lock_week(gid, admin.id)

    # New week starts at zero; the old week is archived in history.
    after = await stats_service.community(admin)
    assert after.total == 0
    history = await stats_service.weeks(admin)
    assert len(history) == 1
    assert history[0].total == 105
