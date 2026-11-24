"""Tests for the shared community group, auto-join and feed enrichment."""
from __future__ import annotations

import pytest

from app.models.activity import ActivityItem
from app.models.group import Membership
from app.models.user import User
from app.services.community_service import community_service
from app.services.dashboard_service import dashboard_service
from tests.helpers import count, find_one, insert

pytestmark = pytest.mark.asyncio


async def _make_user(name: str = "Ramiz") -> User:
    user = User(firebase_uid=f"uid-{name}", display_name=name, email=f"{name}@x.com")
    await insert(user)
    return user


async def test_global_group_is_singleton(init_db):
    g1 = await community_service.get_or_create_global_group()
    g2 = await community_service.get_or_create_global_group()
    assert g1.id == g2.id


async def test_resolve_group_auto_joins_user(init_db):
    user = await _make_user()
    gid = await community_service.resolve_group(user, None)

    membership = await find_one(Membership,
        Membership.group_id == gid, Membership.user_id == user.id
    )
    assert membership is not None
    # Idempotent: calling again doesn't create a duplicate.
    await community_service.resolve_group(user, None)
    n = await count(Membership,
                    Membership.group_id == gid, Membership.user_id == user.id)
    assert n == 1


async def test_feed_enriches_actor_name_and_initial(init_db):
    user = await _make_user("Aisha")
    group = await community_service.get_or_create_global_group()
    await insert(ActivityItem(
        group_id=group.id, actor_id=user.id, type="submission",
        text="Completed Surah Yaseen", reactions={"fire": 3},
    ))

    items = await dashboard_service.feed(group.id, limit=10)
    assert len(items) == 1
    assert items[0].actor_name == "Aisha"
    assert items[0].initial == "A"
    assert items[0].reactions.get("fire") == 3
