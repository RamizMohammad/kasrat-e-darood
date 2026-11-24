"""Pytest fixtures: a real PostgreSQL test database.

Set TEST_DATABASE_URL to an empty database the tests may wipe, e.g.
    TEST_DATABASE_URL=postgresql://postgres:postgres@localhost:5432/kasrat_test
The schema is created once per run from the models; every test starts from
empty tables and runs inside one session, like a request does.
"""
from __future__ import annotations

import asyncio
import os

import pytest
import pytest_asyncio
from sqlalchemy import text

from app.database import postgres
from app.models import Base

TEST_DATABASE_URL = os.getenv(
    "TEST_DATABASE_URL", "postgresql://postgres:postgres@localhost:5432/kasrat_test")


@pytest.fixture(scope="session")
def event_loop():
    loop = asyncio.new_event_loop()
    yield loop
    loop.close()


@pytest.fixture(scope="session", autouse=True)
def _schema(event_loop):
    """Build the schema from the models once (fresh every run)."""
    async def build():
        eng = postgres.init_engine(TEST_DATABASE_URL, null_pool=True)
        async with eng.begin() as conn:
            await conn.run_sync(Base.metadata.drop_all)
            await conn.run_sync(Base.metadata.create_all)
        await postgres.close_postgres()
    event_loop.run_until_complete(build())
    yield


@pytest_asyncio.fixture
async def init_db():
    """Empty tables + an open session (as inside a request) for the test."""
    postgres.init_engine(TEST_DATABASE_URL, null_pool=True)
    names = ", ".join(f'"{t.name}"' for t in Base.metadata.sorted_tables)
    async with postgres.engine().begin() as conn:
        await conn.execute(text(f"TRUNCATE {names} RESTART IDENTITY CASCADE"))
    async with postgres.session_scope() as session:
        yield session
    await postgres.close_postgres()


@pytest_asyncio.fixture
async def clean_db():
    """Empty tables, but NO open session: each step opens its own, like real
    requests on separate connections."""
    postgres.init_engine(TEST_DATABASE_URL, null_pool=True)
    names = ", ".join(f'"{t.name}"' for t in Base.metadata.sorted_tables)
    async with postgres.engine().begin() as conn:
        await conn.execute(text(f"TRUNCATE {names} RESTART IDENTITY CASCADE"))
    yield
    await postgres.close_postgres()
