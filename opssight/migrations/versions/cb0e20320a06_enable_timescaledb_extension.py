"""enable timescaledb extension

Revision ID: cb0e20320a06
Revises: c35a97784f93
Create Date: 2026-08-23 19:02:35.851187

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision: str = 'cb0e20320a06'
down_revision: Union[str, Sequence[str], None] = 'c35a97784f93'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    """Upgrade schema."""
    op.execute("CREATE EXTENSION IF NOT EXISTS timescaledb")
    pass


def downgrade() -> None:
    """Downgrade schema."""
    op.execute("DROP EXTENSION IF EXISTS timescaledb")
    pass
