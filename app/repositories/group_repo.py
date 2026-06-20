"""Group & membership repositories."""
from __future__ import annotations

from beanie import PydanticObjectId

from app.models.group import Group, Membership, MemberStatus
from app.repositories.base import BaseRepository


class GroupRepository(BaseRepository[Group]):
    def __init__(self) -> None:
        super().__init__(Group)

    async def get_by_invite_code(self, code: str) -> Group | None:
        return await Group.find_one(Group.invite_code == code, Group.deleted == False)  # noqa: E712


class MembershipRepository(BaseRepository[Membership]):
    def __init__(self) -> None:
        super().__init__(Membership)

    async def get(self, group_id: PydanticObjectId, user_id: PydanticObjectId):  # type: ignore[override]
        return await Membership.find_one(
            Membership.group_id == group_id,
            Membership.user_id == user_id,
            Membership.deleted == False,  # noqa: E712
        )

    async def list_for_user(self, user_id: PydanticObjectId) -> list[Membership]:
        return await Membership.find(
            Membership.user_id == user_id,
            Membership.status == MemberStatus.ACTIVE,
            Membership.deleted == False,  # noqa: E712
        ).to_list()

    async def list_for_group(self, group_id: PydanticObjectId) -> list[Membership]:
        return await Membership.find(
            Membership.group_id == group_id,
            Membership.deleted == False,  # noqa: E712
        ).to_list()


group_repository = GroupRepository()
membership_repository = MembershipRepository()
