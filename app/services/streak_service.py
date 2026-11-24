"""Streaks: consecutive local (IST) days with at least one live log.

The current streak counts runs that end today or yesterday, so it doesn't drop
to zero in the morning before you've logged; it breaks once a whole day passes
with no log. It's recomputed from submissions on every log and undo (so it's
always exact), and a nightly job zeroes it for users who missed yesterday.
"""
from __future__ import annotations

import uuid
from dataclasses import dataclass
from datetime import date, timedelta

from sqlalchemy import and_, exists, text, update

from app.core import clock
from app.database.postgres import db
from app.models.submission import Submission
from app.models.user import User
from app.repositories.counters import _sync

# Islands of consecutive days ("gaps and islands"): day minus its row number
# is constant within a run of consecutive days.
_RUNS_SQL = text("""
    WITH days AS (
        SELECT DISTINCT (created_at AT TIME ZONE :tz)::date AS d
        FROM submissions
        WHERE user_id = :uid AND deleted = false
    ), runs AS (
        SELECT d, d - (row_number() OVER (ORDER BY d))::int AS grp FROM days
    )
    SELECT max(d) AS last_day, count(*) AS length
    FROM runs GROUP BY grp ORDER BY last_day DESC
""")


@dataclass(frozen=True)
class Streak:
    current: int
    best: int
    last_day: date | None


class StreakService:
    async def compute(self, user_id: uuid.UUID, today: date | None = None) -> Streak:
        today = today or clock.local_today()
        rows = (await db().execute(_RUNS_SQL, {"tz": clock.tz_name(), "uid": user_id})).all()
        if not rows:
            return Streak(0, 0, None)
        best = max(int(r.length) for r in rows)
        last_day, latest_len = rows[0].last_day, int(rows[0].length)
        current = latest_len if last_day >= today - timedelta(days=1) else 0
        return Streak(current, best, last_day)

    async def refresh(self, user: User) -> Streak:
        """Recompute and store streak_days / best_streak for one user."""
        s = await self.compute(user.id)
        await db().execute(
            update(User).where(User.id == user.id)
            .values(streak_days=s.current, best_streak=s.best)
            .execution_options(synchronize_session=False))
        _sync(User, user.id, streak_days=s.current, best_streak=s.best)
        return s

    async def reset_missed(self) -> int:
        """Nightly: zero the streak of everyone with no log since the start of
        yesterday (local). Returns how many users were reset."""
        since = clock.start_of_local_day(clock.days_ago(1))
        logged_recently = exists().where(and_(
            Submission.user_id == User.id,
            Submission.deleted.is_(False),
            Submission.created_at >= since,
        ))
        res = await db().execute(
            update(User).where(User.streak_days > 0, ~logged_recently)
            .values(streak_days=0)
            .execution_options(synchronize_session=False))
        return int(res.rowcount or 0)


streak_service = StreakService()
