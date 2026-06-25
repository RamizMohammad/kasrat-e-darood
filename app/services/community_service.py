"""Shared community group: a single app-wide group every user belongs to.

The product currently uses one global community group rather than per-user
groups. This service lazily bootstraps that group and auto-joins users, and
resolves the effective group_id for read endpoints (dashboard, leaderboard,
feed) so callers never have to know the id.
"""
from __future__ import annotations

from beanie import PydanticObjectId

from app.models.group import (
    Group,
    MemberRole,
    Membership,
    MemberStatus,
    Privacy,
)
from app.models.user import User
from app.repositories.group_repo import group_repository
from app.services.weekly_service import weekly_service

# Reserved, unique invite code that identifies the singleton community group.
GLOBAL_INVITE_CODE = "GLOBAL00"


class CommunityService:
    async def get_or_create_global_group(self) -> Group:
        group = await Group.find_one(
            Group.invite_code == GLOBAL_INVITE_CODE,
            Group.deleted == False,  # noqa: E712
        )
        if group is None:
            group = Group(
                name="Noor Community",
                description="The shared community circle for all members.",
                privacy=Privacy.PUBLIC,
                invite_code=GLOBAL_INVITE_CODE,
                owner_id=PydanticObjectId(),  # system-owned sentinel
                member_count=0,
            )
            await group_repository.create(group)
            # An active weekly session is required by the dashboard/leaderboard.
            await weekly_service.open_first_week(group)
        return group

    async def ensure_member(self, user: User, group: Group) -> None:
        existing = await Membership.find_one(
            Membership.group_id == group.id,
            Membership.user_id == user.id,
            Membership.deleted == False,  # noqa: E712
        )
        if existing is None:
            await Membership(
                group_id=group.id,
                user_id=user.id,
                role=MemberRole.MEMBER,
                status=MemberStatus.ACTIVE,
                created_by=user.id,
            ).insert()

    async def resolve_group(
        self, user: User, group_id: PydanticObjectId | None
    ) -> PydanticObjectId:
        """Return the group to read from, defaulting to the community group.

        When no group_id is supplied the user is (idempotently) joined to the
        global community group and its id is returned.
        """
        if group_id is not None:
            return group_id
        group = await self.get_or_create_global_group()
        await self.ensure_member(user, group)
        return group.id


community_service = CommunityService()
