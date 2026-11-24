"""Device tokens and group topic subscriptions.

A user's tokens are subscribed to ``group_{id}`` for every group they're in,
as long as they want group notifications (preferences.notify.groups).
"""
from __future__ import annotations

import uuid

from app.models.activity import DeviceToken, Notification
from app.models.user import User
from app.repositories.base import BaseRepository
from app.repositories.device_repo import device_repository
from app.repositories.group_repo import membership_repository
from app.repositories.user_repo import user_repository
from app.schemas.preferences import Preferences
from app.services import push_service

_notifications = BaseRepository(Notification)


def wants_group_pushes(user: User) -> bool:
    return Preferences.from_stored(user.preferences).notify.groups


class DeviceService:
    async def register(self, user: User, token: str, platform: str = "android") -> None:
        """Add (or move to this user) a device's FCM token and subscribe it
        to the user's group topics."""
        row = await device_repository.by_token(token)
        if row is None:
            await device_repository.create(DeviceToken(user_id=user.id, fcm_token=token,
                                                       platform=platform, created_by=user.id))
        else:
            if row.user_id != user.id and not row.deleted:
                # The phone changed hands (log out / log in as someone else).
                await self._topics(row.user_id, [token], subscribe=False)
            row.user_id, row.platform, row.deleted = user.id, platform, False
            await device_repository.save(row)
        if wants_group_pushes(user):
            await self._topics(user.id, [token], subscribe=True)

    async def unregister(self, user: User, token: str) -> None:
        row = await device_repository.by_token(token)
        if row is None or row.user_id != user.id or row.deleted:
            return
        await self._topics(user.id, [token], subscribe=False)
        await device_repository.soft_delete(row)

    async def tokens(self, user_id: uuid.UUID) -> list[str]:
        return (await device_repository.tokens_for({user_id})).get(user_id, [])

    async def subscribe_user(self, user_id: uuid.UUID, group_id: uuid.UUID) -> None:
        user = await user_repository.get(user_id)
        if user is not None and wants_group_pushes(user):
            await push_service.subscribe(await self.tokens(user_id), push_service.group_topic(group_id))

    async def unsubscribe_user(self, user_id: uuid.UUID, group_id: uuid.UUID) -> None:
        await push_service.unsubscribe(await self.tokens(user_id), push_service.group_topic(group_id))

    async def sync_user(self, user: User) -> None:
        """After notify.groups changes: (un)subscribe every group topic."""
        await self._topics(user.id, await self.tokens(user.id), subscribe=wants_group_pushes(user))

    async def _topics(self, user_id: uuid.UUID, tokens: list[str], *, subscribe: bool) -> None:
        if not tokens:
            return
        fn = push_service.subscribe if subscribe else push_service.unsubscribe
        for gid in await membership_repository.active_group_ids(user_id):
            await fn(tokens, push_service.group_topic(gid))

    async def notify(self, user_ids, type_: str, title: str, body: str, data: dict) -> None:
        """A personal notification: kept in the in-app inbox, and pushed to the
        person's phones when they want group notifications."""
        user_ids = set(user_ids)
        if not user_ids:
            return
        people = await user_repository.get_many(user_ids)
        tokens = await device_repository.tokens_for(user_ids)
        payload = {"type": type_, **data}
        for uid in user_ids:
            await _notifications.create(Notification(user_id=uid, type=type_, title=title, body=body,
                                                     data=payload))
            person = people.get(uid)
            if person is not None and wants_group_pushes(person):
                await push_service.send_to_tokens(tokens.get(uid, []), title, body, payload)


device_service = DeviceService()
