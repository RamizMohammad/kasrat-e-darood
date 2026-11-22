"""Pytest fixtures using an in-memory Mongo (mongomock-motor)."""
from __future__ import annotations

import asyncio

import pytest
import pytest_asyncio
from beanie import init_beanie
from mongomock_motor import AsyncMongoMockClient

from app.models import ALL_DOCUMENT_MODELS


@pytest.fixture(scope="session")
def event_loop():
    loop = asyncio.new_event_loop()
    yield loop
    loop.close()


@pytest_asyncio.fixture
async def init_db():
    client = AsyncMongoMockClient()
    await init_beanie(database=client["noor_test"],
                      document_models=ALL_DOCUMENT_MODELS)
    yield
