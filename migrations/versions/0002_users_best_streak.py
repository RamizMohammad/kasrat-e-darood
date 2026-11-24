"""users.best_streak, and fill in streaks from existing logs

streak_days was never updated before B1. This adds best_streak and computes
both from each user's submissions (days in Asia/Kolkata, the app's zone).

Revision ID: 0002
Revises: 0001
Create Date: 2026-11-24 13:34:33.059256+00:00
"""
from __future__ import annotations

import sqlalchemy as sa
from alembic import op


revision = '0002'
down_revision = '0001'
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.add_column('users', sa.Column('best_streak', sa.BigInteger(), server_default='0', nullable=False))
    op.execute("""
        WITH days AS (
            SELECT DISTINCT user_id, (created_at AT TIME ZONE 'Asia/Kolkata')::date AS d
            FROM submissions WHERE deleted = false
        ), runs AS (
            SELECT user_id, d,
                   d - (row_number() OVER (PARTITION BY user_id ORDER BY d))::int AS grp
            FROM days
        ), islands AS (
            SELECT user_id, max(d) AS last_day, count(*) AS len
            FROM runs GROUP BY user_id, grp
        ), per_user AS (
            SELECT user_id,
                   max(len) AS best,
                   coalesce(max(len) FILTER (
                       WHERE last_day >= (now() AT TIME ZONE 'Asia/Kolkata')::date - 1), 0) AS current
            FROM islands GROUP BY user_id
        )
        UPDATE users u SET best_streak = p.best, streak_days = p.current
        FROM per_user p WHERE p.user_id = u.id
    """)


def downgrade() -> None:
    op.drop_column('users', 'best_streak')
