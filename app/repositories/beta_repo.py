"""Beta signup repository."""
from __future__ import annotations

from app.models.beta import BetaSignup, BetaTrack
from app.repositories.base import BaseRepository


class BetaRepository(BaseRepository[BetaSignup]):
    def __init__(self) -> None:
        super().__init__(BetaSignup)

    async def get_by_email(self, email: str) -> BetaSignup | None:
        return await BetaSignup.find_one(
            BetaSignup.email == email, BetaSignup.deleted == False  # noqa: E712
        )

    async def count(self, track: BetaTrack | None = None) -> int:
        query = {"deleted": False}
        if track is not None:
            query["track"] = track.value
        return await BetaSignup.find(query).count()


beta_repository = BetaRepository()
