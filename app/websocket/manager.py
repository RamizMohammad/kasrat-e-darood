"""In-process WebSocket connection manager.

Broadcasts realtime events (submission.created, leaderboard.updated, …) to all
sockets subscribed to a group. For multi-instance deployments back this with a
Redis pub/sub fan-out — the public API stays the same.
"""
from __future__ import annotations

from collections import defaultdict

from fastapi import WebSocket


class ConnectionManager:
    def __init__(self) -> None:
        self._rooms: dict[str, set[WebSocket]] = defaultdict(set)

    async def connect(self, group_id: str, websocket: WebSocket) -> None:
        await websocket.accept()
        self._rooms[group_id].add(websocket)

    def disconnect(self, group_id: str, websocket: WebSocket) -> None:
        self._rooms[group_id].discard(websocket)

    async def broadcast(self, group_id: str, event: dict) -> None:
        dead = []
        for ws in self._rooms.get(group_id, set()):
            try:
                await ws.send_json(event)
            except Exception:
                dead.append(ws)
        for ws in dead:
            self.disconnect(group_id, ws)


manager = ConnectionManager()
