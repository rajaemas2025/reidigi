from datetime import datetime

from sqlalchemy import BigInteger, CheckConstraint, ForeignKey, Integer, String, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column

from app.core import clock
from app.core.db import Base, BigId, UTCDateTime


class Wallet(Base):
    """Read-model saldo. Sumber kebenaran tetap wallet_entries dan ledger, dicocokkan oleh rekonsiliasi."""

    __tablename__ = "wallets"
    __table_args__ = (
        CheckConstraint("available >= 0", name="ck_wallet_available_nonneg"),
        CheckConstraint("held >= 0", name="ck_wallet_held_nonneg"),
    )

    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"), primary_key=True)
    available: Mapped[int] = mapped_column(BigInteger, default=0)
    held: Mapped[int] = mapped_column(BigInteger, default=0)
    version: Mapped[int] = mapped_column(Integer, default=0)

    __mapper_args__ = {"version_id_col": version}


class WalletEntry(Base):
    """
    amount bertanda. Untuk semua tipe selain WITHDRAW_SETTLE, amount mengubah saldo tersedia.
    HOLD (-) memindahkan ke saldo tertahan, RELEASE (+) mengembalikan, SETTLE (-) mengeluarkan dari saldo tertahan.
    available = jumlah semua entri kecuali SETTLE; held = -(HOLD + RELEASE) + SETTLE.
    """

    __tablename__ = "wallet_entries"
    __table_args__ = (UniqueConstraint("type", "reference_type", "reference_id"),)

    id: Mapped[int] = mapped_column(BigId, primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("wallets.user_id"), index=True)
    # SALE_CREDIT, PURCHASE_DEBIT, TOPUP, WITHDRAW_HOLD, WITHDRAW_RELEASE, WITHDRAW_SETTLE, REFUND
    type: Mapped[str] = mapped_column(String(20))
    amount: Mapped[int] = mapped_column(BigInteger)
    reference_type: Mapped[str] = mapped_column(String(32))
    reference_id: Mapped[str] = mapped_column(String(64))
    withdrawable_at: Mapped[datetime | None] = mapped_column(UTCDateTime)
    created_at: Mapped[datetime] = mapped_column(UTCDateTime, default=clock.now)


class Topup(Base):
    __tablename__ = "topups"

    id: Mapped[int] = mapped_column(BigId, primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"), index=True)
    amount: Mapped[int] = mapped_column(BigInteger)
    status: Mapped[str] = mapped_column(String(16))
    source: Mapped[str] = mapped_column(String(32))
    created_at: Mapped[datetime] = mapped_column(UTCDateTime, default=clock.now)


class Withdrawal(Base):
    __tablename__ = "withdrawals"

    id: Mapped[int] = mapped_column(BigId, primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"), index=True)
    bank_account_id: Mapped[int] = mapped_column(ForeignKey("bank_accounts.id"))
    amount: Mapped[int] = mapped_column(BigInteger)
    # PROCESSING, SUCCESS, FAILED, UNKNOWN
    status: Mapped[str] = mapped_column(String(16), index=True)
    attempts: Mapped[int] = mapped_column(Integer, default=1)
    created_at: Mapped[datetime] = mapped_column(UTCDateTime, default=clock.now)
    updated_at: Mapped[datetime] = mapped_column(UTCDateTime, default=clock.now)
