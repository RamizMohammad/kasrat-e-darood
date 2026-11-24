"""B2: global board (ties, hidden users, your own row), Hall of Fame, and
the automatic Friday close (boundary, run twice, manual close)."""
from __future__ import annotations

from datetime import datetime, timedelta, timezone
from zoneinfo import ZoneInfo

import httpx
import pytest

from app.core import clock
from app.database.postgres import session_scope
from app.models.weekly_session import WeeklySession, WeekStatus
from app.security.tokens import create_access_token
from app.services.board_service import board_service
from app.services.weekly_service import week_end_for, weekly_service
from app.tasks.scheduler import every_minutes
from tests import group_helpers as h
from tests.helpers import count

pytestmark = pytest.mark.asyncio
IST = ZoneInfo("Asia/Kolkata")


def ist(y, mo, d, hh, mm):
    return datetime(y, mo, d, hh, mm, tzinfo=IST).astimezone(timezone.utc)


# ── Week boundary ───────────────────────────────────────────────────────────

def test_next_close_is_friday_1830_ist():
    # 2026-11-27 is a Friday.
    assert clock.next_week_close(ist(2026, 11, 26, 9, 0)) == ist(2026, 11, 27, 18, 30)    # Thu
    assert clock.next_week_close(ist(2026, 11, 27, 18, 29)) == ist(2026, 11, 27, 18, 30)  # a minute before
    assert clock.next_week_close(ist(2026, 11, 27, 18, 30)) == ist(2026, 12, 4, 18, 30)   # at the close
    assert clock.next_week_close(ist(2026, 11, 28, 0, 1)) == ist(2026, 12, 4, 18, 30)     # Sat


def test_early_close_runs_until_the_close_after_next():
    # Closed by hand on Friday at 17:00: the new week doesn't end 90 min later.
    assert week_end_for(ist(2026, 11, 27, 17, 0)) == ist(2026, 12, 4, 18, 30)
    # Closed on Tuesday: it runs to this Friday.
    assert week_end_for(ist(2026, 11, 24, 12, 0)) == ist(2026, 11, 27, 18, 30)


def test_every_minutes():
    t = datetime(2026, 11, 27, 13, 2, 30, tzinfo=timezone.utc)
    assert every_minutes(5)(t) == datetime(2026, 11, 27, 13, 5, tzinfo=timezone.utc)


# ── Ranking ─────────────────────────────────────────────────────────────────

async def test_ties_share_a_rank_and_first_there_goes_first(init_db):
    rec = await h.recitation()
    a, b, c = await h.user("Ali"), await h.user("Bilal"), await h.user("Chand")
    await h.log(a, rec, 10)
    await h.log(b, rec, 10)
    await h.log(c, rec, 5)
    board = await board_service.global_board(c, "week", False, 100)
    assert [(e.user.display_name, e.rank, e.total) for e in board.entries] == [
        ("Ali", 1, 10), ("Bilal", 1, 10), ("Chand", 3, 5)]
    assert board.total == 25 and board.week is not None


async def test_hidden_users_are_a_member_except_to_themselves(init_db):
    rec = await h.recitation()
    a = await h.user("Ali")
    b = await h.user("Bilal", preferences={"hide_on_boards": True})
    await h.log(a, rec, 3)
    await h.log(b, rec, 9)
    seen_by_a = await board_service.global_board(a, "week", False, 100)
    top = seen_by_a.entries[0]
    assert top.user.hidden and top.user.id is None and top.user.display_name == "A member"
    assert top.user.avatar is None and top.total == 9 and top.rank == 1
    seen_by_b = await board_service.global_board(b, "week", False, 100)
    assert seen_by_b.me.user.display_name == "Bilal" and not seen_by_b.me.user.hidden


async def test_your_row_is_always_returned(init_db):
    rec = await h.recitation()
    users = [await h.user(f"U{i}") for i in range(15)]
    for i, u in enumerate(users):
        await h.log(u, rec, 100 - i)
    last = users[-1]
    board = await board_service.global_board(last, "week", False, 3)
    assert len(board.entries) == 3 and board.me.rank == 15 and board.me.is_me
    around = await board_service.global_board(last, "week", True, 3)
    assert [e.rank for e in around.entries] == [10, 11, 12, 13, 14, 15]


async def test_all_time_scope_uses_lifetime(init_db):
    rec = await h.recitation()
    a, b = await h.user("Ali"), await h.user("Bilal")
    await h.log(a, rec, 4)
    await weekly_service.close_week(a.id)
    await h.log(b, rec, 6)
    week = await board_service.global_board(a, "week", False, 10)
    assert [e.total for e in week.entries] == [6]
    all_time = await board_service.global_board(a, "all", False, 10)
    assert [(e.user.display_name, e.total) for e in all_time.entries] == [("Bilal", 6), ("Ali", 4)]


