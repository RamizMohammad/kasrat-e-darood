"""Category and Recitation documents (admin-managed)."""
from __future__ import annotations

import pymongo
from beanie import Indexed, PydanticObjectId

from app.models.base import BaseDocument


class Category(BaseDocument):
    name: str
    slug: Indexed(str, unique=True)  # type: ignore[valid-type]
    color: str = "#004532"
    icon: str | None = None
    sort_order: int = 0

    class Settings:
        name = "categories"


class Recitation(BaseDocument):
    group_id: PydanticObjectId | None = None  # None => global library
    arabic_name: str
    english_name: str
    translation: str | None = None
    category_id: PydanticObjectId | None = None
    description: str | None = None
    benefits: str | None = None
    reference: str | None = None
    color: str = "#004532"
    icon: str | None = None
    sort_order: int = 0
    visibility: str = "public"
    default_increment: int = 1

    class Settings:
        name = "recitations"
        indexes = [
            [("group_id", pymongo.ASCENDING), ("sort_order", pymongo.ASCENDING)],
            [("arabic_name", pymongo.TEXT), ("english_name", pymongo.TEXT),
             ("translation", pymongo.TEXT)],
        ]
