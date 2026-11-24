"""Refresh tokens and one-time codes."""
from __future__ import annotations

import uuid

from sqlalchemy import delete, update

from app.database.postgres import db
from app.models.activity import PasswordResetCode, RefreshToken
from app.models.base import utcnow
from app.repositories.base import BaseRepository


class RefreshTokenRepository(BaseRepository[RefreshToken]):
    def __init__(self) -> None:
        super().__init__(RefreshToken)

    async def get_live(self, token_hash: str) -> RefreshToken | None:
        return await self.first(RefreshToken.token_hash == token_hash,
                                RefreshToken.revoked.is_(False))

    async def get_any(self, token_hash: str) -> RefreshToken | None:
        return await self.first(RefreshToken.token_hash == token_hash)

    async def revoke_all_for(self, user_id: uuid.UUID) -> None:
        await db().execute(
            update(RefreshToken)
            .where(RefreshToken.user_id == user_id, RefreshToken.revoked.is_(False))
            .values(revoked=True, updated_at=utcnow())
            .execution_options(synchronize_session=False))

    async def purge_expired(self) -> int:
        result = await db().execute(delete(RefreshToken).where(RefreshToken.expires_at < utcnow()))
        return result.rowcount or 0


class OneTimeCodeRepository(BaseRepository[PasswordResetCode]):
    def __init__(self) -> None:
        super().__init__(PasswordResetCode)

    async def latest_unused(self, email: str, purpose: str) -> PasswordResetCode | None:
        stmt = (self.live().where(PasswordResetCode.email == email,
                                  PasswordResetCode.purpose == purpose,
                                  PasswordResetCode.used.is_(False))
                .order_by(PasswordResetCode.created_at.desc()).limit(1))
        return (await db().execute(stmt)).scalars().first()

    async def purge_expired(self) -> int:
        result = await db().execute(
            delete(PasswordResetCode).where(PasswordResetCode.expires_at < utcnow()))
        return result.rowcount or 0


refresh_token_repository = RefreshTokenRepository()
otp_repository = OneTimeCodeRepository()
