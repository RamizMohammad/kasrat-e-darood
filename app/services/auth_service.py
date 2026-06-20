"""Authentication use-cases: Firebase exchange, refresh rotation, logout."""
from __future__ import annotations

from app.core.exceptions import UnauthorizedError
from app.models.activity import RefreshToken
from app.models.base import utcnow
from app.models.user import User
from app.repositories.user_repo import user_repository
from app.schemas.auth import LoginResponse, TokenPair
from app.schemas.user import UserOut
from app.security.firebase import verify_id_token
from app.security.tokens import (
    create_access_token,
    create_refresh_token,
    hash_refresh_token,
)


class AuthService:
    """Stateless service handling the auth lifecycle."""

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
        if record is None or record.expires_at < utcnow():
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
