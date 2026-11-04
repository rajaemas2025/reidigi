import uuid
from datetime import datetime

from sqlalchemy import BigInteger, ForeignKey, Index, LargeBinary, String, Text, Uuid, text
from sqlalchemy.orm import Mapped, mapped_column

from app.core import clock
from app.core.db import Base, BigId, UTCDateTime

FINAL_STATUSES = ("RETURNED", "PRINTED", "CANCELLED")
_OPEN = text("status NOT IN ('RETURNED', 'PRINTED', 'CANCELLED')")


class Token(Base):
    __tablename__ = "tokens"
    __table_args__ = (
        # Penjaga utama invarian #1: satu unit hanya boleh punya satu token yang masih berjalan.
        Index("uq_unit_one_open_token", "unit_id", unique=True, sqlite_where=_OPEN, postgresql_where=_OPEN),
    )

    id: Mapped[uuid.UUID] = mapped_column(Uuid, primary_key=True, default=uuid.uuid4)
    token_code: Mapped[str] = mapped_column(String(40), unique=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"), index=True)
    unit_id: Mapped[int] = mapped_column(ForeignKey("units.id"))
    sku_id: Mapped[int] = mapped_column(ForeignKey("skus.id"))
    status: Mapped[str] = mapped_column(String(20))
    acquired_price: Mapped[int] = mapped_column(BigInteger)
    # ORDER atau FINANCING. Spesifikasi menulis origin_order_id; token cicilan tidak lahir dari order.
    origin_type: Mapped[str] = mapped_column(String(16))
    origin_id: Mapped[int] = mapped_column(BigInteger)
    created_at: Mapped[datetime] = mapped_column(UTCDateTime, default=clock.now)
    closed_at: Mapped[datetime | None] = mapped_column(UTCDateTime)


class TokenEvent(Base):
    """Rantai hash per token: hash = sha256(prev_hash + isi event). Perubahan diam-diam akan memutus rantai."""

    __tablename__ = "token_events"

    id: Mapped[int] = mapped_column(BigId, primary_key=True)
    token_id: Mapped[uuid.UUID] = mapped_column(Uuid, ForeignKey("tokens.id"), index=True)
    from_status: Mapped[str | None] = mapped_column(String(20))
    to_status: Mapped[str] = mapped_column(String(20))
    actor_type: Mapped[str] = mapped_column(String(16))
    actor_id: Mapped[str | None] = mapped_column(String(64))
    reason: Mapped[str | None] = mapped_column(Text)
    prev_hash: Mapped[bytes | None] = mapped_column(LargeBinary)
    hash: Mapped[bytes] = mapped_column(LargeBinary)
    created_at: Mapped[datetime] = mapped_column(UTCDateTime, default=clock.now)
