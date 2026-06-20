"""Category & recitation repositories."""
from __future__ import annotations

from beanie import PydanticObjectId

from app.models.category import Category, Recitation
from app.repositories.base import BaseRepository


class CategoryRepository(BaseRepository[Category]):
    def __init__(self) -> None:
        super().__init__(Category)

    async def list_all(self) -> list[Category]:
        return await Category.find(Category.deleted == False).sort(  # noqa: E712
            "+sort_order"
        ).to_list()


class RecitationRepository(BaseRepository[Recitation]):
    def __init__(self) -> None:
        super().__init__(Recitation)

    async def list_for_group(
        self, group_id: PydanticObjectId | None, q: str | None = None
    ) -> list[Recitation]:
        query: dict = {"deleted": False, "group_id": {"$in": [group_id, None]}}
        if q:
            query["$text"] = {"$search": q}
        return await Recitation.find(query).sort("+sort_order").to_list()


category_repository = CategoryRepository()
recitation_repository = RecitationRepository()
