"""Generic repository implementing soft-delete-aware CRUD over Beanie documents."""
from __future__ import annotations

from typing import Generic, Type, TypeVar

from beanie import PydanticObjectId

from app.models.base import BaseDocument

TDoc = TypeVar("TDoc", bound=BaseDocument)


class BaseRepository(Generic[TDoc]):
    """Base class for repositories. Encapsulates all MongoDB access for a model."""

    model: Type[TDoc]

    def __init__(self, model: Type[TDoc]) -> None:
        self.model = model

    async def get(self, doc_id: PydanticObjectId) -> TDoc | None:
        doc = await self.model.get(doc_id)
        if doc is None or doc.deleted:
            return None
        return doc

    async def create(self, doc: TDoc) -> TDoc:
        await doc.insert()
        return doc

    async def save(self, doc: TDoc) -> TDoc:
        doc.touch()
        await doc.save()
        return doc

    async def soft_delete(self, doc: TDoc) -> None:
        doc.deleted = True
        doc.touch()
        await doc.save()

    async def list_paginated(
        self,
        *filters,
        cursor: PydanticObjectId | None = None,
        limit: int = 20,
        sort: str = "-_id",
    ) -> tuple[list[TDoc], str | None]:
        """Cursor pagination by ``_id``. Returns (items, next_cursor)."""
        query = [self.model.deleted == False, *filters]  # noqa: E712
        if cursor is not None:
            query.append(self.model.id < cursor)
        items = await self.model.find(*query).sort(sort).limit(limit + 1).to_list()
        next_cursor = None
        if len(items) > limit:
            next_cursor = str(items[limit - 1].id)
            items = items[:limit]
        return items, next_cursor
