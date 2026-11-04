from datetime import datetime

from sqlalchemy import JSON, ForeignKey, Integer, String, Text, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column

from app.core import clock
from app.core.db import Base, BigId, UTCDateTime


class IdempotencyKey(Base):
    __tablename__ = "idempotency_keys"
    __table_args__ = (UniqueConstraint("scope", "key"),)

    id: Mapped[int] = mapped_column(BigId, primary_key=True)
    scope: Mapped[str] = mapped_column(String(64))
    key: Mapped[str] = mapped_column(String(128))
    request_hash: Mapped[str] = mapped_column(String(64))
    response: Mapped[dict] = mapped_column(JSON)
    created_at: Mapped[datetime] = mapped_column(UTCDateTime, default=clock.now)


class OutboxEvent(Base):
    """Peristiwa untuk sistem lain (notifikasi, alert). Ditulis dalam transaksi yang sama dengan perubahannya."""

    __tablename__ = "outbox_events"

    id: Mapped[int] = mapped_column(BigId, primary_key=True)
    topic: Mapped[str] = mapped_column(String(64), index=True)
    payload: Mapped[dict] = mapped_column(JSON)
    created_at: Mapped[datetime] = mapped_column(UTCDateTime, default=clock.now)
    published_at: Mapped[datetime | None] = mapped_column(UTCDateTime)


class SystemFlag(Base):
    """Saklar operasional: henti perdagangan, bekukan penarikan."""

    __tablename__ = "system_flags"

    key: Mapped[str] = mapped_column(String(64), primary_key=True)
    enabled: Mapped[bool] = mapped_column(default=False)
    reason: Mapped[str | None] = mapped_column(Text)
    set_by_admin_id: Mapped[int | None] = mapped_column(ForeignKey("admin_users.id"))
    set_by_system: Mapped[bool] = mapped_column(default=False)
    updated_at: Mapped[datetime] = mapped_column(UTCDateTime, default=clock.now)
    version: Mapped[int] = mapped_column(Integer, default=0)


def emit(db, topic: str, **payload) -> None:
    db.add(OutboxEvent(topic=topic, payload=payload))
