"""Document model registry for Beanie initialization."""
from app.models.activity import (
    ActivityItem,
    DeviceToken,
    LeaderboardSnapshot,
    Notification,
    RefreshToken,
)
from app.models.beta import BetaSignup
from app.models.category import Category, Recitation
from app.models.group import Group, Membership
from app.models.submission import Submission
from app.models.user import User
from app.models.weekly_session import WeeklySession

ALL_DOCUMENT_MODELS = [
    User,
    Group,
    Membership,
    Category,
    Recitation,
    WeeklySession,
    Submission,
    ActivityItem,
    LeaderboardSnapshot,
    Notification,
    DeviceToken,
    RefreshToken,
    BetaSignup,
]

__all__ = [m.__name__ for m in ALL_DOCUMENT_MODELS] + ["ALL_DOCUMENT_MODELS"]
