"""Application configuration.

All settings are read from environment variables (12-factor). Values are
validated by Pydantic at startup so the process fails fast on misconfiguration.
"""
from __future__ import annotations

from functools import lru_cache
from typing import List

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Strongly-typed application settings loaded from the environment."""

    model_config = SettingsConfigDict(
        env_file=".env", env_file_encoding="utf-8", extra="ignore"
    )

    # --- Application ---------------------------------------------------------
    APP_NAME: str = "Noor API"
    APP_ENV: str = Field(default="development")  # development | staging | production
    DEBUG: bool = Field(default=True)
    API_V1_PREFIX: str = "/api/v1"
    HOST: str = "0.0.0.0"
    PORT: int = 8000

    # --- MongoDB -------------------------------------------------------------
    MONGODB_URI: str = Field(default="mongodb://localhost:27017")
    MONGODB_DB_NAME: str = Field(default="noor")

    # --- Redis ---------------------------------------------------------------
    REDIS_URL: str = Field(default="redis://localhost:6379/0")
    CACHE_TTL_SECONDS: int = 60

    # --- JWT -----------------------------------------------------------------
    JWT_SECRET: str = Field(default="change-me-in-production")
    JWT_ALGORITHM: str = "HS256"
    ACCESS_TOKEN_EXPIRE_MINUTES: int = 30
    REFRESH_TOKEN_EXPIRE_DAYS: int = 30

    # --- Password reset (OTP) ------------------------------------------------
    PASSWORD_RESET_CODE_TTL_MINUTES: int = 10
    PASSWORD_RESET_MAX_ATTEMPTS: int = 5

    # --- Firebase ------------------------------------------------------------
    # Path to the Firebase service-account JSON. When empty, Firebase token
    # verification is bypassed in development (a stub identity is used).
    FIREBASE_CREDENTIALS_FILE: str = Field(default="")
    FIREBASE_PROJECT_ID: str = Field(default="")

    # --- Google Sign-In ------------------------------------------------------
    # The OAuth *web* client ID (audience of the Google ID token the Android
    # app sends). Taken from google-services.json (oauth_client type 3).
    GOOGLE_CLIENT_ID: str = Field(
        default="571474681893-2tl01abvbecpp764fjil2bkjse1dssj5.apps.googleusercontent.com"
    )

    # --- SMTP / email (Gmail) ------------------------------------------------
    # Use a Gmail account with 2FA enabled and an App Password (not your normal
    # password). Leave SMTP_USER/SMTP_PASSWORD empty to disable email sending.
    SMTP_HOST: str = Field(default="smtp.gmail.com")
    SMTP_PORT: int = Field(default=587)
    SMTP_USER: str = Field(default="")          # your Gmail address
    SMTP_PASSWORD: str = Field(default="")      # Gmail App Password (16 chars)
    SMTP_USE_SSL: bool = Field(default=False)   # False => STARTTLS on 587; True => SSL on 465
    SMTP_FROM_NAME: str = Field(default="Kasrat-e-Darood")
    SMTP_FROM_EMAIL: str = Field(default="")    # defaults to SMTP_USER when empty
    SUPPORT_EMAIL: str = Field(default="")      # shown in the email footer (optional)

    # --- Domain rules --------------------------------------------------------
    SUBMISSION_UNDO_WINDOW_SECONDS: int = 300
    DEFAULT_TIMEZONE: str = "Asia/Kolkata"
    WEEK_LOCK_WEEKDAY: int = 4  # 0=Mon … 4=Friday

    @property
    def is_production(self) -> bool:
        return self.APP_ENV == "production"


@lru_cache
def get_settings() -> Settings:
    """Return a cached Settings instance (one per process)."""
    return Settings()


settings = get_settings()
