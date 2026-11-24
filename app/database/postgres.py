"""PostgreSQL engine and request-scoped sessions (SQLAlchemy 2, asyncpg).

Each HTTP request runs in one session and one transaction: the ``use_db``
dependency opens it, commits when the endpoint succeeds and rolls back when it
raises. Services and repositories reach the current session with ``db()`` so
their signatures don't have to carry it around. Background jobs and scripts
open their own with ``session_scope()``.
"""
from __future__ import annotations

from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from contextvars import ContextVar

from loguru import logger
from sqlalchemy import text
from sqlalchemy.ext.asyncio import (
    AsyncEngine,
    AsyncSession,
    async_sessionmaker,
    create_async_engine,
)
from sqlalchemy.pool import NullPool

from app.config.settings import settings

_engine: AsyncEngine | None = None
_sessionmaker: async_sessionmaker[AsyncSession] | None = None
_current: ContextVar[AsyncSession | None] = ContextVar("db_session", default=None)


def async_url(url: str) -> str:
    """Accept postgres:// or postgresql:// URLs and use the asyncpg driver."""
    for prefix in ("postgres://", "postgresql://", "postgresql+psycopg2://", "postgresql+psycopg://"):
        if url.startswith(prefix):
            return "postgresql+asyncpg://" + url[len(prefix):]
    return url


def init_engine(url: str | None = None, *, null_pool: bool = False) -> AsyncEngine:
    """Create the engine + session factory (idempotent per process)."""
    global _engine, _sessionmaker
    target = async_url(url or settings.DATABASE_URL)
    if not target:
        raise RuntimeError("DATABASE_URL is not set")
    kwargs: dict = {"pool_pre_ping": True}
    if null_pool:
        kwargs["poolclass"] = NullPool
    else:
        kwargs.update(pool_size=settings.DB_POOL_SIZE, max_overflow=settings.DB_MAX_OVERFLOW,
                      pool_recycle=1800)
    _engine = create_async_engine(target, **kwargs)
    _sessionmaker = async_sessionmaker(_engine, expire_on_commit=False, autoflush=True)
    return _engine


def engine() -> AsyncEngine:
    if _engine is None:
        init_engine()
    assert _engine is not None
    return _engine


async def connect_to_postgres() -> None:
    """Create the engine and check the database answers (fail fast at startup)."""
    eng = engine()
    async with eng.connect() as conn:
        await conn.execute(text("SELECT 1"))
    logger.info("PostgreSQL connected")


async def close_postgres() -> None:
    global _engine, _sessionmaker
    if _engine is not None:
        await _engine.dispose()
        logger.info("PostgreSQL connection pool closed")
    _engine = None
    _sessionmaker = None


def db() -> AsyncSession:
    """The session of the current request / job. Raises outside a scope."""
    session = _current.get()
    if session is None:
        raise RuntimeError("No database session: wrap the call in session_scope()")
    return session


@asynccontextmanager
async def session_scope() -> AsyncIterator[AsyncSession]:
    """One session + transaction: commit on success, roll back on error.

    Nested calls reuse the outer session (and its transaction).
    """
    existing = _current.get()
    if existing is not None:
        yield existing
        return
    engine()  # make sure the factory exists
    assert _sessionmaker is not None
    async with _sessionmaker() as session:
        token = _current.set(session)
        try:
            yield session
            await session.commit()
        except BaseException:
            await session.rollback()
            raise
        finally:
            _current.reset(token)


async def use_db() -> AsyncIterator[AsyncSession]:
    """FastAPI dependency: run the request inside one transaction."""
    async with session_scope() as session:
        yield session
