"""Auth request/response schemas."""
from __future__ import annotations

from pydantic import BaseModel, Field, field_validator

from app.schemas.user import UserOut


class FirebaseLoginRequest(BaseModel):
    id_token: str = Field(min_length=1, max_length=8192)


class GoogleLoginRequest(BaseModel):
    id_token: str = Field(min_length=1, max_length=8192)


class ForgotPasswordRequest(BaseModel):
    email: str = Field(min_length=3, max_length=254)


class ResetPasswordRequest(BaseModel):
    email: str = Field(min_length=3, max_length=254)
    code: str = Field(min_length=4, max_length=10, pattern=r"^[0-9 ]+$")
    new_password: str = Field(min_length=8, max_length=128)


class DeleteAccountConfirm(BaseModel):
    code: str = Field(min_length=4, max_length=10, pattern=r"^[0-9 ]+$")


class OkResponse(BaseModel):
    ok: bool = True


class RegisterRequest(BaseModel):
    email: str = Field(min_length=3, max_length=254, pattern=r"^[^@\s]+@[^@\s]+\.[^@\s]+$")
    password: str = Field(min_length=8, max_length=128)
    display_name: str = Field(min_length=1, max_length=80)
    lang: str = Field(default="en", pattern="^(en|hi|ur)$")

    @field_validator("display_name")
    @classmethod
    def _name(cls, v: str) -> str:
        from app.schemas.user import _clean_display_name
        return _clean_display_name(v)  # type: ignore[return-value]


class EmailLoginRequest(BaseModel):
    email: str = Field(min_length=3, max_length=254)
    password: str = Field(min_length=1, max_length=128)


class RefreshRequest(BaseModel):
    refresh_token: str = Field(min_length=1, max_length=512)


class LogoutRequest(BaseModel):
    refresh_token: str = Field(min_length=1, max_length=512)


class TokenPair(BaseModel):
    access_token: str
    refresh_token: str
    token_type: str = "bearer"


class LoginResponse(TokenPair):
    user: UserOut