# ── Closing ─────────────────────────────────────────────────────────────────

async def test_scheduled_close_runs_once_and_fills_hall_of_fame(init_db):
    rec = await h.recitation()
    a, b = await h.user("Ali"), await h.user("Bilal")
    await h.log(a, rec, 7)
    await h.log(b, rec, 3)
    week = await weekly_service.current()

    assert await weekly_service.close_due() is None            # not Friday evening yet

    due = week.end_date
    archived = await weekly_service.close_due(now=due + timedelta(seconds=30))
    assert archived is not None and archived.id == week.id
    assert archived.status == WeekStatus.ARCHIVED and archived.group_total == 10
    assert [hh["display_name"] for hh in archived.hall_of_fame] == ["Ali", "Bilal"]

    nxt = await weekly_service.current()
    assert nxt.week_number == week.week_number + 1
    assert nxt.start_date == due                                # opens at the boundary
    assert nxt.end_date == due + timedelta(days=7)

    # A second trigger right after does nothing: no second archive, no extra week.
    assert await weekly_service.close_due(now=due + timedelta(minutes=5)) is None
    assert await count(WeeklySession, WeeklySession.status == WeekStatus.ACTIVE) == 1
    assert await count(WeeklySession, WeeklySession.status == WeekStatus.ARCHIVED) == 1

    fame, _ = await board_service.hall_of_fame(a, None, 10)
    assert fame[0].week_number == week.week_number and fame[0].total == 10
    assert [(e.rank, e.total) for e in fame[0].top] == [(1, 7), (2, 3)]


async def test_manual_close_twice_with_week_id_closes_once(init_db):
    a = await h.user("Admin")
    week = await weekly_service.current()
    first = await weekly_service.close_week(a.id, expected_week_id=week.id)
    assert first is not None
    assert await weekly_service.close_week(a.id, expected_week_id=week.id) is None
    assert (await weekly_service.current()).week_number == week.week_number + 1


async def test_log_after_close_lands_in_the_new_week(init_db):
    rec = await h.recitation()
    a = await h.user("Ali")
    await h.log(a, rec, 5)
    old = await weekly_service.current()
    await weekly_service.close_week(a.id)
    r = await h.log(a, rec, 2)
    new = await weekly_service.current()
    assert r.submission.week_id == new.id != old.id
    assert r.totals.user_week == 2 and r.totals.group_week == 2


async def test_previous_rank_from_last_week(init_db):
    rec = await h.recitation()
    a, b = await h.user("Ali"), await h.user("Bilal")
    await h.log(a, rec, 9)
    await h.log(b, rec, 1)
    await weekly_service.close_week(a.id)
    await h.log(b, rec, 5)
    await h.log(a, rec, 1)
    board = await board_service.global_board(a, "week", False, 10)
    assert [(e.user.display_name, e.rank, e.previous_rank) for e in board.entries] == [
        ("Bilal", 1, 2), ("Ali", 2, 1)]


async def test_hidden_winner_stays_hidden_in_hall_of_fame(init_db):
    rec = await h.recitation()
    a = await h.user("Ali", preferences={"hide_on_boards": True})
    b = await h.user("Bilal")
    await h.log(a, rec, 9)
    await weekly_service.close_week(a.id)
    fame, _ = await board_service.hall_of_fame(b, None, 10)
    assert fame[0].top[0].user.display_name == "A member"


# ── HTTP ────────────────────────────────────────────────────────────────────

async def test_http_global_board_and_hall_of_fame_paging(clean_db):
    from app.main import app
    async with session_scope():
        rec = await h.recitation()
        a = await h.user("Ali")
        for i in range(3):
            await h.log(a, rec, i + 1)
            await weekly_service.close_week(a.id)
        uid = a.id
    hdr = {"Authorization": f"Bearer {create_access_token(str(uid))}"}
    async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app), base_url="http://t") as c:
        r = await c.get("/api/v1/leaderboards/global?scope=all", headers=hdr)
        assert r.status_code == 200, r.text
        assert r.json()["me"]["total"] == 6
        r = await c.get("/api/v1/leaderboards/global?scope=month", headers=hdr)
        assert r.status_code == 422
        r = await c.get("/api/v1/weeks/current", headers=hdr)
        assert r.json()["week_number"] == 4
        r = await c.get("/api/v1/weeks/hall-of-fame?limit=2", headers=hdr)
        assert [w["week_number"] for w in r.json()] == [3, 2]
        nxt = r.headers["X-Next-Cursor"]
        r = await c.get("/api/v1/weeks/hall-of-fame", params={"limit": 2, "cursor": nxt}, headers=hdr)
        assert [w["week_number"] for w in r.json()] == [1] and "X-Next-Cursor" not in r.headers
        r = await c.get("/api/v1/weeks/hall-of-fame?cursor=junk", headers=hdr)
        assert r.status_code == 422
