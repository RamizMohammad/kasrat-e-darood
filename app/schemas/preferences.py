"""User preferences: a fixed, validated shape (no arbitrary dicts).

Stored as JSON in users.preferences. PATCH /users/me accepts a *partial*
PreferencesUpdate and deep-merges it into what's stored, so the app can send
just {"hide_on_boards": true}. Unknown keys are rejected with 422.
"""
from __future__ import annotations

from typing import Any

from pydantic import BaseModel, ConfigDict, Field, field_validator

_HHMM = r"^([01][0-9]|2[0-3]):[0-5][0-9]$"


class _Strict(BaseModel):
    model_config = ConfigDict(extra="forbid")


def _days(v: list[int] | None) -> list[int] | None:
    if v is None:
        return v
    if any(d < 0 or d > 6 for d in v):
        raise ValueError("days are 0 (Monday) … 6 (Sunday)")
    return sorted(set(v))


class ReminderPrefs(_Strict):
    """Daily reminder (P8 schedules it on the phone; the server only stores it)."""
    enabled: bool = False
    time: str = Field(default="21:00", pattern=_HHMM)      # local time, 24 h
    days: list[int] = Field(default_factory=lambda: list(range(7)), max_length=7)

    @field_validator("days")
    @classmethod
    def _check_days(cls, v: list[int]) -> list[int]:
        return _days(v)


class NotifyPrefs(_Strict):
    """Which kinds of push you want."""
    activity: bool = True    # community logs
    groups: bool = True      # join requests, goals, rank changes
    system: bool = True      # quotes, reminders, closed weeks
    streak: bool = True      # "keep your streak" nudge


class Preferences(_Strict):
    hide_on_boards: bool = False          # show as "A member" on leaderboards
    weekly_goal: int = Field(default=7, ge=1, le=100_000)
    prayer_method: int = Field(default=1, ge=0, le=23)     # AlAdhan method id
    prayer_school: int = Field(default=1, ge=0, le=1)      # 0 Shafi, 1 Hanafi
    reminders: ReminderPrefs = Field(default_factory=ReminderPrefs)
    notify: NotifyPrefs = Field(default_factory=NotifyPrefs)

    @classmethod
    def from_stored(cls, raw: dict[str, Any] | None) -> "Preferences":
        """Read what's in the database leniently: drop keys this version
        doesn't know and fall back to defaults for invalid values."""
        data = dict(raw or {})
        good: dict[str, Any] = {}
        for name in cls.model_fields:
            if name not in data:
                continue
            try:
                cls.model_validate({name: data[name]})
                good[name] = data[name]
            except ValueError:  # one bad stored value mustn't break GET /me
                continue
        return cls.model_validate(good)


class ReminderPrefsUpdate(_Strict):
    enabled: bool | None = None
    time: str | None = Field(default=None, pattern=_HHMM)
    days: list[int] | None = Field(default=None, max_length=7)

    @field_validator("days")
    @classmethod
    def _check_days(cls, v: list[int] | None) -> list[int] | None:
        return _days(v)


class NotifyPrefsUpdate(_Strict):
    activity: bool | None = None
    groups: bool | None = None
    system: bool | None = None
    streak: bool | None = None


class PreferencesUpdate(_Strict):
    hide_on_boards: bool | None = None
    weekly_goal: int | None = Field(default=None, ge=1, le=100_000)
    prayer_method: int | None = Field(default=None, ge=0, le=23)
    prayer_school: int | None = Field(default=None, ge=0, le=1)
    reminders: ReminderPrefsUpdate | None = None
    notify: NotifyPrefsUpdate | None = None

    def apply_to(self, current: Preferences) -> Preferences:
        """``current`` with every field that was sent replaced (nested too)."""
        merged = current.model_dump()
        for key, value in self.model_dump(exclude_unset=True).items():
            if isinstance(value, dict):
                merged[key] = {**merged[key], **{k: v for k, v in value.items()}}
            else:
                merged[key] = value
        return Preferences.model_validate(merged)
