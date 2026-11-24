"""Category & recitation repositories."""
from __future__ import annotations

import uuid

from sqlalchemy import or_

from app.database.postgres import db
from app.models.category import Category, Recitation
from app.repositories.base import BaseRepository


class CategoryRepository(BaseRepository[Category]):
    def __init__(self) -> None:
        super().__init__(Category)

    async def list_all(self) -> list[Category]:
        result = await db().execute(self.live().order_by(Category.sort_order, Category.name))
        return list(result.scalars().all())

    async def get_by_slug(self, slug: str) -> Category | None:
        return await self.first(Category.slug == slug)


class RecitationRepository(BaseRepository[Recitation]):
    def __init__(self) -> None:
        super().__init__(Recitation)

    async def list_for_group(
        self, group_id: uuid.UUID | None, q: str | None = None
    ) -> list[Recitation]:
        """The global library plus the group's own recitations, optionally searched."""
        stmt = self.live().where(
            Recitation.group_id.is_(None) if group_id is None
            else or_(Recitation.group_id.is_(None), Recitation.group_id == group_id))
        if q and q.strip():
            like = "%" + q.strip().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
            stmt = stmt.where(or_(
                Recitation.english_name.ilike(like, escape="\\"),
                Recitation.arabic_name.ilike(like, escape="\\"),
                Recitation.urdu_name.ilike(like, escape="\\"),
                Recitation.transliteration.ilike(like, escape="\\"),
                Recitation.translation.ilike(like, escape="\\"),
                Recitation.category.ilike(like, escape="\\"),
            ))
        result = await db().execute(stmt.order_by(Recitation.sort_order, Recitation.english_name))
        return list(result.scalars().all())

    async def get_many(self, ids: set[uuid.UUID]) -> dict[uuid.UUID, Recitation]:
        if not ids:
            return {}
        result = await db().execute(self.live().where(Recitation.id.in_(ids)))
        return {r.id: r for r in result.scalars().all()}


category_repository = CategoryRepository()
recitation_repository = RecitationRepository()
