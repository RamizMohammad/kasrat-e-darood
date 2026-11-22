"""The core submission use-case: store a recitation and roll up all totals."""
from __future__ import annotations

from datetime import datetime, timedelta, timezone

from beanie import PydanticObjectId
from pymongo.errors import DuplicateKeyError

from app.core.exceptions import ConflictError, NotFoundError, PermissionDeniedError
from app.database.redis import cache
from app.models.activity import ActivityItem
from app.models.submission import Submission
from app.models.user import User
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
from app.services.push_service import send_to_community


def _start_of_today() -> datetime:
    now = datetime.now(timezone.utc)
    return now.replace(hour=0, minute=0, second=0, microsecond=0)


class SubmissionService:
    async def submit(self, user: User, data: SubmissionCreate,
                     notify: bool = True) -> SubmissionResult:
        week = await weekly_repository.get_active(data.group_id)
        if week is None:
            raise NotFoundError("No active week for this group")

        # Idempotency: a repeated client_uuid returns the existing result.
        if data.client_uuid:
            existing = await submission_repository.find_by_client_uuid(
                user.id, data.client_uuid
            )
            if existing is not None:
                return await self._result(user, week.id, data.group_id, existing)

        recitation = await recitation_repository.get(data.recitation_id)
        if recitation is None:
            raise NotFoundError("Recitation not found")

        submission = Submission(
            group_id=data.group_id, week_id=week.id, user_id=user.id,
            recitation_id=data.recitation_id, count=data.count, note=data.note,
            client_uuid=data.client_uuid, created_by=user.id,
        )
        try:
            await submission_repository.create(submission)
        except DuplicateKeyError:
            # Two requests with the same client_uuid raced past the check
            # above; the other one won. Return its result, count nothing.
            existing = await submission_repository.find_by_client_uuid(
                user.id, data.client_uuid or ""
            )
            if existing is None:
                raise
            return await self._result(user, week.id, data.group_id, existing)

        # Roll-ups: atomic server-side increments (see repositories/counters).
        user.lifetime_total = await inc_user_lifetime(user.id, data.count)
        await inc_week(week.id, user.id, data.count)

        await ActivityItem(
            group_id=data.group_id, actor_id=user.id, type="submission.created",
            text=f"{user.display_name} completed {recitation.english_name} ×{data.count}",
            payload={"recitation_id": str(recitation.id), "count": data.count},
            created_by=user.id,
        ).insert()

        # Invalidate cached read-models for this group.
        await cache.delete_prefix(f"dashboard:{data.group_id}")
        await cache.delete_prefix(f"leaderboard:{data.group_id}")

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

        return await self._result(user, week.id, data.group_id, submission)

    async def submit_bulk(self, user: User, data: BulkSubmissionCreate) -> Totals:
        # Default to the shared community group when no group is specified.
        from app.services.community_service import community_service
        group_id = await community_service.resolve_group(user, data.group_id)

        last: SubmissionResult | None = None
        total_count = 0
        for item in data.items:
            last = await self.submit(user, SubmissionCreate(
                group_id=group_id, recitation_id=item.recitation_id,
                count=item.count, client_uuid=item.client_uuid,
            ), notify=False)
            total_count += item.count
        assert last is not None

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
        subs = await submission_repository.recent_for_user(user.id, limit)
        out: list[MySubmissionOut] = []
        names: dict = {}
        for s in subs:
            cached = names.get(s.recitation_id)
            if cached is None:
                rec = await recitation_repository.get(s.recitation_id)
                cached = (rec.english_name if rec else "Recitation",
                          rec.urdu_name if rec else None)
                names[s.recitation_id] = cached
            out.append(MySubmissionOut(
                id=s.id, recitation_id=s.recitation_id,
                recitation_name=cached[0], urdu_name=cached[1],
                count=s.count, created_at=s.created_at,
            ))
        return out

    async def undo(self, user: User, submission_id: PydanticObjectId) -> Totals:
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
        user.lifetime_total = await inc_user_lifetime(user.id, -submission.count)
        await inc_week(submission.week_id, user.id, -submission.count)
        await cache.delete_prefix(f"dashboard:{submission.group_id}")
        await cache.delete_prefix(f"leaderboard:{submission.group_id}")
        return await self._totals(user, submission.week_id, submission.group_id)

    async def _result(self, user, week_id, group_id, submission) -> SubmissionResult:
        return SubmissionResult(
            submission=SubmissionOut.model_validate(submission),
            totals=await self._totals(user, week_id, group_id),
        )

    async def _totals(self, user: User, week_id, group_id) -> Totals:
        user_week = await submission_repository.sum_for_user_week(week_id, user.id)
        today = await submission_repository.sum_for_user_since(
            user.id, group_id, _start_of_today()
        )
        week = await weekly_repository.get(week_id)
        return Totals(
            user_week=user_week,
            user_lifetime=user.lifetime_total,
            group_week=week.group_total if week else 0,
            today=today,
        )


submission_service = SubmissionService()
