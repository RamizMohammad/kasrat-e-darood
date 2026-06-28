"""Category & recitation use-cases (admin-managed library)."""
from __future__ import annotations

from beanie import PydanticObjectId

from app.core.exceptions import NotFoundError
from app.models.category import Category, Recitation
from app.models.user import User
from app.repositories.recitation_repo import (
    category_repository,
    recitation_repository,
)
from app.schemas.recitation import RecitationCreate, RecitationUpdate


class RecitationService:
    async def list_categories(self) -> list[Category]:
        return await category_repository.list_all()

    async def list_recitations(
        self, group_id: PydanticObjectId | None, q: str | None
    ) -> list[Recitation]:
        return await recitation_repository.list_for_group(group_id, q)

    async def get(self, recitation_id: PydanticObjectId) -> Recitation:
        rec = await recitation_repository.get(recitation_id)
        if rec is None:
            raise NotFoundError("Recitation not found")
        return rec

    async def create(self, data: RecitationCreate, by: User) -> Recitation:
        payload = data.model_dump()
        # Denormalize the category label so the library/stats can group by it.
        if data.category_id:
            category = await category_repository.get(data.category_id)
            if category:
                payload["category"] = category.name
        rec = Recitation(**payload, created_by=by.id)
        return await recitation_repository.create(rec)

    async def update(
        self, recitation_id: PydanticObjectId, data: RecitationUpdate, by: User
    ) -> Recitation:
        rec = await self.get(recitation_id)
        for key, value in data.model_dump(exclude_unset=True).items():
            setattr(rec, key, value)
        rec.updated_by = by.id
        return await recitation_repository.save(rec)


recitation_service = RecitationService()
