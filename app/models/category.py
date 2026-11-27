"""Categories and recitations (admin-managed library)."""
from __future__ import annotations

import uuid

from sqlalchemy import Index, Integer, String, Text, text as sql_text
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import AuditMixin, Base, uuid_col


class Category(AuditMixin, Base):
    __tablename__ = "categories"

    name: Mapped[str] = mapped_column(String(80), nullable=False)
    slug: Mapped[str] = mapped_column(String(80), unique=True, nullable=False)
    color: Mapped[str] = mapped_column(String(16), default="#004532", server_default="#004532", nullable=False)
    icon: Mapped[str | None] = mapped_column(String(64))
    sort_order: Mapped[int] = mapped_column(Integer, default=0, server_default="0", nullable=False)


class Recitation(AuditMixin, Base):
    __tablename__ = "recitations"

    group_id: Mapped[uuid.UUID | None] = uuid_col(nullable=True, fk="groups.id")  # None = global library
    arabic_name: Mapped[str] = mapped_column(Text, nullable=False)
    english_name: Mapped[str] = mapped_column(String(200), nullable=False)
    urdu_name: Mapped[str | None] = mapped_column(String(200))
    transliteration: Mapped[str | None] = mapped_column(Text)
    category: Mapped[str | None] = mapped_column(String(80))  # denormalised label (Surah, Zikr…)
    translation: Mapped[str | None] = mapped_column(Text)
    category_id: Mapped[uuid.UUID | None] = uuid_col(nullable=True, fk="categories.id")
    description: Mapped[str | None] = mapped_column(Text)
    benefits: Mapped[str | None] = mapped_column(Text)
    reference: Mapped[str | None] = mapped_column(String(300))
    color: Mapped[str] = mapped_column(String(16), default="#004532", server_default="#004532", nullable=False)
    icon: Mapped[str | None] = mapped_column(String(64))
    sort_order: Mapped[int] = mapped_column(Integer, default=0, server_default="0", nullable=False)
    visibility: Mapped[str] = mapped_column(String(16), default="public", server_default="public", nullable=False)
    default_increment: Mapped[int] = mapped_column(Integer, default=1, server_default="1", nullable=False)
    # Where an imported row came from ("quran:36", "hisn:75", "curated:kalima-2"…);
    # scripts_import_library uses it to never insert the same item twice.
    source_key: Mapped[str | None] = mapped_column(String(120))

    __table_args__ = (
        Index("ix_recitations_group_sort", "group_id", "sort_order"),
        Index("uq_recitations_source_key", "source_key", unique=True,
              postgresql_where=sql_text("source_key IS NOT NULL AND deleted = false")),
    )
