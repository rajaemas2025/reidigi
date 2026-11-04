from datetime import datetime

from sqlalchemy import JSON, BigInteger, ForeignKey, Integer, String, Text
from sqlalchemy.orm import Mapped, mapped_column

from app.core import clock
from app.core.db import Base, BigId, UTCDateTime


class Payment(Base):
    """Pembayaran masuk lewat iFortepay (QRIS atau VA)."""

    __tablename__ = "payments"

    id: Mapped[int] = mapped_column(BigId, primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"), index=True)
    purpose: Mapped[str] = mapped_column(String(20))  # ORDER, FINANCING_DP
    reference_id: Mapped[int] = mapped_column(BigInteger, index=True)
    method: Mapped[str] = mapped_column(String(20))
    amount: Mapped[int] = mapped_column(BigInteger)
    # PENDING, PAID, CANCELLED, EXPIRED, REFUNDED (masuk setelah kedaluwarsa, dikembalikan ke saldo)
    status: Mapped[str] = mapped_column(String(16))
    provider_ref: Mapped[str] = mapped_column(String(64), unique=True)
    payment_code: Mapped[str] = mapped_column(String(128))
    expires_at: Mapped[datetime] = mapped_column(UTCDateTime)
    paid_at: Mapped[datetime | None] = mapped_column(UTCDateTime)
    last_callback: Mapped[dict | None] = mapped_column(JSON)
    created_at: Mapped[datetime] = mapped_column(UTCDateTime, default=clock.now)


class Disbursement(Base):
    """Pencairan dana keluar ke rekening pengguna. Satu penarikan bisa punya beberapa percobaan."""

    __tablename__ = "disbursements"

    id: Mapped[int] = mapped_column(BigId, primary_key=True)
    withdrawal_id: Mapped[int] = mapped_column(ForeignKey("withdrawals.id"), index=True)
    attempt: Mapped[int] = mapped_column(Integer)
    amount: Mapped[int] = mapped_column(BigInteger)
    # PENDING (belum dikirim), SENT, SUCCESS, FAILED, UNKNOWN
    status: Mapped[str] = mapped_column(String(16))
    provider_ref: Mapped[str | None] = mapped_column(String(64), unique=True)
    failure_reason: Mapped[str | None] = mapped_column(Text)
    created_at: Mapped[datetime] = mapped_column(UTCDateTime, default=clock.now)
    updated_at: Mapped[datetime] = mapped_column(UTCDateTime, default=clock.now)
