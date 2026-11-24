"""Firebase Cloud Messaging.

* ``community`` topic: every installed app subscribes itself (v2 + v3).
* ``group_{id}`` topics: the server subscribes a user's registered device
  tokens when they join a group and unsubscribes them when they leave, are
  removed or banned (POST /users/me/devices registers a token).
* Direct pushes to one user's tokens (join requests, decisions).

``notification`` messages, so the system tray shows them with the app closed.
Without Firebase credentials (local / tests) nothing is sent; each call is
recorded in ``outbox`` instead so tests can see what would have gone out.
"""
from __future__ import annotations

import asyncio
from collections import deque

from loguru import logger

from app.config.settings import settings

COMMUNITY_TOPIC = "community"

# What would have been sent when Firebase isn't configured: dicts with
# kind = "topic" | "tokens" | "subscribe" | "unsubscribe".
outbox: deque[dict] = deque(maxlen=500)


def group_topic(group_id) -> str:
    return f"group_{group_id}"


def _enabled() -> bool:
    return bool(settings.FIREBASE_CREDENTIALS_FILE)


def _message(title: str, body: str, data: dict | None, **target):  # pragma: no cover - needs Firebase
    from firebase_admin import messaging
    return messaging.Message(
        notification=messaging.Notification(title=title, body=body),
        data={k: str(v) for k, v in (data or {}).items()},
        android=messaging.AndroidConfig(
            priority="high",
            notification=messaging.AndroidNotification(channel_id="noor_default", default_sound=True),
        ),
        **target,
    )


def _send_topic_sync(topic: str, title: str, body: str, data: dict | None) -> None:
    if not _enabled():
        outbox.append({"kind": "topic", "topic": topic, "title": title, "body": body, "data": data or {}})
        logger.debug("FCM skipped (no credentials): {} → {}", topic, title)
        return
    try:  # pragma: no cover
        from firebase_admin import messaging
        messaging.send(_message(title, body, data, topic=topic))
        logger.info("FCM sent to topic '{}': {}", topic, title)
    except Exception as exc:  # pragma: no cover
        logger.error("FCM send to {} failed: {}", topic, exc)


def _send_tokens_sync(tokens: list[str], title: str, body: str, data: dict | None) -> None:
    if not tokens:
        return
    if not _enabled():
        outbox.append({"kind": "tokens", "tokens": list(tokens), "title": title, "body": body,
                       "data": data or {}})
        return
    try:  # pragma: no cover
        from firebase_admin import messaging
        msg = messaging.MulticastMessage(
            tokens=list(tokens),
            notification=messaging.Notification(title=title, body=body),
            data={k: str(v) for k, v in (data or {}).items()},
            android=messaging.AndroidConfig(priority="high", notification=messaging.AndroidNotification(
                channel_id="noor_default", default_sound=True)),
        )
        messaging.send_each_for_multicast(msg)
    except Exception as exc:  # pragma: no cover
        logger.error("FCM direct send failed: {}", exc)


def _topic_sync(subscribe: bool, tokens: list[str], topic: str) -> None:
    if not tokens:
        return
    if not _enabled():
        outbox.append({"kind": "subscribe" if subscribe else "unsubscribe",
                       "topic": topic, "tokens": list(tokens)})
        return
    try:  # pragma: no cover
        from firebase_admin import messaging
        fn = messaging.subscribe_to_topic if subscribe else messaging.unsubscribe_from_topic
        # FCM takes at most 1000 tokens per call.
        for i in range(0, len(tokens), 1000):
            fn(tokens[i:i + 1000], topic)
    except Exception as exc:  # pragma: no cover
        logger.error("FCM topic {} {} failed: {}", "subscribe" if subscribe else "unsubscribe", topic, exc)


# ── async API (FCM calls are blocking HTTP: run them off the event loop) ────

async def send_to_community(title: str, body: str, data: dict | None = None) -> None:
    """Broadcast to every member (the ``community`` topic)."""
    await asyncio.to_thread(_send_topic_sync, COMMUNITY_TOPIC, title, body, data)


async def send_to_topic(topic: str, title: str, body: str, data: dict | None = None) -> None:
    await asyncio.to_thread(_send_topic_sync, topic, title, body, data)


async def send_to_tokens(tokens: list[str], title: str, body: str, data: dict | None = None) -> None:
    await asyncio.to_thread(_send_tokens_sync, tokens, title, body, data)


async def subscribe(tokens: list[str], topic: str) -> None:
    await asyncio.to_thread(_topic_sync, True, tokens, topic)


async def unsubscribe(tokens: list[str], topic: str) -> None:
    await asyncio.to_thread(_topic_sync, False, tokens, topic)
