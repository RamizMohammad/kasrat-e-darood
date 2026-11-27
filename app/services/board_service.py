"""Leaderboards: the global board, Hall of Fame, and the shared ranking code.

Ranks are standard competition ranks (1, 2, 2, 4): ties share a rank. The
order inside a tie is whoever got there first.
"""
from __future__ import annotations

import uuid

from app.core.exceptions import ValidationAppError
from app.database.redis import cache
from app.models.user import User
from app.models.weekly_session import WeeklySession
from app.repositories import board_repo
from app.repositories.base import decode_cursor
from app.repositories.user_repo import user_repository
from app.repositories.weekly_repo import weekly_repository
from app.schemas.board import BoardEntry, BoardResponse, BoardUser, HallOfFameWeek, WeekInfo
from app.schemas.preferences import Preferences
from app.config.settings import settings

HIDDEN_NAME = "A member"
AROUND = 5       # rows above and below you with around_me=true


def ranked(rows: list[tuple[uuid.UUID, int]]) -> list[tuple[uuid.UUID, int, int]]:
    """[(user_id, total)] in board order → [(user_id, total, rank)]."""
    out, prev, rank = [], None, 0
    for i, (uid, total) in enumerate(rows, start=1):
        if total != prev:
            rank, prev = i, total
        out.append((uid, total, rank))
    return out


def is_hidden(user: User) -> bool:
    return Preferences.from_stored(user.preferences).hide_on_boards


HIDDEN_ID = uuid.UUID(int=0)


def feed_actor(user: User | None, viewer_id: uuid.UUID | None, actor_id: uuid.UUID,
               text: str, fallback: str) -> tuple[uuid.UUID, str, str]:
    """(actor_id, actor_name, text) for a feed row. People who hide themselves
    on boards also stay hidden in feeds: their id is blanked and their name
    in older rows' text is replaced."""
    if user is None:
        return actor_id, fallback, text
    if user.id != viewer_id and is_hidden(user):
        if user.display_name and text.startswith(user.display_name):
            text = HIDDEN_NAME + text[len(user.display_name):]
        return HIDDEN_ID, HIDDEN_NAME, text
    return actor_id, user.display_name, text


def board_user(user: User | None, viewer_id: uuid.UUID | None) -> BoardUser:
    if user is None:
        return BoardUser(id=None, display_name=HIDDEN_NAME, hidden=True)
    if user.id != viewer_id and is_hidden(user):
        return BoardUser(id=None, display_name=HIDDEN_NAME, hidden=True)
    return BoardUser(id=user.id, display_name=user.display_name, avatar=user.avatar,
                     photo_url=user.photo_url)


def week_info(week: WeeklySession | None) -> WeekInfo | None:
    if week is None:
        return None
    return WeekInfo(id=week.id, week_number=week.week_number,
                    starts_at=week.start_date, closes_at=week.end_date)


async def build_board(rows: list[tuple[uuid.UUID, int]], viewer_id: uuid.UUID, *,
                      around_me: bool, limit: int,
                      previous: dict[uuid.UUID, int] | None = None) -> tuple[list[BoardEntry], BoardEntry | None]:
    """Turn ordered rows into entries (top ``limit``, or the window around
    the viewer) plus the viewer's own entry."""
    ranks = ranked(rows)
    my_index = next((i for i, (uid, _, _) in enumerate(ranks) if uid == viewer_id), None)
    if around_me and my_index is not None:
        lo = max(0, my_index - AROUND)
        window = ranks[lo:my_index + AROUND + 1]
    else:
        window = ranks[:limit]
    wanted = {uid for uid, _, _ in window}
    if my_index is not None:
        wanted.add(viewer_id)
    people = await user_repository.get_many(wanted)

    def entry(uid, total, rank) -> BoardEntry:
        return BoardEntry(rank=rank, total=total, user=board_user(people.get(uid), viewer_id),
                          is_me=uid == viewer_id,
                          previous_rank=(previous or {}).get(uid))

    entries = [entry(*r) for r in window]
    me = entry(*ranks[my_index]) if my_index is not None else None
    return entries, me


def _previous_ranks(week: WeeklySession | None) -> dict[uuid.UUID, int]:
    """Ranks in a closed week, from its frozen totals."""
    if week is None or not week.totals:
        return {}
    rows = sorted(((uuid.UUID(k), int(v)) for k, v in week.totals.items()), key=lambda r: -r[1])
    return {uid: rank for uid, _, rank in ranked(rows)}


class BoardService:
    async def global_board(self, viewer: User, scope: str, around_me: bool, limit: int) -> BoardResponse:
        from app.services.community_service import community_service
        group = await community_service.get_or_create_global_group()
        week = await weekly_repository.get_active(group.id)
        if scope == "week":
            if week is None:
                return BoardResponse(scope=scope, entries=[])
            key = f"board:global:week:{week.id}"
            rows = await self._cached_rows(key, lambda: board_repo.global_week(week.id))
            last = await weekly_repository.latest_closed(group.id)
            entries, me = await build_board(rows, viewer.id, around_me=around_me, limit=limit,
                                            previous=_previous_ranks(last))
            return BoardResponse(scope=scope, week=week_info(week), total=week.group_total,
                                 entries=entries, me=me)
        if scope == "all":
            rows = await self._cached_rows("board:global:all", board_repo.global_all_time)
            entries, me = await build_board(rows, viewer.id, around_me=around_me, limit=limit)
            return BoardResponse(scope=scope, week=week_info(week),
                                 total=sum(t for _, t in rows), entries=entries, me=me)
        raise ValidationAppError("scope must be week or all", code="invalid_scope")

    async def hall_of_fame(self, viewer: User, cursor: str | None, limit: int):
        from app.services.community_service import community_service
        if cursor and decode_cursor(cursor) is None:
            raise ValidationAppError("Invalid cursor", code="invalid_cursor")
        group = await community_service.get_or_create_global_group()
        weeks, next_cursor = await weekly_repository.history(group.id, cursor, limit)
        ids = {uuid.UUID(str(h["user_id"])) for w in weeks for h in (w.hall_of_fame or [])}
        people = await user_repository.get_many(ids)
        items = []
        for w in weeks:
            top = [BoardEntry(rank=int(h["rank"]), total=int(h["total"]),
                              user=board_user(people.get(uuid.UUID(str(h["user_id"]))), viewer.id),
                              is_me=str(h["user_id"]) == str(viewer.id))
                   for h in (w.hall_of_fame or [])]
            items.append(HallOfFameWeek(week_id=w.id, week_number=w.week_number, label=w.gregorian_week,
                                        starts_at=w.start_date, closed_at=w.locked_at,
                                        total=w.group_total, top=top))
        return items, next_cursor

    @staticmethod
    async def _cached_rows(key: str, load) -> list[tuple[uuid.UUID, int]]:
        cached = await cache.get_json(key)
        if cached is not None:
            return [(uuid.UUID(u), int(t)) for u, t in cached]
        rows = await load()
        await cache.set_json(key, [[str(u), t] for u, t in rows], ttl=settings.GROUP_BOARD_CACHE_SECONDS)
        return rows


board_service = BoardService()


async def invalidate_boards_for(user_id: uuid.UUID) -> None:
    """After a log or undo: the global boards and every board the user is on."""
    from app.repositories.group_repo import membership_repository
    await cache.delete_prefix("board:global")
    for gid in await membership_repository.active_group_ids(user_id):
        await cache.delete_prefix(f"board:{gid}")
    await cache.delete_prefix("board:groups")
