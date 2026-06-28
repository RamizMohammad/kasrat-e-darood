"""Category & recitation schemas."""
from __future__ import annotations

from beanie import PydanticObjectId
from pydantic import BaseModel, Field

from app.schemas.common import ORMModel


class CategoryOut(ORMModel):
    id: PydanticObjectId
    name: str
    slug: str
    color: str
    icon: str | None = None


class RecitationCreate(BaseModel):
    arabic_name: str
    english_name: str
    urdu_name: str | None = None
    transliteration: str | None = None
    translation: str | None = None
    category_id: PydanticObjectId | None = None
    group_id: PydanticObjectId | None = None
    description: str | None = None
    benefits: str | None = None
    reference: str | None = None
    color: str = "#004532"
    icon: str | None = None
    sort_order: int = 0
    default_increment: int = Field(default=1, ge=1)


class RecitationUpdate(BaseModel):
    arabic_name: str | None = None
    english_name: str | None = None
    translation: str | None = None
    description: str | None = None
    benefits: str | None = None
    color: str | None = None
    icon: str | None = None
    sort_order: int | None = None


class RecitationOut(ORMModel):
    id: PydanticObjectId
    group_id: PydanticObjectId | None = None
    arabic_name: str
    english_name: str
    urdu_name: str | None = None
    transliteration: str | None = None
    category: str | None = None
    translation: str | None = None
    category_id: PydanticObjectId | None = None
    description: str | None = None
    reference: str | None = None
    color: str
    icon: str | None = None
    sort_order: int
    default_increment: int
