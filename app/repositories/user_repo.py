"""User repository."""
from __future__ import annotations

from app.models.user import User
from app.repositories.base import BaseRepository


class UserRepository(BaseRepository[User]):
    def __init__(self) -> None:
        super().__init__(User)

    async def get_by_firebase_uid(self, uid: str) -> User | None:
        return await User.find_one(User.firebase_uid == uid, User.deleted == False)  # noqa: E712

    async def search(self, q: str, limit: int = 20) -> list[User]:
        return await User.find(
            {"$text": {"$search": q}, "deleted": False}
        ).limit(limit).to_list()


user_repository = UserRepository()
