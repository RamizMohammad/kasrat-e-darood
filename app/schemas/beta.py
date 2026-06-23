"""Beta signup request/response schemas."""
from __future__ import annotations

import re

from beanie import PydanticObjectId
from pydantic import BaseModel, Field, field_validator

from app.schemas.common import ORMModel

_EMAIL_RE = re.compile(r"^[^@\s]+@[^@\s]+\.[^@\s]+$")


class BetaSignupCreate(BaseModel):
    full_name: str = Field(min_length=2, max_length=80)
    email: str = Field(max_length=120)
    google_email: str | None = Field(default=None, max_length=120)
    phone: str | None = Field(default=None, max_length=30)
    country: str | None = Field(default=None, max_length=60)
    device: str = "android"
    how_heard: str | None = Field(default=None, max_length=120)
    reason: str | None = Field(default=None, max_length=500)
    track: str = "closed"
    consent: bool = True
    # Preferred language for the confirmation email: en | hi | ur.
    lang: str = "en"
    # Honeypot: a hidden field real users never see. Bots tend to fill every
    # input, so a non-empty value flags the submission as spam.
    website: str | None = Field(default=None, max_length=200)

    @field_validator("lang")
    @classmethod
    def _valid_lang(cls, v: str) -> str:
        return v if v in {"en", "hi", "ur"} else "en"

    @field_validator("email", "google_email")
    @classmethod
    def _valid_email(cls, v: str | None) -> str | None:
        if v is None or v == "":
            return None if v == "" else v
        if not _EMAIL_RE.match(v.strip().lower()):
            raise ValueError("Invalid email address")
        return v.strip().lower()

    @field_validator("device")
    @classmethod
    def _valid_device(cls, v: str) -> str:
        if v not in {"android", "ios"}:
            raise ValueError("device must be 'android' or 'ios'")
        return v


class BetaSignupOut(ORMModel):
    id: PydanticObjectId
    full_name: str
    email: str
    device: str
    track: str
    status: str


class BetaSignupResult(BaseModel):
    ok: bool
    already_registered: bool
    signup: BetaSignupOut


class BetaStats(BaseModel):
    total: int
    closed: int
    open: int
