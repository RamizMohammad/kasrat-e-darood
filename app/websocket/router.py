"""WebSocket endpoint for realtime group events."""
from __future__ import annotations

from fastapi import APIRouter, WebSocket, WebSocketDisconnect

from app.websocket.manager import manager

router = APIRouter()


@router.websocket("/ws")
async def group_socket(websocket: WebSocket, group_id: str) -> None:
    await manager.connect(group_id, websocket)
    try:
        while True:
            # Keep the socket open; inbound messages are currently ignored.
            await websocket.receive_text()
    except WebSocketDisconnect:
        manager.disconnect(group_id, websocket)
