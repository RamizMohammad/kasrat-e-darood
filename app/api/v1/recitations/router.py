"""Category & recitation endpoints."""
from __future__ import annotations

import uuid
import hashlib

from fastapi import APIRouter, Depends, Query, Request, Response, status

from app.core.exceptions import PermissionDeniedError
from app.dependencies.auth import get_current_user
from app.models.user import GlobalRole, User
from app.schemas.recitation import (
    CategoryOut,
    RecitationCreate,
    RecitationOut,
    RecitationUpdate,
)

_LIBRARY_MANAGER_ROLES = {GlobalRole.SUPER_MEMBER, GlobalRole.SUPER_ADMIN}
from app.services.recitation_service import recitation_service

router = APIRouter(tags=["recitations"])


@router.get("/categories", response_model=list[CategoryOut])
async def list_categories(_: User = Depends(get_current_user)) -> list[CategoryOut]:
    cats = await recitation_service.list_categories()
    return [CategoryOut.model_validate(c) for c in cats]


@router.get("/recitations", response_model=list[RecitationOut])
async def list_recitations(
    request: Request,
    response: Response,
    group_id: uuid.UUID | None = None,
    q: str | None = Query(default=None, max_length=80),
    _: User = Depends(get_current_user),
):
    """The library. Sends an ETag; a client that already has this version
    (If-None-Match) gets 304 and no body, so the app doesn't download the
    whole library on every launch."""
    recs = await recitation_service.list_recitations(group_id, q)
    etag = _library_etag(recs, group_id, q)
    if etag in _if_none_match(request):
        return Response(status_code=304, headers={"ETag": etag})
    response.headers["ETag"] = etag
    return [RecitationOut.model_validate(r) for r in recs]


def _library_etag(recs, group_id, q) -> str:
    """Changes whenever a recitation is added, edited, removed or re-sorted."""
    h = hashlib.sha256()
    h.update(f"v1|{group_id}|{q or ''}|{len(recs)}".encode())
    for r in recs:
        h.update(f"|{r.id}:{r.updated_at.isoformat() if r.updated_at else ''}".encode())
    return 'W/"' + h.hexdigest()[:32] + '"'


def _if_none_match(request: Request) -> set[str]:
    raw = request.headers.get("if-none-match", "")
    return {t.strip() for t in raw.split(",") if t.strip()}


@router.get("/recitations/{recitation_id}", response_model=RecitationOut)
async def get_recitation(
    recitation_id: uuid.UUID, _: User = Depends(get_current_user)
) -> RecitationOut:
    return RecitationOut.model_validate(await recitation_service.get(recitation_id))


@router.post("/recitations", response_model=RecitationOut,
             status_code=status.HTTP_201_CREATED)
async def create_recitation(
    body: RecitationCreate, user: User = Depends(get_current_user)
) -> RecitationOut:
    if user.role not in _LIBRARY_MANAGER_ROLES:
        raise PermissionDeniedError("Only super members or admins can add recitations")
    return RecitationOut.model_validate(await recitation_service.create(body, user))


@router.patch("/recitations/{recitation_id}", response_model=RecitationOut)
async def update_recitation(
    recitation_id: uuid.UUID, body: RecitationUpdate,
    user: User = Depends(get_current_user),
) -> RecitationOut:
    if user.role not in _LIBRARY_MANAGER_ROLES:
        raise PermissionDeniedError("Only super members or admins can edit recitations")
    return RecitationOut.model_validate(
        await recitation_service.update(recitation_id, body, user)
    )
