"""Beta signup repository."""
from __future__ import annotations

from sqlalchemy import func, select

from app.database.postgres import db
from app.models.beta import BetaSignup, BetaTrack
from app.repositories.base import BaseRepository


class BetaRepository(BaseRepository[BetaSignup]):
    def __init__(self) -> None:
        super().__init__(BetaSignup)

    async def get_by_email(self, email: str) -> BetaSignup | None:
        return await self.first(BetaSignup.email == email)

    async def count(self, track: BetaTrack | None = None) -> int:
        stmt = select(func.count()).select_from(BetaSignup).where(BetaSignup.deleted.is_(False))
        if track is not None:
            stmt = stmt.where(BetaSignup.track == track)
        return int((await db().execute(stmt)).scalar_one())


beta_repository = BetaRepository()
