"""Base Beanie document with audit fields, soft delete and optimistic locking."""
from __future__ import annotations

from datetime import datetime, timezone

from beanie import Document, PydanticObjectId
from pydantic import Field


def utcnow() -> datetime:
    return datetime.now(timezone.utc)


class BaseDocument(Document):
    """Shared base for every collection.

    Provides the audit/soft-delete/version fields mandated by the data model.
    Concrete documents subclass this and add their own fields + Settings.
    """

    created_at: datetime = Field(default_factory=utcnow)
    updated_at: datetime = Field(default_factory=utcnow)
    created_by: PydanticObjectId | None = None
    updated_by: PydanticObjectId | None = None
    deleted: bool = False
    version: int = 1

    def touch(self, by: PydanticObjectId | None = None) -> None:
        """Update audit fields prior to a write."""
        self.updated_at = utcnow()
        self.version += 1
        if by is not None:
            self.updated_by = by
