"""Group use-cases: create, join, leave, members, invite codes."""
from __future__ import annotations

import secrets

from beanie import PydanticObjectId

from app.core.exceptions import ConflictError, NotFoundError, PermissionDeniedError
from app.models.group import Group, MemberRole, Membership, MemberStatus
from app.models.user import User
from app.repositories.group_repo import group_repository, membership_repository
from app.schemas.group import GroupCreate, GroupUpdate
from app.services.weekly_service import weekly_service


def _generate_invite_code() -> str:
    return secrets.token_urlsafe(6).replace("_", "").replace("-", "")[:8].upper()


class GroupService:
    async def create_group(self, owner: User, data: GroupCreate) -> Group:
        group = Group(
            name=data.name,
            description=data.description,
            privacy=data.privacy,
            timezone=data.timezone,
            invite_code=_generate_invite_code(),
            owner_id=owner.id,
            created_by=owner.id,
        )
        await group_repository.create(group)
        # Owner membership
        await Membership(
            group_id=group.id, user_id=owner.id,
            role=MemberRole.OWNER, status=MemberStatus.ACTIVE,
            created_by=owner.id,
        ).insert()
        # First active week
        await weekly_service.open_first_week(group)
        return group

    async def list_my_groups(self, user: User) -> list[Group]:
        memberships = await membership_repository.list_for_user(user.id)
        group_ids = [m.group_id for m in memberships]
        if not group_ids:
            return []
        return await Group.find(
            {"_id": {"$in": group_ids}, "deleted": False}
        ).to_list()

    async def get_group(self, group_id: PydanticObjectId) -> Group:
        group = await group_repository.get(group_id)
        if group is None:
            raise NotFoundError("Group not found")
        return group

    async def update_group(
        self, group_id: PydanticObjectId, data: GroupUpdate, by: User
    ) -> Group:
        group = await self.get_group(group_id)
        patch = data.model_dump(exclude_unset=True)
        for key, value in patch.items():
            setattr(group, key, value)
        return await group_repository.save(group)

    async def join_by_code(self, user: User, invite_code: str) -> Membership:
        group = await group_repository.get_by_invite_code(invite_code)
        if group is None:
            raise NotFoundError("Invalid invite code")
        existing = await membership_repository.get(group.id, user.id)
        if existing is not None:
            raise ConflictError("Already a member")
        membership = Membership(
            group_id=group.id, user_id=user.id,
            role=MemberRole.MEMBER, status=MemberStatus.ACTIVE,
            created_by=user.id,
        )
        await membership.insert()
        group.member_count += 1
        await group_repository.save(group)
        return membership

    async def leave_group(self, user: User, group_id: PydanticObjectId) -> None:
        membership = await membership_repository.get(group_id, user.id)
        if membership is None:
            raise NotFoundError("Not a member")
        if membership.role == MemberRole.OWNER:
            raise PermissionDeniedError("Owner must transfer ownership before leaving")
        await membership_repository.soft_delete(membership)
        group = await self.get_group(group_id)
        group.member_count = max(0, group.member_count - 1)
        await group_repository.save(group)

    async def rotate_invite_code(self, group_id: PydanticObjectId) -> str:
        group = await self.get_group(group_id)
        group.invite_code = _generate_invite_code()
        await group_repository.save(group)
        return group.invite_code

    async def list_members(self, group_id: PydanticObjectId) -> list[Membership]:
        return await membership_repository.list_for_group(group_id)


group_service = GroupService()
