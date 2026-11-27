"""Category & recitation schemas."""
from __future__ import annotations

import uuid
from pydantic import BaseModel, Field

from app.schemas.common import ORMModel


class CategoryOut(ORMModel):
    id: uuid.UUID
    name: str
    slug: str
    color: str
    icon: str | None = None


# The app parses colour as #RRGGBB / #AARRGGBB; anything else could crash it.
_COLOR = r"^#([0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})$"
_ICON = r"^[a-z0-9_]{1,64}$"


class RecitationCreate(BaseModel):
    arabic_name: str = Field(min_length=1, max_length=20_000)
    english_name: str = Field(min_length=1, max_length=200)
    urdu_name: str | None = Field(default=None, max_length=200)
    transliteration: str | None = Field(default=None, max_length=20_000)
    translation: str | None = Field(default=None, max_length=20_000)
    category_id: uuid.UUID | None = None
    group_id: uuid.UUID | None = None
    description: str | None = Field(default=None, max_length=5_000)
    benefits: str | None = Field(default=None, max_length=5_000)
    reference: str | None = Field(default=None, max_length=300)
    color: str = Field(default="#004532", pattern=_COLOR)
    icon: str | None = Field(default=None, pattern=_ICON)
    sort_order: int = Field(default=0, ge=-100_000, le=100_000)
    default_increment: int = Field(default=1, ge=1, le=100_000)


class RecitationUpdate(BaseModel):
    arabic_name: str | None = Field(default=None, min_length=1, max_length=20_000)
    english_name: str | None = Field(default=None, min_length=1, max_length=200)
    translation: str | None = Field(default=None, max_length=20_000)
    description: str | None = Field(default=None, max_length=5_000)
    benefits: str | None = Field(default=None, max_length=5_000)
    color: str | None = Field(default=None, pattern=_COLOR)
    icon: str | None = Field(default=None, pattern=_ICON)
    sort_order: int | None = Field(default=None, ge=-100_000, le=100_000)


class RecitationOut(ORMModel):
    id: uuid.UUID
    group_id: uuid.UUID | None = None
    arabic_name: str
    english_name: str
    urdu_name: str | None = None
    transliteration: str | None = None
    category: str | None = None
    translation: str | None = None
    category_id: uuid.UUID | None = None
    description: str | None = None
    reference: str | None = None
    color: str
    icon: str | None = None
    sort_order: int
    default_increment: int
