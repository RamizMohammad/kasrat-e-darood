"""Aggregate all v1 routers under the API prefix."""
from __future__ import annotations

from fastapi import APIRouter, Depends

from app.api.v1.auth.router import router as auth_router
from app.api.v1.beta.router import router as beta_router
from app.api.v1.dashboard.router import router as dashboard_router
from app.api.v1.groups.router import router as groups_router
from app.api.v1.invites.router import router as invites_router
from app.api.v1.leaderboards.router import router as leaderboards_router
from app.api.v1.notifications.router import router as notifications_router
from app.api.v1.recitations.router import router as recitations_router
from app.api.v1.statistics.router import router as statistics_router
from app.api.v1.submissions.router import router as submissions_router
from app.api.v1.users.router import router as users_router
from app.api.v1.weeks.router import router as weeks_router

from app.database.postgres import use_db  # noqa: E402

# Every v1 route runs inside one database transaction (see database/postgres.py).
api_router = APIRouter(dependencies=[Depends(use_db)])
api_router.include_router(auth_router)
api_router.include_router(users_router)
api_router.include_router(recitations_router)
api_router.include_router(submissions_router)
api_router.include_router(dashboard_router)
api_router.include_router(statistics_router)
api_router.include_router(notifications_router)
api_router.include_router(beta_router)
api_router.include_router(leaderboards_router)
api_router.include_router(weeks_router)
api_router.include_router(groups_router)
api_router.include_router(invites_router)
