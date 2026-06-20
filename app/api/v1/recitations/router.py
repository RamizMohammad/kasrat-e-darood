"""Category & recitation endpoints."""
from __future__ import annotations

from beanie import PydanticObjectId
from fastapi import APIRouter, Depends, Query, status

from app.dependencies.auth import get_current_user
from app.models.user import User
from app.schemas.recitation import (
    CategoryOut,
    RecitationCreate,
    RecitationOut,
    RecitationUpdate,
)
from app.services.recitation_service import recitation_service

router = APIRouter(tags=["recitations"])


@router.get("/categories", response_model=list[CategoryOut])
async def list_categories(_: User = Depends(get_current_user)) -> list[CategoryOut]:
    cats = await recitation_service.list_categories()
    return [CategoryOut.model_validate(c) for c in cats]


@router.get("/recitations", response_model=list[RecitationOut])
async def list_recitations(
    group_id: PydanticObjectId | None = None,
    q: str | None = Query(default=None),
    _: User = Depends(get_current_user),
) -> list[RecitationOut]:
    recs = await recitation_service.list_recitations(group_id, q)
    return [RecitationOut.model_validate(r) for r in recs]


@router.get("/recitations/{recitation_id}", response_model=RecitationOut)
async def get_recitation(
    recitation_id: PydanticObjectId, _: User = Depends(get_current_user)
) -> RecitationOut:
    return RecitationOut.model_validate(await recitation_service.get(recitation_id))


@router.post("/recitations", response_model=RecitationOut,
             status_code=status.HTTP_201_CREATED)
async def create_recitation(
    body: RecitationCreate, user: User = Depends(get_current_user)
) -> RecitationOut:
    return RecitationOut.model_validate(await recitation_service.create(body, user))


@router.patch("/recitations/{recitation_id}", response_model=RecitationOut)
async def update_recitation(
    recitation_id: PydanticObjectId, body: RecitationUpdate,
    user: User = Depends(get_current_user),
) -> RecitationOut:
    return RecitationOut.model_validate(
        await recitation_service.update(recitation_id, body, user)
    )
