"""Authentication use-cases: Firebase/Google exchange, refresh, password reset."""
from __future__ import annotations

import hashlib
import secrets
from datetime import datetime, timedelta, timezone

from app.config.settings import settings
from app.core.exceptions import (
    ConflictError,
    UnauthorizedError,
    ValidationAppError,
)
from app.models.activity import PasswordResetCode, RefreshToken
from app.models.base import utcnow
from app.models.user import User
from app.repositories.user_repo import user_repository
from app.schemas.auth import LoginResponse, TokenPair
from app.schemas.user import UserOut
from app.security.firebase import verify_id_token
from app.security.google import verify_google_id_token
from app.security.passwords import hash_password, verify_password
from app.security.tokens import (
    create_access_token,
    create_refresh_token,
    hash_refresh_token,
)


def _normalize_email(email: str) -> str:
    return email.strip().lower()


def _hash_code(code: str) -> str:
    return hashlib.sha256(code.encode()).hexdigest()


def _as_aware(dt: datetime) -> datetime:
    """MongoDB returns naive UTC datetimes; make them tz-aware for comparison."""
    return dt if dt.tzinfo is not None else dt.replace(tzinfo=timezone.utc)


class AuthService:
    """Stateless service handling the auth lifecycle."""

    async def register(
        self, email: str, password: str, display_name: str
    ) -> LoginResponse:
        """Create an email/password account and return an authenticated session."""
        email = _normalize_email(email)
        if await user_repository.get_by_email(email):
            raise ConflictError("An account with this email already exists",
                                code="email_taken")
        user = User(
            # Email/password accounts have no Firebase identity, so a synthetic
            # uid keeps the unique index satisfied and stable per email.
            firebase_uid=f"pwd:{email}",
            display_name=display_name.strip(),
            email=email,
            password_hash=hash_password(password),
        )
        await user_repository.create(user)
        pair = await self._issue_tokens(user)
        return LoginResponse(
            access_token=pair.access_token,
            refresh_token=pair.refresh_token,
            user=UserOut.model_validate(user),
        )

    async def login_with_password(self, email: str, password: str) -> LoginResponse:
        """Verify email/password credentials and return an authenticated session."""
        email = _normalize_email(email)
        user = await user_repository.get_by_email(email)
        if user is None or not verify_password(password, user.password_hash):
            raise UnauthorizedError("Incorrect email or password",
                                    code="invalid_credentials")
        pair = await self._issue_tokens(user)
        return LoginResponse(
            access_token=pair.access_token,
            refresh_token=pair.refresh_token,
            user=UserOut.model_validate(user),
        )

    async def login_with_google(self, id_token: str) -> LoginResponse:
        """Verify a Google ID token and return an authenticated session.

        New Google users are created as MEMBER; an existing email account with
        the same address is linked to the Google identity.
        """
        identity = verify_google_id_token(id_token)
        uid = f"google:{identity['uid']}"
        email = _normalize_email(identity["email"]) if identity.get("email") else None

        user = await user_repository.get_by_firebase_uid(uid)
        if user is None and email:
            user = await user_repository.get_by_email(email)
            if user is not None:
                user.firebase_uid = uid  # link existing account to Google
                await user_repository.save(user)
        if user is None:
            user = User(
                firebase_uid=uid,
                display_name=identity.get("name") or "User",
                email=email,
                photo_url=identity.get("picture"),
            )
            await user_repository.create(user)

        pair = await self._issue_tokens(user)
        return LoginResponse(
            access_token=pair.access_token,
            refresh_token=pair.refresh_token,
            user=UserOut.model_validate(user),
        )

    # --- Password reset (OTP) -------------------------------------------------
    async def create_reset_code(self, email: str) -> tuple[str, str] | None:
        """Generate + store a reset OTP for `email`. Returns (code, lang).

        Returns None when no matching account exists (caller should still respond
        with success to avoid leaking which emails are registered).
        """
        email = _normalize_email(email)
        user = await user_repository.get_by_email(email)
        if user is None:
            return None
        code = f"{secrets.randbelow(1_000_000):06d}"
        expires = utcnow() + timedelta(
            minutes=settings.PASSWORD_RESET_CODE_TTL_MINUTES
        )
        await PasswordResetCode(
            user_id=user.id, email=email, code_hash=_hash_code(code),
            expires_at=expires,
        ).insert()
        lang = str(user.preferences.get("lang", "en"))
        return code, lang

    async def reset_password(self, email: str, code: str, new_password: str) -> None:
        """Verify the OTP and set a new password."""
        email = _normalize_email(email)
        record = await PasswordResetCode.find(
            PasswordResetCode.email == email,
            PasswordResetCode.used == False,  # noqa: E712
        ).sort("-created_at").first_or_none()

        if record is None or _as_aware(record.expires_at) < utcnow():
            raise ValidationAppError("Invalid or expired code", code="invalid_code")
        if record.attempts >= settings.PASSWORD_RESET_MAX_ATTEMPTS:
            record.used = True
            await record.save()
            raise ValidationAppError("Too many attempts. Request a new code.",
                                     code="too_many_attempts")
        if record.code_hash != _hash_code(code):
            record.attempts += 1
            await record.save()
            raise ValidationAppError("Incorrect code", code="incorrect_code")

        user = await user_repository.get(record.user_id)
        if user is None:
            raise ValidationAppError("Account not found", code="not_found")
        user.password_hash = hash_password(new_password)
        await user_repository.save(user)
        record.used = True
        await record.save()

    async def login_with_firebase(self, id_token: str) -> LoginResponse:
        identity = verify_id_token(id_token)
        user = await user_repository.get_by_firebase_uid(identity["uid"])
        if user is None:
            user = User(
                firebase_uid=identity["uid"],
                display_name=identity.get("name") or "User",
                email=identity.get("email"),
                phone=identity.get("phone"),
                photo_url=identity.get("picture"),
            )
            await user_repository.create(user)
        pair = await self._issue_tokens(user)
        return LoginResponse(
            access_token=pair.access_token,
            refresh_token=pair.refresh_token,
            user=UserOut.model_validate(user),
        )

    async def refresh(self, raw_refresh: str) -> TokenPair:
        token_hash = hash_refresh_token(raw_refresh)
        record = await RefreshToken.find_one(
            RefreshToken.token_hash == token_hash,
            RefreshToken.revoked == False,  # noqa: E712
        )
        if record is None or _as_aware(record.expires_at) < utcnow():
            raise UnauthorizedError("Invalid or expired refresh token")
        # Rotation: revoke the old token, issue a fresh pair.
        record.revoked = True
        await record.save()
        user = await user_repository.get(record.user_id)
        if user is None:
            raise UnauthorizedError("User not found")
        return await self._issue_tokens(user)

    async def logout(self, raw_refresh: str) -> None:
        token_hash = hash_refresh_token(raw_refresh)
        record = await RefreshToken.find_one(RefreshToken.token_hash == token_hash)
        if record is not None:
            record.revoked = True
            await record.save()

    async def _issue_tokens(self, user: User) -> TokenPair:
        access = create_access_token(str(user.id), role=user.role.value)
        raw, token_hash, expires = create_refresh_token()
        await RefreshToken(
            user_id=user.id, token_hash=token_hash, expires_at=expires
        ).insert()
        return TokenPair(access_token=access, refresh_token=raw)


auth_service = AuthService()
