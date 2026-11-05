"""verifikasi email dan tanggal lahir pengguna

Revision ID: 0002
Revises: 0001
Create Date: 2026-11-04 16:00:00
"""
from alembic import op
import sqlalchemy as sa
import app.core.db


revision = '0002'
down_revision = '0001'
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table('email_codes',
    sa.Column('id', sa.BigInteger().with_variant(sa.Integer(), 'sqlite'), nullable=False),
    sa.Column('email', sa.String(length=254), nullable=False),
    sa.Column('purpose', sa.String(length=16), nullable=False),
    sa.Column('code_hash', sa.String(length=200), nullable=False),
    sa.Column('attempts', sa.Integer(), nullable=False),
    sa.Column('created_at', app.core.db.UTCDateTime(timezone=True), nullable=False),
    sa.Column('expires_at', app.core.db.UTCDateTime(timezone=True), nullable=False),
    sa.Column('verified_at', app.core.db.UTCDateTime(timezone=True), nullable=True),
    sa.Column('ticket_hash', sa.String(length=64), nullable=True),
    sa.Column('consumed_at', app.core.db.UTCDateTime(timezone=True), nullable=True),
    sa.PrimaryKeyConstraint('id'),
    sa.UniqueConstraint('ticket_hash')
    )
    with op.batch_alter_table('email_codes', schema=None) as batch_op:
        batch_op.create_index(batch_op.f('ix_email_codes_email'), ['email'], unique=False)

    with op.batch_alter_table('users', schema=None) as batch_op:
        batch_op.add_column(sa.Column('date_of_birth', sa.Date(), nullable=True))


def downgrade() -> None:
    with op.batch_alter_table('users', schema=None) as batch_op:
        batch_op.drop_column('date_of_birth')

    with op.batch_alter_table('email_codes', schema=None) as batch_op:
        batch_op.drop_index(batch_op.f('ix_email_codes_email'))

    op.drop_table('email_codes')
