"""Shared setup for the B2–B5 tests (one session, like inside a request)."""
from __future__ import annotations

from app.models.category import Recitation
from app.models.group import MemberRole
from app.models.user import User
from app.repositories.group_repo import membership_repository
from app.schemas.group import GroupCreate
from app.schemas.submission import SubmissionCreate
from app.services import push_service
from app.services.group_service import group_service
from app.services.join_service import join_service
from app.services.submission_service import submission_service
from tests.helpers import insert

_n = {"i": 0}


async def user(name: str, **kw) -> User:
    _n["i"] += 1
    return await insert(User(firebase_uid=f"uid-{name}-{_n['i']}", display_name=name, **kw))


async def recitation() -> Recitation:
    return await insert(Recitation(arabic_name="درود", english_name="Darood"))


async def log(u: User, rec: Recitation, count: int, group_id=None):
    return await submission_service.submit(
        u, SubmissionCreate(recitation_id=rec.id, count=count, group_id=group_id), notify=False)


async def group(owner: User, name: str = "Masjid Circle", **kw):
    return await group_service.create_group(owner, GroupCreate(name=name, **kw))


async def add(g, u: User, role: MemberRole = MemberRole.MEMBER):
    m = await join_service.add_member(g, u, by=u)
    if role != MemberRole.MEMBER:
        m.role = role
        await membership_repository.save(m)
    return m


def outbox(kind: str | None = None, topic: str | None = None) -> list[dict]:
    return [m for m in push_service.outbox
            if (kind is None or m["kind"] == kind) and (topic is None or m.get("topic") == topic)]
