"""Public beta-program endpoints (no authentication)."""
from __future__ import annotations

from fastapi import APIRouter, status

from app.schemas.beta import (
    BetaSignupCreate,
    BetaSignupOut,
    BetaSignupResult,
    BetaStats,
)
from app.services.beta_service import beta_service

router = APIRouter(prefix="/beta", tags=["beta"])


@router.post("/signup", response_model=BetaSignupResult,
             status_code=status.HTTP_201_CREATED)
async def signup(body: BetaSignupCreate) -> BetaSignupResult:
    """Register interest for the closed/open beta. Idempotent by email."""
    signup, already = await beta_service.signup(body)
    return BetaSignupResult(
        ok=True,
        already_registered=already,
        signup=BetaSignupOut.model_validate(signup),
    )


@router.get("/stats", response_model=BetaStats)
async def stats() -> BetaStats:
    """Public counters (e.g. to show 'N testers joined' on the landing page)."""
    return await beta_service.stats()
