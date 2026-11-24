"""The core submission use-case: store a recitation and roll up all totals."""
from __future__ import annotations

from datetime import datetime, timedelta, timezone

import uuid

from app.core import clock
from app.core.exceptions import ConflictError, NotFoundError, PermissionDeniedError
from app.database.redis import cache
from app.models.activity import ActivityItem
from app.models.submission import Submission
from app.models.user import User
from app.repositories.activity_repo import activity_repository
from app.repositories.counters import claim_undo, inc_user_lifetime, inc_week
from app.repositories.recitation_repo import recitation_repository
from app.repositories.submission_repo import submission_repository
from app.repositories.weekly_repo import weekly_repository
from app.schemas.submission import (
    BulkSubmissionCreate,
    MySubmissionOut,
    SubmissionCreate,
    SubmissionResult,
    SubmissionOut,
    Totals,
)
from app.config.settings import settings
from loguru import logger
from app.services.push_service import send_to_community
from app.services.streak_service import streak_service


def stats_cache_key(user_id) -> str:
    """Cache key for GET /statistics/me (cleared on every log and undo)."""
    return f"stats:me:{user_id}"


def _start_of_today() -> datetime:
    """Start of today in the app's time zone (IST), as a UTC instant."""
    return clock.start_of_today()


class SubmissionService:
    async def submit(self, user: User, data: SubmissionCreate,
                     notify: bool = True, refresh_streak: bool = True) -> SubmissionResult:
        """Store one log. It belongs to the user: it counts this (global) week
        on the global board and on every group board the user is in."""
        from app.services.community_service import community_service
        from app.services.weekly_service import weekly_service

        global_group = await community_service.get_or_create_global_group()
        group_id = await self._source_group(user, data.group_id, global_group.id)
        week = await weekly_service.current()

        # Idempotency: a repeated client_uuid returns the existing result.
        if data.client_uuid:
            existing = await submission_repository.find_by_client_uuid(
                user.id, data.client_uuid
            )
            if existing is not None:
                return await self._result(user, existing.week_id, existing)

        recitation = await recitation_repository.get(data.recitation_id)
        if recitation is None:
            raise NotFoundError("Recitation not found")

        submission = Submission(
            group_id=group_id, week_id=week.id, user_id=user.id,
            recitation_id=data.recitation_id, count=data.count, note=data.note,
            client_uuid=data.client_uuid, created_by=user.id,
        )
        if await submission_repository.create_unique(submission) is None:
            # Two requests with the same client_uuid raced past the check
            # above; the other one won. Return its result, count nothing.
            existing = await submission_repository.find_by_client_uuid(
                user.id, data.client_uuid or ""
            )
            if existing is None:
                raise ConflictError("Duplicate submission")
            return await self._result(user, existing.week_id, existing)

        # Roll-ups: atomic server-side increments (see repositories/counters).
        await inc_user_lifetime(user.id, data.count)   # also refreshes user.lifetime_total
        await inc_week(week.id, user.id, data.count)

        await activity_repository.create(ActivityItem(
            group_id=global_group.id, actor_id=user.id, type="submission.created",
            text=f"{user.display_name} completed {recitation.english_name} ×{data.count}",
            payload={"recitation_id": str(recitation.id), "count": data.count},
            created_by=user.id,
        ))

        if refresh_streak:
            await streak_service.refresh(user)

        await self._invalidate(user.id)
        try:
            from app.services.member_service import member_service
            await member_service.check_goals(user, week)
        except Exception:  # pragma: no cover - a push must never break a log
            logger.exception("goal check failed")

        # Notify the whole community (single submissions only; bulk sends one
        # summary push from submit_bulk to avoid spamming).
        if notify:
            try:
                await send_to_community(
                    "New recitation logged",
                    f"{user.display_name} completed {recitation.english_name} ×{data.count}",
                )
            except Exception:  # pragma: no cover - push must never break a submit
                pass

        return await self._result(user, week.id, submission)

    @staticmethod
    async def _source_group(user: User, group_id, global_id):
        """Where a log was made from (optional). A group you're not in is refused."""
        if group_id is None or group_id == global_id:
            return None
        from app.repositories.group_repo import membership_repository
        from app.models.group import MemberStatus
        m = await membership_repository.get(group_id, user.id)
        if m is None or m.status != MemberStatus.ACTIVE:
            raise PermissionDeniedError("You are not a member of this group", code="not_member")
        return group_id

    @staticmethod
    async def _invalidate(user_id) -> None:
        from app.services.board_service import invalidate_boards_for
        await cache.delete_prefix("dashboard:")
        await cache.delete_prefix("leaderboard:")
        await cache.delete_prefix(stats_cache_key(user_id))
        await invalidate_boards_for(user_id)

    async def submit_bulk(self, user: User, data: BulkSubmissionCreate) -> Totals:
        last: SubmissionResult | None = None
        total_count = 0
        for item in data.items:
            last = await self.submit(user, SubmissionCreate(
                group_id=data.group_id, recitation_id=item.recitation_id,
                count=item.count, client_uuid=item.client_uuid,
            ), notify=False, refresh_streak=False)
            total_count += item.count
        assert last is not None
        # Streak once for the whole batch; the totals then carry it.
        streak = await streak_service.refresh(user)
        last.totals.streak = streak.current

        # One summary push for the whole batch.
        try:
            n = len(data.items)
            await send_to_community(
                "New recitations logged",
                f"{user.display_name} logged {n} "
                f"{'recitation' if n == 1 else 'recitations'} (×{total_count}).",
            )
        except Exception:  # pragma: no cover
            pass

        return last.totals

    async def recent_for_user(
        self, user: User, limit: int = 20
    ) -> list[MySubmissionOut]:
        """The user's most recent submissions, enriched with recitation names."""
        return await self.page_for_user(user, limit, None)

    async def page_for_user(self, user: User, limit: int, before) -> list[MySubmissionOut]:
        """A page of the user's submissions, newest first (see the /me route)."""
        subs = await submission_repository.page_for_user(user.id, limit, before)
        recs = await recitation_repository.get_many({s.recitation_id for s in subs})
        out: list[MySubmissionOut] = []
        for s in subs:
            rec = recs.get(s.recitation_id)
            out.append(MySubmissionOut(
                id=s.id, recitation_id=s.recitation_id,
                recitation_name=rec.english_name if rec else "Recitation",
                urdu_name=rec.urdu_name if rec else None,
                count=s.count, created_at=s.created_at,
            ))
        return out

    async def undo(self, user: User, submission_id: uuid.UUID) -> Totals:
        submission = await submission_repository.get(submission_id)
        if submission is None:
            raise NotFoundError("Submission not found")
        if submission.user_id != user.id:
            raise PermissionDeniedError("Cannot undo another user's submission")
        window = timedelta(seconds=settings.SUBMISSION_UNDO_WINDOW_SECONDS)
        created = submission.created_at
        if created.tzinfo is None:  # stored as naive UTC by some drivers
            created = created.replace(tzinfo=timezone.utc)
        if datetime.now(timezone.utc) - created > window:
            raise ConflictError("Undo window has passed")

        # Claim the undo atomically so a double tap can't subtract twice.
        if not await claim_undo(submission.id):
            raise NotFoundError("Submission not found")
        await inc_user_lifetime(user.id, -submission.count)
        await inc_week(submission.week_id, user.id, -submission.count)
        await streak_service.refresh(user)       # undoing today's only log breaks it
        await self._invalidate(user.id)
        return await self._totals(user, submission.week_id)

    async def _result(self, user, week_id, submission) -> SubmissionResult:
        return SubmissionResult(
            submission=SubmissionOut.model_validate(submission),
            totals=await self._totals(user, week_id),
        )

    async def _totals(self, user: User, week_id) -> Totals:
        user_week = await submission_repository.sum_for_user_week(week_id, user.id)
        today = await submission_repository.sum_for_user_since_all_groups(
            user.id, _start_of_today()
        )
        week = await weekly_repository.get(week_id)
        return Totals(
            user_week=user_week,
            user_lifetime=user.lifetime_total,
            group_week=week.group_total if week else 0,
            today=today,
            streak=user.streak_days,
        )


submission_service = SubmissionService()
