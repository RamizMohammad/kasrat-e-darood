"""The core submission use-case: store a recitation and roll up all totals."""
from __future__ import annotations

from datetime import datetime, timedelta, timezone

from beanie import PydanticObjectId

from app.core.exceptions import ConflictError, NotFoundError, PermissionDeniedError
from app.database.redis import cache
from app.models.activity import ActivityItem
from app.models.submission import Submission
from app.models.user import User
from app.repositories.recitation_repo import recitation_repository
from app.repositories.submission_repo import submission_repository
from app.repositories.user_repo import user_repository
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
        await submission_repository.create(submission)

        # Roll-ups
        user.lifetime_total += data.count
        await user_repository.save(user)
        week.group_total += data.count
        week.totals[str(user.id)] = week.totals.get(str(user.id), 0) + data.count
        await weekly_repository.save(week)

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
        if datetime.now(timezone.utc) - submission.created_at > window:
            raise ConflictError("Undo window has passed")

        week = await weekly_repository.get(submission.week_id)
        await submission_repository.soft_delete(submission)
        user.lifetime_total = max(0, user.lifetime_total - submission.count)
        await user_repository.save(user)
        if week is not None:
            week.group_total = max(0, week.group_total - submission.count)
            key = str(user.id)
            week.totals[key] = max(0, week.totals.get(key, 0) - submission.count)
            await weekly_repository.save(week)
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
