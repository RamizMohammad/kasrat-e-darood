"""Beta program signup document."""
from __future__ import annotations

from enum import Enum

import pymongo
from beanie import Indexed

from app.models.base import BaseDocument


class BetaTrack(str, Enum):
    CLOSED = "closed"
    OPEN = "open"


class SignupStatus(str, Enum):
    PENDING = "pending"
    INVITED = "invited"
    JOINED = "joined"
    REJECTED = "rejected"


class BetaSignup(BaseDocument):
    full_name: str
    email: Indexed(str, unique=True)  # type: ignore[valid-type]
    google_email: str | None = None  # Play Store tester account (Android closed beta)
    phone: str | None = None
    country: str | None = None
    device: str = "android"          # android | ios
    how_heard: str | None = None
    reason: str | None = None
    track: BetaTrack = BetaTrack.CLOSED
    status: SignupStatus = SignupStatus.PENDING
    consent: bool = True
    lang: str = "en"                 # preferred language for emails: en | hi | ur

    class Settings:
        name = "beta_signups"
        indexes = [
            [("track", pymongo.ASCENDING), ("status", pymongo.ASCENDING)],
            [("created_at", pymongo.DESCENDING)],
        ]
