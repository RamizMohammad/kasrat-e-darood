"""Aggregate all v1 routers under the API prefix."""
from __future__ import annotations

from fastapi import APIRouter

from app.api.v1.auth.router import router as auth_router
from app.api.v1.beta.router import router as beta_router
from app.api.v1.dashboard.router import router as dashboard_router
from app.api.v1.recitations.router import router as recitations_router
from app.api.v1.submissions.router import router as submissions_router
from app.api.v1.users.router import router as users_router

api_router = APIRouter()
api_router.include_router(auth_router)
api_router.include_router(users_router)
api_router.include_router(recitations_router)
api_router.include_router(submissions_router)
api_router.include_router(dashboard_router)
api_router.include_router(beta_router)
