"""FCM device tokens."""
from __future__ import annotations

import uuid

from sqlalchemy import select

from app.database.postgres import db
from app.models.activity import DeviceToken
from app.repositories.base import BaseRepository


class DeviceRepository(BaseRepository[DeviceToken]):
    def __init__(self) -> None:
        super().__init__(DeviceToken)

    async def by_token(self, token: str) -> DeviceToken | None:
        """Including soft-deleted rows (the token column is globally unique)."""
        stmt = select(DeviceToken).where(DeviceToken.fcm_token == token).limit(1)
        return (await db().execute(stmt)).scalars().first()

    async def tokens_for(self, user_ids: list[uuid.UUID] | set[uuid.UUID]) -> dict[uuid.UUID, list[str]]:
        if not user_ids:
            return {}
        stmt = select(DeviceToken.user_id, DeviceToken.fcm_token).where(
            DeviceToken.deleted.is_(False), DeviceToken.user_id.in_(list(user_ids)))
        out: dict[uuid.UUID, list[str]] = {}
        for uid, tok in (await db().execute(stmt)).all():
            out.setdefault(uid, []).append(tok)
        return out


device_repository = DeviceRepository()
