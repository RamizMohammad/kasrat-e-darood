"""Add an existing account to a group, for testing before the app can join (P6).

    docker exec -it kasrat-api python -m scripts_add_member K7Q2XM friend@example.com

Same rules as a real join (not banned, not already in, group not full);
the join shows in the group's Activity. Codes are case-insensitive.
"""
from __future__ import annotations

import asyncio
import sys

from app.core.exceptions import AppError
from app.database.postgres import close_postgres, session_scope
from app.repositories.user_repo import user_repository
from app.services.join_service import _by_code, join_service


async def main(code: str, email: str) -> int:
    try:
        async with session_scope():
            user = await user_repository.get_by_email(email.strip().lower())
            if user is None:
                print(f"No account with email {email}")
                return 1
            group = await _by_code(code)
            await join_service.add_member(group, user, by=user)
            print(f"Added {user.display_name} <{email}> to {group.name} ({group.member_count} members)")
            return 0
    except AppError as e:
        print(f"Not added: {e.message} ({e.code})")
        return 1
    finally:
        await close_postgres()


if __name__ == "__main__":
    if len(sys.argv) != 3:
        print(__doc__)
        sys.exit(2)
    sys.exit(asyncio.run(main(sys.argv[1], sys.argv[2])))
