"""Notification feed + system broadcasts.

The in-app Notifications screen has three sections, all backed by the
``activity_feed`` collection via the ActivityItem ``type``:

  * activity        -> "submission.created"  (group submissions feed)
  * notifications   -> "system.reminder"     (Islamic quotes / motivation)
  * considerations  -> "week.locked"         (a week was finalized/archived)
"""
from __future__ import annotations

import random

from beanie import PydanticObjectId

from app.models.activity import ActivityItem
from app.models.user import User
from app.repositories.activity_repo import activity_repository
from app.repositories.user_repo import user_repository
from app.schemas.dashboard import FeedItemOut
from app.services.community_service import community_service
from app.services.push_service import send_to_community

# Which ActivityItem types feed each section of the Notifications screen.
CATEGORY_TYPES: dict[str, set[str]] = {
    "activity": {"submission.created"},
    "notifications": {"system.reminder"},
    "considerations": {"week.locked"},
}

# Short, widely-known translations used for the periodic motivational push.
ISLAMIC_QUOTES: list[dict[str, str]] = [
    {"title": "Remembrance", "body": "Verily, in the remembrance of Allah do hearts find rest. (Qur'an 13:28)"},
    {"title": "Patience", "body": "Indeed, Allah is with the patient. (Qur'an 2:153)"},
    {"title": "Hope", "body": "Do not despair of the mercy of Allah. (Qur'an 39:53)"},
    {"title": "Gratitude", "body": "If you are grateful, I will surely increase you. (Qur'an 14:7)"},
    {"title": "Ease", "body": "Indeed, with hardship comes ease. (Qur'an 94:6)"},
    {"title": "Dhikr", "body": "The best of remembrance is La ilaha illa Allah."},
    {"title": "Reliance", "body": "And whoever relies upon Allah - then He is sufficient for him. (Qur'an 65:3)"},
    {"title": "Prayer", "body": "Establish prayer for My remembrance. (Qur'an 20:14)"},
    {"title": "Kindness", "body": "The most beloved of people to Allah are those most beneficial to others. (Hadith)"},
    {"title": "Forgiveness", "body": "Seek the forgiveness of your Lord and turn to Him in repentance. (Qur'an 11:90)"},
]


class NotificationService:
    async def feed(
        self, user: User, category: str, limit: int = 50
    ) -> list[FeedItemOut]:
        """Recent feed entries for one section, enriched with actor display info."""
        group_id = await community_service.resolve_group(user, None)
        types = CATEGORY_TYPES.get(category)

        # Pull a generous window, then filter to the requested category.
        activities = await activity_repository.recent(group_id, limit=200)
        items: list[FeedItemOut] = []
        names: dict = {}
        for a in activities:
            if types is not None and a.type not in types:
                continue
            name = names.get(a.actor_id)
            if name is None:
                actor = await user_repository.get(a.actor_id)
                name = actor.display_name if actor else "Noor"
                names[a.actor_id] = name
            initial = (name.strip()[:1] or "•").upper()
            items.append(FeedItemOut(
                id=a.id, actor_id=a.actor_id, actor_name=name, initial=initial,
                type=a.type, text=a.text, reactions=a.reactions,
                created_at=a.created_at,
            ))
            if len(items) >= limit:
                break
        return items

    async def record_and_push(
        self, group_id: PydanticObjectId, actor_id: PydanticObjectId,
        type_: str, title: str, text: str, data: dict | None = None,
    ) -> None:
        """Persist an activity entry (so it shows in-app) and push it to everyone."""
        await ActivityItem(
            group_id=group_id, actor_id=actor_id, type=type_, text=text,
            payload=data or {}, created_by=actor_id,
        ).insert()
        await send_to_community(title, text, data)

    async def broadcast_quote(self) -> None:
        """Send a random Islamic quote / motivation to the whole community."""
        group = await community_service.get_or_create_global_group()
        quote = random.choice(ISLAMIC_QUOTES)
        await self.record_and_push(
            group.id, group.owner_id, "system.reminder",
            quote["title"], quote["body"],
        )


notification_service = NotificationService()
