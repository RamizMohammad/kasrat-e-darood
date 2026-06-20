"""MongoDB connection and Beanie initialization."""
from __future__ import annotations

from beanie import init_beanie
from loguru import logger
from motor.motor_asyncio import AsyncIOMotorClient

from app.config.settings import settings
from app.models import ALL_DOCUMENT_MODELS


class _Mongo:
    """Holds the shared Motor client and database handle."""

    client: AsyncIOMotorClient | None = None

    @property
    def db(self):
        if self.client is None:
            raise RuntimeError("MongoDB client not initialized")
        return self.client[settings.MONGODB_DB_NAME]


mongo = _Mongo()


async def connect_to_mongo() -> None:
    """Open the Motor client and register Beanie document models."""
    logger.info("Connecting to MongoDB …")
    mongo.client = AsyncIOMotorClient(
        settings.MONGODB_URI,
        uuidRepresentation="standard",
        tz_aware=True,
    )
    await init_beanie(database=mongo.db, document_models=ALL_DOCUMENT_MODELS)
    logger.info("MongoDB connected, Beanie initialized ({} models)",
                len(ALL_DOCUMENT_MODELS))


async def close_mongo_connection() -> None:
    """Close the Motor client on shutdown."""
    if mongo.client is not None:
        mongo.client.close()
        logger.info("MongoDB connection closed")
