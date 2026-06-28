"""Firebase Cloud Messaging push helpers.

Notifications are broadcast to a single FCM **topic** (``community``) that every
installed app subscribes to. Using ``notification`` messages means the Android
system tray shows them even when the app is in the background or fully closed.

If Firebase credentials are not configured (local/dev), sends are skipped with a
warning so the rest of the stack keeps working.
"""
from __future__ import annotations

import asyncio

from loguru import logger

from app.config.settings import settings

COMMUNITY_TOPIC = "community"


def _send_sync(title: str, body: str, data: dict | None = None) -> None:
    if not settings.FIREBASE_CREDENTIALS_FILE:
        logger.warning("FCM skipped (no Firebase credentials): {}", title)
        return
    try:  # pragma: no cover - requires real credentials
        from firebase_admin import messaging

        message = messaging.Message(
            topic=COMMUNITY_TOPIC,
            notification=messaging.Notification(title=title, body=body),
            data={k: str(v) for k, v in (data or {}).items()},
            android=messaging.AndroidConfig(
                priority="high",
                notification=messaging.AndroidNotification(
                    channel_id="noor_default",
                    default_sound=True,
                ),
            ),
        )
        messaging.send(message)
        logger.info("FCM sent to topic '{}': {}", COMMUNITY_TOPIC, title)
    except Exception as exc:  # pragma: no cover
        logger.error("FCM send failed: {}", exc)


async def send_to_community(title: str, body: str, data: dict | None = None) -> None:
    """Broadcast a notification to every member (the ``community`` topic)."""
    await asyncio.to_thread(_send_sync, title, body, data)
