"""Small query helpers for tests (the services use repositories instead)."""
from __future__ import annotations

from sqlalchemy import func, select

from app.database.postgres import db


async def get(model, row_id):
    return await db().get(model, row_id)


async def insert(obj):
    db().add(obj)
    await db().flush()
    return obj


async def find_one(model, *where):
    return (await db().execute(select(model).where(*where).limit(1))).scalars().first()


async def find_all(model, *where):
    return list((await db().execute(select(model).where(*where))).scalars().all())


async def count(model, *where):
    stmt = select(func.count()).select_from(model).where(*where)
    return int((await db().execute(stmt)).scalar_one())
