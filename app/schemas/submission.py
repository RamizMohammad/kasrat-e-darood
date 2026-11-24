"""Submission schemas."""
from __future__ import annotations

from datetime import datetime

import uuid
from pydantic import BaseModel, Field

from app.schemas.common import ORMModel


class SubmissionCreate(BaseModel):
    group_id: uuid.UUID
    recitation_id: uuid.UUID
    count: int = Field(gt=0, le=1_000_000)
    note: str | None = Field(default=None, max_length=500)
    client_uuid: str | None = None


class BulkItem(BaseModel):
    recitation_id: uuid.UUID
    count: int = Field(gt=0, le=1_000_000)
    client_uuid: str | None = None


class BulkSubmissionCreate(BaseModel):
    # Optional: when omitted, the shared community group is used.
    group_id: uuid.UUID | None = None
    items: list[BulkItem] = Field(min_length=1, max_length=200)


class Totals(BaseModel):
    user_week: int
    user_lifetime: int
    group_week: int
    today: int
    # Your current streak (days in a row with a log, IST) after this change.
    streak: int = 0


class SubmissionOut(ORMModel):
    id: uuid.UUID
    group_id: uuid.UUID
    week_id: uuid.UUID
    user_id: uuid.UUID
    recitation_id: uuid.UUID
    count: int
    note: str | None = None


class SubmissionResult(BaseModel):
    submission: SubmissionOut
    totals: Totals


class MySubmissionOut(BaseModel):
    """A user's own submission, enriched with the recitation's display names."""
    id: uuid.UUID
    recitation_id: uuid.UUID
    recitation_name: str
    urdu_name: str | None = None
    count: int
    created_at: datetime
