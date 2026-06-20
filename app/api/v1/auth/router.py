"""Authentication endpoints."""
from __future__ import annotations

from fastapi import APIRouter, Depends, status

from app.dependencies.auth import get_current_user
from app.models.user import User
from app.schemas.auth import (
    FirebaseLoginRequest,
    LoginResponse,
    LogoutRequest,
    RefreshRequest,
    TokenPair,
)
from app.schemas.user import UserOut
from app.services.auth_service import auth_service

router = APIRouter(prefix="/auth", tags=["auth"])


@router.post("/firebase", response_model=LoginResponse)
async def login_with_firebase(body: FirebaseLoginRequest) -> LoginResponse:
    """Exchange a Firebase ID token for application access/refresh tokens."""
    return await auth_service.login_with_firebase(body.id_token)


@router.post("/refresh", response_model=TokenPair)
async def refresh(body: RefreshRequest) -> TokenPair:
    """Rotate a refresh token, returning a new token pair."""
    return await auth_service.refresh(body.refresh_token)


@router.post("/logout", status_code=status.HTTP_204_NO_CONTENT)
async def logout(body: LogoutRequest) -> None:
    """Revoke a refresh token."""
    await auth_service.logout(body.refresh_token)


@router.get("/me", response_model=UserOut)
async def me(user: User = Depends(get_current_user)) -> UserOut:
    """Return the authenticated user."""
    return UserOut.model_validate(user)
