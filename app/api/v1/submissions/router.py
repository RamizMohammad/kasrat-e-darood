"""Submission endpoints (core write path)."""
from __future__ import annotations

import uuid
from fastapi import APIRouter, Depends, Query, Response

from app.core.exceptions import ValidationAppError
from app.repositories.base import decode_cursor, encode_cursor

from app.dependencies.auth import get_current_user
from app.models.user import User
from app.schemas.submission import (
    BulkSubmissionCreate,
    MySubmissionOut,
    SubmissionCreate,
    SubmissionResult,
    Totals,
)
from app.services.submission_service import submission_service

router = APIRouter(prefix="/submissions", tags=["submissions"])


@router.get("/me", response_model=list[MySubmissionOut])
async def my_submissions(
    response: Response,
    limit: int = Query(default=20, ge=1, le=100),
    before: str | None = Query(
        default=None,
        description='Next page: the X-Next-Cursor header of the previous page '
                    '(or "<created_at>|<id>" of the last item you have).'),
    user: User = Depends(get_current_user),
) -> list[MySubmissionOut]:
    """Your submissions, newest first, ``limit`` at a time.

    When there may be more, the response carries an ``X-Next-Cursor`` header;
    pass it back as ``?before=`` for the next page.
    """
    cursor = None
    if before:
        cursor = decode_cursor(before)
        if cursor is None:
            raise ValidationAppError("Invalid cursor", code="invalid_cursor")
    items = await submission_service.page_for_user(user, limit, cursor)
    if len(items) == limit:
        response.headers["X-Next-Cursor"] = encode_cursor(items[-1].created_at, items[-1].id)
    return items


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
    submission_id: uuid.UUID, user: User = Depends(get_current_user)
) -> Totals:
    return await submission_service.undo(user, submission_id)
