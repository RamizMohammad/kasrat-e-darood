"""Shared community group: the one app-wide circle every user belongs to.

Its weekly sessions are THE weeks: every log points at the active one, and
every board (global and group) reads from them.

The product currently uses one global community group rather than per-user
groups. This service lazily bootstraps that group and auto-joins users, and
resolves the effective group_id for read endpoints (dashboard, leaderboard,
feed) so callers never have to know the id.
"""
from __future__ import annotations

import uuid

from app.models.group import (
    Group,
    MemberRole,
    Membership,
    MemberStatus,
    Privacy,
)
from app.models.user import User
from app.repositories.group_repo import group_repository, membership_repository
from app.services.weekly_service import weekly_service

# Reserved, unique invite code that identifies the singleton community group.
GLOBAL_INVITE_CODE = "GLOBAL00"


class CommunityService:
    async def get_or_create_global_group(self) -> Group:
        group = await group_repository.get_global()
        if group is None:
            # Older databases: the community group exists but isn't marked yet.
            group = await group_repository.get_by_invite_code(GLOBAL_INVITE_CODE)
            if group is not None:
                group.is_global = True
                await group_repository.save(group)
                if await weekly_service.global_week_or_none(group.id) is None:
                    await weekly_service.open_first_week(group)
                return group
        if group is None:
            group = Group(
                name="Noor Community",
                description="The shared community circle for all members.",
                privacy=Privacy.PUBLIC,
                invite_code=GLOBAL_INVITE_CODE,
                owner_id=uuid.uuid4(),  # system-owned sentinel
                member_count=0,
                is_global=True,
                member_limit=2_000_000_000,
            )
            if await group_repository.create_unique(group) is None:
                # Another request created it at the same moment: use theirs.
                group = await group_repository.get_global()
                assert group is not None
                return group
            # An active weekly session is required by the dashboard/leaderboard.
            await weekly_service.open_first_week(group)
        return group

    async def ensure_member(self, user: User, group: Group) -> None:
        existing = await membership_repository.get(group.id, user.id)
        if existing is None:
            # create_unique: two first requests from one new user can race here.
            await membership_repository.create_unique(Membership(
                group_id=group.id,
                user_id=user.id,
                role=MemberRole.MEMBER,
                status=MemberStatus.ACTIVE,
                created_by=user.id,
            ))

    async def resolve_group(
        self, user: User, group_id: uuid.UUID | None
    ) -> uuid.UUID:
        """Return the group to read from, defaulting to the community group.

        When no group_id is supplied the user is (idempotently) joined to the
        global community group and its id is returned.
        """
        if group_id is not None:
            # Only groups you're an active member of (dashboard feed and the
            # legacy leaderboard read from it). Without this anyone could read
            # a private group's activity and board by guessing its id.
            from app.services.group_service import load_group, require_member
            group = await load_group(group_id)
            await require_member(group, user)
            return group.id
        group = await self.get_or_create_global_group()
        await self.ensure_member(user, group)
        return group.id


community_service = CommunityService()
