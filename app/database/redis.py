"""Redis cache client and small helper for cached read-models."""
from __future__ import annotations

import json
from typing import Any

import redis.asyncio as aioredis
from loguru import logger

from app.config.settings import settings


class _Cache:
    client: aioredis.Redis | None = None

    async def connect(self) -> None:
        self.client = aioredis.from_url(settings.REDIS_URL, decode_responses=True)
        try:
            await self.client.ping()
            logger.info("Redis connected")
        except Exception as exc:  # pragma: no cover - infra
            logger.warning("Redis unavailable ({}); cache disabled", exc)
            self.client = None

    async def close(self) -> None:
        if self.client is not None:
            await self.client.close()

    async def get_json(self, key: str) -> Any | None:
        if self.client is None:
            return None
        raw = await self.client.get(key)
        return json.loads(raw) if raw else None

    async def set_json(self, key: str, value: Any, ttl: int | None = None) -> None:
        if self.client is None:
            return
        await self.client.set(key, json.dumps(value, default=str),
                              ex=ttl or settings.CACHE_TTL_SECONDS)

    async def delete_prefix(self, prefix: str) -> None:
        """Invalidate every key under a prefix (used after writes)."""
        if self.client is None:
            return
        async for key in self.client.scan_iter(match=f"{prefix}*"):
            await self.client.delete(key)


cache = _Cache()
