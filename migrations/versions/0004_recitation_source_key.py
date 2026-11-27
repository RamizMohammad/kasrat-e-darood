"""recitations.source_key: where an imported recitation came from

Used by scripts_import_library to recognise rows it already imported (or
adopted from the hand-written seed), so re-running it never duplicates. The
unique index is the database-level guarantee.

Revision ID: 0004
Revises: 0003
"""
from __future__ import annotations

import sqlalchemy as sa
from alembic import op


revision = '0004'
down_revision = '0003'
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.add_column('recitations', sa.Column('source_key', sa.String(length=120), nullable=True))
    op.create_index(
        'uq_recitations_source_key', 'recitations', ['source_key'], unique=True,
        postgresql_where=sa.text('source_key IS NOT NULL AND deleted = false'))


def downgrade() -> None:
    op.drop_index('uq_recitations_source_key', table_name='recitations')
    op.drop_column('recitations', 'source_key')
