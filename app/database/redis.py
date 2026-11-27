"""Redis cache client and small helper for cached read-models."""
from __future__ import annotations

import json
from typing import Any

import redis.asyncio as aioredis
from loguru import logger

from app.config.settings import settings


# central-redis is shared with other services: every key this app writes
# starts with this, so we never read, overwrite or delete anyone else's keys.
KEY_PREFIX = "kasrat:"


def k(key: str) -> str:
    """The namespaced Redis key for ``key``."""
    return KEY_PREFIX + key


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
        raw = await self.client.get(k(key))
        return json.loads(raw) if raw else None

    async def set_json(self, key: str, value: Any, ttl: int | None = None) -> None:
        if self.client is None:
            return
        await self.client.set(k(key), json.dumps(value, default=str),
                              ex=ttl or settings.CACHE_TTL_SECONDS)

    async def delete_prefix(self, prefix: str) -> None:
        """Invalidate every key under a prefix (used after writes)."""
        if self.client is None:
            return
        # Glob characters in the prefix are escaped so it only matches itself.
        pattern = k(prefix).replace("\\", "\\\\")
        for ch in "*?[]":
            pattern = pattern.replace(ch, "\\" + ch)
        batch: list[str] = []
        async for key in self.client.scan_iter(match=pattern + "*", count=500):
            batch.append(key)
            if len(batch) >= 500:
                await self.client.delete(*batch)
                batch = []
        if batch:
            await self.client.delete(*batch)

    async def hit(self, key: str, window_seconds: int) -> int:
        """Count one event in a fixed window; returns the count so far.

        The expiry is set together with the first increment, and repaired if a
        key ever ends up without one, so a counter can never block forever.
        """
        if self.client is None:
            return 0
        name = k(key)
        async with self.client.pipeline(transaction=True) as pipe:
            pipe.incr(name)
            pipe.ttl(name)
            count, ttl = await pipe.execute()
        if ttl is None or ttl < 0:
            await self.client.expire(name, window_seconds)
        return int(count)

    async def count(self, key: str) -> int:
        """Current value of a counter made by ``hit`` (0 if none)."""
        if self.client is None:
            return 0
        raw = await self.client.get(k(key))
        return int(raw) if raw else 0

    async def claim(self, key: str, ttl_seconds: int) -> bool:
        """True the first time in ``ttl_seconds`` (a cooldown); True as well
        when Redis is down, so features keep working."""
        if self.client is None:
            return True
        return bool(await self.client.set(k(key), "1", nx=True, ex=ttl_seconds))

    async def clear(self, key: str) -> None:
        if self.client is not None:
            await self.client.delete(k(key))


cache = _Cache()
