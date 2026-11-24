"""In-app inbox."""
from __future__ import annotations

import uuid
from datetime import datetime

from app.schemas.common import ORMModel


class InboxItem(ORMModel):
    id: uuid.UUID
    type: str
    title: str
    body: str
    data: dict = {}
    read: bool = False
    created_at: datetime
