"""Groups: create, my groups, Discover, a group's home, board, invites,
requests, members, announcements, activity and audit log."""
from __future__ import annotations

import uuid
from typing import Literal

from fastapi import APIRouter, Depends, Query, Response, status

from app.api.v1.paging import check_keyset, offset_of, set_next
from app.dependencies.auth import get_current_user
from app.models.user import User
from app.schemas.auth import OkResponse
from app.schemas.board import BoardResponse
from app.schemas.group import (
    AnnouncementCreate,
    AnnouncementOut,
    AuditOut,
    DecideMany,
    DecideResult,
    DeleteGroupBody,
    GroupActivityOut,
    GroupCreate,
    GroupDetail,
    GroupPreview,
    GroupUpdate,
    InviteOut,
    JoinRequestOut,
    JoinResult,
    MemberOut,
    MyGroup,
    RoleChange,
    TransferBody,
)
from app.services.group_service import group_service
from app.services.join_service import join_service
from app.services.member_service import member_service

router = APIRouter(prefix="/groups", tags=["groups"])


# ── Collection ──────────────────────────────────────────────────────────────

@router.post("", response_model=GroupDetail, status_code=status.HTTP_201_CREATED)
async def create_group(body: GroupCreate, user: User = Depends(get_current_user)) -> GroupDetail:
    """Start a group (you own at most 10). It gets a 6-character invite code."""
    group = await group_service.create_group(user, body)
    detail = await group_service.detail(user, group.id)
    assert isinstance(detail, GroupDetail)
    return detail


@router.get("/mine", response_model=list[MyGroup])
async def my_groups(user: User = Depends(get_current_user)) -> list[MyGroup]:
    """The global circle first, then your groups by this week's total."""
    return await group_service.mine(user)


@router.get("/discover", response_model=list[GroupPreview])
async def discover(
    response: Response,
    q: str | None = Query(default=None, max_length=80),
    sort: Literal["active", "new"] = "active",
    cursor: str | None = None,
    limit: int = Query(default=20, ge=1, le=50),
    user: User = Depends(get_current_user),
) -> list[GroupPreview]:
    """Public groups only. ``active`` = most logged in the last 7 days."""
    items, nxt = await join_service.discover(user, q, sort, offset_of(cursor), limit)
    set_next(response, nxt)
    return items


# ── One group ───────────────────────────────────────────────────────────────

@router.get("/{group_id}", response_model=GroupDetail | GroupPreview)
async def get_group(group_id: uuid.UUID, user: User = Depends(get_current_user)):
    """Members: the full group. Others: a preview of a public group (no
    member list). 410 = deleted, 403 code=banned, 404 = private or unknown."""
    return await group_service.detail(user, group_id)


@router.patch("/{group_id}", response_model=GroupDetail)
async def update_group(group_id: uuid.UUID, body: GroupUpdate,
                       user: User = Depends(get_current_user)) -> GroupDetail:
    """Owner and admins: name, description, avatar, privacy, join rule, goal, total-only."""
    return await group_service.update_group(group_id, body, user)


@router.delete("/{group_id}", response_model=OkResponse)
async def delete_group(group_id: uuid.UUID, body: DeleteGroupBody,
                       user: User = Depends(get_current_user)) -> OkResponse:
    """Owner only; ``confirm_name`` must be the group's name. Members get a push;
    everyone's recitations stay in their own stats."""
    await group_service.delete_group(group_id, user, body.confirm_name)
    return OkResponse()


@router.get("/{group_id}/board", response_model=BoardResponse)
async def group_board(
    group_id: uuid.UUID,
    scope: Literal["week", "all"] = "week",
    around_me: bool = False,
    limit: int = Query(default=100, ge=1, le=500),
    user: User = Depends(get_current_user),
) -> BoardResponse:
    """Members' logs since they joined. Total-only groups show members just
    the group total and their own number (owner and admins see the board)."""
    return await group_service.board(user, group_id, scope, around_me, limit)


# ── Invites and joining ─────────────────────────────────────────────────────

@router.get("/{group_id}/invite", response_model=InviteOut)
async def get_invite(group_id: uuid.UUID, user: User = Depends(get_current_user)) -> InviteOut:
    return await join_service.invite(user, group_id)


@router.post("/{group_id}/invite/reset", response_model=InviteOut)
async def reset_invite(group_id: uuid.UUID, user: User = Depends(get_current_user)) -> InviteOut:
    """Owner and admins: a new code; the old code and link stop working."""
    return await join_service.reset_code(user, group_id)


@router.post("/{group_id}/join", response_model=JoinResult)
async def join_public(group_id: uuid.UUID, user: User = Depends(get_current_user)) -> JoinResult:
    """Join a public group from Discover (or ask, if an admin approves joins)."""
    return await join_service.join_public(user, group_id)


@router.get("/{group_id}/requests", response_model=list[JoinRequestOut])
async def list_requests(group_id: uuid.UUID, response: Response, cursor: str | None = None,
                        limit: int = Query(default=50, ge=1, le=100),
                        user: User = Depends(get_current_user)) -> list[JoinRequestOut]:
    check_keyset(cursor)
    items, nxt = await join_service.requests(user, group_id, cursor, limit)
    set_next(response, nxt)
    return items


