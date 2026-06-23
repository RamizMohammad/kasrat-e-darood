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

    # --- Firebase ------------------------------------------------------------
    # Path to the Firebase service-account JSON. When empty, Firebase token
    # verification is bypassed in development (a stub identity is used).
    FIREBASE_CREDENTIALS_FILE: str = Field(default="")
    FIREBASE_PROJECT_ID: str = Field(default="")

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
