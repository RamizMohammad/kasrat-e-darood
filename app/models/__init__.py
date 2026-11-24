"""Model registry: importing this module registers every table on Base.metadata."""
from app.models.activity import (
    ActivityItem,
    DeviceToken,
    LeaderboardSnapshot,
    Notification,
    PasswordResetCode,
    RefreshToken,
)
from app.models.base import Base
from app.models.beta import BetaSignup
from app.models.category import Category, Recitation
from app.models.group import (
    Group,
    GroupAnnouncement,
    GroupAuditLog,
    GroupWeek,
    JoinRequest,
    Membership,
)
from app.models.submission import Submission
from app.models.user import User
from app.models.weekly_session import WeeklySession

ALL_MODELS = [
    User, Group, Membership, JoinRequest, GroupAnnouncement, GroupAuditLog, GroupWeek,
    Category, Recitation, WeeklySession, Submission,
    ActivityItem, LeaderboardSnapshot, Notification, DeviceToken, RefreshToken,
    PasswordResetCode, BetaSignup,
]

__all__ = [m.__name__ for m in ALL_MODELS] + ["ALL_MODELS", "Base"]