@router.post("/{group_id}/requests/approve", response_model=DecideResult)
async def approve_many(group_id: uuid.UUID, body: DecideMany,
                       user: User = Depends(get_current_user)) -> DecideResult:
    """Approve several at once (stops when the group is full)."""
    return await join_service.approve_many(user, group_id, body.user_ids)


@router.post("/{group_id}/requests/{user_id}/approve", response_model=OkResponse)
async def approve(group_id: uuid.UUID, user_id: uuid.UUID,
                  user: User = Depends(get_current_user)) -> OkResponse:
    await join_service.approve(user, group_id, user_id)
    return OkResponse()


@router.post("/{group_id}/requests/{user_id}/decline", response_model=OkResponse)
async def decline(group_id: uuid.UUID, user_id: uuid.UUID,
                  user: User = Depends(get_current_user)) -> OkResponse:
    """They can ask again 7 days later."""
    await join_service.decline(user, group_id, user_id)
    return OkResponse()


# ── Members ─────────────────────────────────────────────────────────────────

@router.get("/{group_id}/members", response_model=list[MemberOut])
async def members(group_id: uuid.UUID, response: Response,
                  q: str | None = Query(default=None, max_length=80),
                  status_: Literal["active", "banned"] = Query(default="active", alias="status"),
                  cursor: str | None = None, limit: int = Query(default=50, ge=1, le=100),
                  user: User = Depends(get_current_user)) -> list[MemberOut]:
    """Owner first, then admins, moderators, members (A→Z). ``status=banned``
    lists banned people (for those who can unban)."""
    items, nxt = await member_service.members(user, group_id, q, status_, offset_of(cursor), limit)
    set_next(response, nxt)
    return items


@router.patch("/{group_id}/members/{user_id}", response_model=MemberOut)
async def change_role(group_id: uuid.UUID, user_id: uuid.UUID, body: RoleChange,
                      user: User = Depends(get_current_user)) -> MemberOut:
    return await member_service.set_role(user, group_id, user_id, body.role)


@router.delete("/{group_id}/members/{user_id}", response_model=OkResponse)
async def remove_member(group_id: uuid.UUID, user_id: uuid.UUID,
                        user: User = Depends(get_current_user)) -> OkResponse:
    await member_service.remove(user, group_id, user_id)
    return OkResponse()


@router.post("/{group_id}/members/{user_id}/ban", response_model=OkResponse)
async def ban_member(group_id: uuid.UUID, user_id: uuid.UUID,
                     user: User = Depends(get_current_user)) -> OkResponse:
    await member_service.ban(user, group_id, user_id)
    return OkResponse()


@router.post("/{group_id}/members/{user_id}/unban", response_model=OkResponse)
async def unban_member(group_id: uuid.UUID, user_id: uuid.UUID,
                       user: User = Depends(get_current_user)) -> OkResponse:
    await member_service.unban(user, group_id, user_id)
    return OkResponse()


@router.post("/{group_id}/leave", response_model=OkResponse)
async def leave(group_id: uuid.UUID, user: User = Depends(get_current_user)) -> OkResponse:
    """The owner has to transfer ownership first (409 owner_must_transfer)."""
    await member_service.leave(user, group_id)
    return OkResponse()


@router.post("/{group_id}/transfer", response_model=OkResponse)
async def transfer(group_id: uuid.UUID, body: TransferBody,
                   user: User = Depends(get_current_user)) -> OkResponse:
    """Owner only. The new owner must be a member; you become an admin."""
    await member_service.transfer(user, group_id, body.user_id)
    return OkResponse()


# ── Announcements, activity, audit ──────────────────────────────────────────

@router.post("/{group_id}/announcements", response_model=AnnouncementOut,
             status_code=status.HTTP_201_CREATED)
async def announce(group_id: uuid.UUID, body: AnnouncementCreate,
                   user: User = Depends(get_current_user)) -> AnnouncementOut:
    """Owner, admins and moderators. Replaces the pinned one; pushes to members."""
    return await member_service.announce(user, group_id, body.text)


@router.get("/{group_id}/announcements/latest", response_model=AnnouncementOut | None)
async def latest_announcement(group_id: uuid.UUID,
                              user: User = Depends(get_current_user)) -> AnnouncementOut | None:
    return await member_service.latest_announcement(user, group_id)


@router.get("/{group_id}/activity", response_model=list[GroupActivityOut])
async def activity(group_id: uuid.UUID, response: Response, cursor: str | None = None,
                   limit: int = Query(default=30, ge=1, le=100),
                   user: User = Depends(get_current_user)) -> list[GroupActivityOut]:
    """Joins, goal reached, week winners and announcements, newest first."""
    check_keyset(cursor)
    items, nxt = await member_service.activity(user, group_id, cursor, limit)
    set_next(response, nxt)
    return items


@router.get("/{group_id}/audit", response_model=list[AuditOut])
async def audit_log(group_id: uuid.UUID, response: Response, cursor: str | None = None,
                    limit: int = Query(default=50, ge=1, le=100),
                    user: User = Depends(get_current_user)) -> list[AuditOut]:
    """Owner only: who removed, banned, promoted, approved... whom."""
    check_keyset(cursor)
    items, nxt = await member_service.audit_log(user, group_id, cursor, limit)
    set_next(response, nxt)
    return items


