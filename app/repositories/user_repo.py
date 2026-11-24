"""User repository."""
from __future__ import annotations

from sqlalchemy import func, select

from app.database.postgres import db
from app.models.user import User
from app.repositories.base import BaseRepository


class UserRepository(BaseRepository[User]):
    def __init__(self) -> None:
        super().__init__(User)

    async def get_by_firebase_uid(self, uid: str) -> User | None:
        return await self.first(User.firebase_uid == uid)

    async def get_by_email(self, email: str) -> User | None:
        return await self.first(User.email == email)

    async def get_many(self, ids: set) -> dict:
        """{id: User} for the given ids (deleted users left out)."""
        ids = {i for i in ids if i is not None}
        if not ids:
            return {}
        result = await db().execute(self.live().where(User.id.in_(ids)))
        return {u.id: u for u in result.scalars().all()}

    async def count_all(self) -> int:
        stmt = select(func.count()).select_from(User).where(User.deleted.is_(False))
        return int((await db().execute(stmt)).scalar_one())

    async def search(self, q: str, limit: int = 20) -> list[User]:
        """Case-insensitive name search (prefix matches first)."""
        q = q.strip()
        if not q:
            return []
        pattern = "%" + q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
        stmt = (self.live()
                .where(User.display_name.ilike(pattern, escape="\\"))
                .order_by(func.lower(User.display_name).startswith(q.lower()).desc(),
                          User.display_name)
                .limit(limit))
        return list((await db().execute(stmt)).scalars().all())


user_repository = UserRepository()
