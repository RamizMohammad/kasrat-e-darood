"""Authentication endpoints."""
from __future__ import annotations

from fastapi import APIRouter, BackgroundTasks, Depends, Response, status

from app.config.settings import settings
from app.dependencies.auth import get_current_user
from app.models.user import User
from app.schemas.auth import (
    EmailLoginRequest,
    FirebaseLoginRequest,
    ForgotPasswordRequest,
    GoogleLoginRequest,
    LoginResponse,
    LogoutRequest,
    OkResponse,
    RefreshRequest,
    RegisterRequest,
    ResetPasswordRequest,
    TokenPair,
)
from app.schemas.user import UserOut
from app.services.auth_service import auth_service
from app.services.email_service import email_service

router = APIRouter(prefix="/auth", tags=["auth"])


@router.post("/register", response_model=LoginResponse,
             status_code=status.HTTP_201_CREATED)
async def register(body: RegisterRequest) -> LoginResponse:
    """Create an email/password account and return an authenticated session."""
    return await auth_service.register(body.email, body.password, body.display_name)


@router.post("/login", response_model=LoginResponse)
async def login(body: EmailLoginRequest) -> LoginResponse:
    """Authenticate with email/password, returning access/refresh tokens."""
    return await auth_service.login_with_password(body.email, body.password)


@router.post("/google", response_model=LoginResponse)
async def login_with_google(body: GoogleLoginRequest) -> LoginResponse:
    """Exchange a Google ID token for application access/refresh tokens."""
    return await auth_service.login_with_google(body.id_token)


@router.post("/forgot-password", response_model=OkResponse)
async def forgot_password(
    body: ForgotPasswordRequest, background: BackgroundTasks
) -> OkResponse:
    """Email a one-time reset code. Always returns ok (no account enumeration)."""
    result = await auth_service.create_reset_code(body.email)
    if result is not None:
        code, lang = result
        background.add_task(
            email_service.send_password_reset,
            to_email=body.email.strip().lower(),
            code=code,
            minutes=settings.PASSWORD_RESET_CODE_TTL_MINUTES,
            lang=lang,
        )
    return OkResponse()


@router.post("/reset-password", response_model=OkResponse)
async def reset_password(body: ResetPasswordRequest) -> OkResponse:
    """Verify the OTP and set a new password."""
    await auth_service.reset_password(body.email, body.code, body.new_password)
    return OkResponse()


@router.post("/firebase", response_model=LoginResponse)
async def login_with_firebase(body: FirebaseLoginRequest) -> LoginResponse:
    """Exchange a Firebase ID token for application access/refresh tokens."""
    return await auth_service.login_with_firebase(body.id_token)


@router.post("/refresh", response_model=TokenPair)
async def refresh(body: RefreshRequest) -> TokenPair:
    """Rotate a refresh token, returning a new token pair."""
    return await auth_service.refresh(body.refresh_token)


@router.post("/logout", status_code=status.HTTP_204_NO_CONTENT, response_class=Response)
async def logout(body: LogoutRequest) -> Response:
    """Revoke a refresh token."""
    await auth_service.logout(body.refresh_token)
    return Response(status_code=status.HTTP_204_NO_CONTENT)


@router.get("/me", response_model=UserOut)
async def me(user: User = Depends(get_current_user)) -> UserOut:
    """Return the authenticated user."""
    return UserOut.model_validate(user)
