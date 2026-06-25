"""Submission repository with aggregation helpers."""
from __future__ import annotations

from datetime import datetime

from beanie import PydanticObjectId

from app.models.submission import Submission
from app.repositories.base import BaseRepository


class SubmissionRepository(BaseRepository[Submission]):
    def __init__(self) -> None:
        super().__init__(Submission)

    async def recent_for_user(
        self, user_id: PydanticObjectId, limit: int = 20
    ) -> list[Submission]:
        return await Submission.find(
            Submission.user_id == user_id,
            Submission.deleted == False,  # noqa: E712
        ).sort("-created_at").limit(limit).to_list()

    async def find_by_client_uuid(
        self, user_id: PydanticObjectId, client_uuid: str
    ) -> Submission | None:
        return await Submission.find_one(
            Submission.user_id == user_id,
            Submission.client_uuid == client_uuid,
            Submission.deleted == False,  # noqa: E712
        )

    async def sum_for_user_week(
        self, week_id: PydanticObjectId, user_id: PydanticObjectId
    ) -> int:
        pipeline = [
            {"$match": {"week_id": week_id, "user_id": user_id, "deleted": False}},
            {"$group": {"_id": None, "total": {"$sum": "$count"}}},
        ]
        res = await Submission.aggregate(pipeline).to_list()
        return int(res[0]["total"]) if res else 0

    async def sum_for_user_since(
        self, user_id: PydanticObjectId, group_id: PydanticObjectId, since: datetime
    ) -> int:
        pipeline = [
            {"$match": {"user_id": user_id, "group_id": group_id,
                        "deleted": False, "created_at": {"$gte": since}}},
            {"$group": {"_id": None, "total": {"$sum": "$count"}}},
        ]
        res = await Submission.aggregate(pipeline).to_list()
        return int(res[0]["total"]) if res else 0

    async def leaderboard_for_week(self, week_id: PydanticObjectId) -> list[dict]:
        """Return [{_id: user_id, total: n}] sorted desc."""
        pipeline = [
            {"$match": {"week_id": week_id, "deleted": False}},
            {"$group": {"_id": "$user_id", "total": {"$sum": "$count"}}},
            {"$sort": {"total": -1}},
        ]
        return await Submission.aggregate(pipeline).to_list()

    async def daily_chart(
        self, week_id: PydanticObjectId, user_id: PydanticObjectId
    ) -> list[dict]:
        pipeline = [
            {"$match": {"week_id": week_id, "user_id": user_id, "deleted": False}},
            {"$group": {
                "_id": {"$dayOfWeek": "$created_at"},
                "value": {"$sum": "$count"}}},
        ]
        return await Submission.aggregate(pipeline).to_list()


submission_repository = SubmissionRepository()
