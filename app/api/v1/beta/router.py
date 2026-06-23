"""Public beta-program endpoints (no authentication)."""
from __future__ import annotations

from fastapi import APIRouter, BackgroundTasks, status

from app.schemas.beta import (
    BetaSignupCreate,
    BetaSignupOut,
    BetaSignupResult,
    BetaStats,
)
from app.services.beta_service import beta_service
from app.services.email_service import email_service

router = APIRouter(prefix="/beta", tags=["beta"])


@router.post("/signup", response_model=BetaSignupResult,
             status_code=status.HTTP_201_CREATED)
async def signup(body: BetaSignupCreate, background: BackgroundTasks) -> BetaSignupResult:
    """Register interest for the closed/open beta. Idempotent by email.

    A branded welcome email is sent (in the background) only for brand-new,
    non-honeypot signups — never for duplicates or bot submissions.
    """
    signup, already = await beta_service.signup(body)

    if not already and not body.website:
        background.add_task(
            email_service.send_beta_welcome,
            to_email=signup.email,
            full_name=signup.full_name,
            lang=signup.lang,
        )

    return BetaSignupResult(
        ok=True,
        already_registered=already,
        signup=BetaSignupOut.model_validate(signup),
    )


@router.get("/stats", response_model=BetaStats)
async def stats() -> BetaStats:
    """Public counters (e.g. to show 'N testers joined' on the landing page)."""
    return await beta_service.stats()
