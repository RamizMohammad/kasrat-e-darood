"""Paging helpers: the next page's cursor travels in the X-Next-Cursor header."""
from __future__ import annotations

from fastapi import Response

from app.core.exceptions import ValidationAppError


def offset_of(cursor: str | None) -> int:
    if not cursor:
        return 0
    try:
        n = int(cursor)
    except ValueError:
        raise ValidationAppError("Invalid cursor", code="invalid_cursor") from None
    if n < 0:
        raise ValidationAppError("Invalid cursor", code="invalid_cursor")
    return n


def set_next(response: Response, next_cursor) -> None:
    if next_cursor is not None:
        response.headers["X-Next-Cursor"] = str(next_cursor)


def check_keyset(cursor: str | None) -> None:
    from app.repositories.base import decode_cursor
    if cursor and decode_cursor(cursor) is None:
        raise ValidationAppError("Invalid cursor", code="invalid_cursor")
