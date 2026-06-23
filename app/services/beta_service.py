"""Beta program use-cases: public signup + simple stats."""
from __future__ import annotations

from beanie import PydanticObjectId

from app.models.beta import BetaSignup, BetaTrack
from app.repositories.beta_repo import beta_repository
from app.schemas.beta import BetaSignupCreate, BetaStats


class BetaService:
    async def signup(self, data: BetaSignupCreate) -> tuple[BetaSignup, bool]:
        """Register a beta tester. Idempotent by email.

        Returns (signup, already_registered).
        """
        # Honeypot: silently accept (so bots think they succeeded) but skip
        # persistence when the hidden field is filled.
        if data.website:
            decoy = BetaSignup(
                full_name=data.full_name, email=data.email,
                track=BetaTrack(data.track),
            )
            decoy.id = PydanticObjectId()  # not persisted; just satisfies the response
            return decoy, False

        existing = await beta_repository.get_by_email(data.email)
        if existing is not None:
            return existing, True

        signup = BetaSignup(
            full_name=data.full_name,
            email=data.email,
            google_email=data.google_email,
            phone=data.phone,
            country=data.country,
            device=data.device,
            how_heard=data.how_heard,
            reason=data.reason,
            track=BetaTrack(data.track),
            consent=data.consent,
        )
        await beta_repository.create(signup)
        return signup, False

    async def stats(self) -> BetaStats:
        return BetaStats(
            total=await beta_repository.count(),
            closed=await beta_repository.count(BetaTrack.CLOSED),
            open=await beta_repository.count(BetaTrack.OPEN),
        )


beta_service = BetaService()
