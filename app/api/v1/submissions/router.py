"""Submission endpoints (core write path)."""
from __future__ import annotations

from beanie import PydanticObjectId
from fastapi import APIRouter, Depends

from app.dependencies.auth import get_current_user
from app.models.user import User
from app.schemas.submission import (
    BulkSubmissionCreate,
    SubmissionCreate,
    SubmissionResult,
    Totals,
)
from app.services.submission_service import submission_service

router = APIRouter(prefix="/submissions", tags=["submissions"])


@router.post("", response_model=SubmissionResult)
async def create_submission(
    body: SubmissionCreate, user: User = Depends(get_current_user)
) -> SubmissionResult:
    """Submit a recitation count; returns updated personal & group totals."""
    return await submission_service.submit(user, body)


@router.post("/bulk", response_model=Totals)
async def create_bulk(
    body: BulkSubmissionCreate, user: User = Depends(get_current_user)
) -> Totals:
    return await submission_service.submit_bulk(user, body)


@router.delete("/{submission_id}", response_model=Totals)
async def undo_submission(
    submission_id: PydanticObjectId, user: User = Depends(get_current_user)
) -> Totals:
    return await submission_service.undo(user, submission_id)
