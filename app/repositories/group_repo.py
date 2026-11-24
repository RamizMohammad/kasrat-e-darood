"""Group & membership repositories."""
from __future__ import annotations

import uuid

from app.database.postgres import db
from app.models.group import Group, Membership, MemberStatus
from app.repositories.base import BaseRepository


class GroupRepository(BaseRepository[Group]):
    def __init__(self) -> None:
        super().__init__(Group)

    async def get_by_invite_code(self, code: str) -> Group | None:
        return await self.first(Group.invite_code == code)

    async def list_by_ids(self, ids: list[uuid.UUID]) -> list[Group]:
        if not ids:
            return []
        result = await db().execute(self.live().where(Group.id.in_(ids)))
        return list(result.scalars().all())


class MembershipRepository(BaseRepository[Membership]):
    def __init__(self) -> None:
        super().__init__(Membership)

    async def get(self, group_id: uuid.UUID, user_id: uuid.UUID):  # type: ignore[override]
        return await self.first(Membership.group_id == group_id, Membership.user_id == user_id)

    async def list_for_user(self, user_id: uuid.UUID) -> list[Membership]:
        result = await db().execute(self.live().where(
            Membership.user_id == user_id, Membership.status == MemberStatus.ACTIVE))
        return list(result.scalars().all())

    async def list_for_group(self, group_id: uuid.UUID) -> list[Membership]:
        result = await db().execute(self.live().where(Membership.group_id == group_id)
                                    .order_by(Membership.created_at))
        return list(result.scalars().all())


group_repository = GroupRepository()
membership_repository = MembershipRepository()
