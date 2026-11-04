from datetime import datetime

from sqlalchemy import BigInteger, CheckConstraint, ForeignKey, Integer, String, Text
from sqlalchemy.orm import Mapped, mapped_column

from app.core import clock
from app.core.db import Base, BigId, UTCDateTime


class LedgerAccount(Base):
    __tablename__ = "ledger_accounts"

    code: Mapped[str] = mapped_column(String(48), primary_key=True)
    name: Mapped[str] = mapped_column(String(120))
    type: Mapped[str] = mapped_column(String(16))  # ASSET, LIABILITY, REVENUE, EXPENSE
    normal_side: Mapped[str] = mapped_column(String(6))  # DEBIT, CREDIT


class LedgerEntry(Base):
    """Satu baris jurnal. Baris dengan journal_id yang sama wajib seimbang (invarian #8)."""

    __tablename__ = "ledger_entries"
    __table_args__ = (
        CheckConstraint("debit >= 0 AND credit >= 0", name="ck_ledger_nonneg"),
        CheckConstraint("(debit = 0) <> (credit = 0)", name="ck_ledger_one_side"),
    )

    id: Mapped[int] = mapped_column(BigId, primary_key=True)
    journal_id: Mapped[str] = mapped_column(String(36), index=True)
    account_code: Mapped[str] = mapped_column(ForeignKey("ledger_accounts.code"), index=True)
    debit: Mapped[int] = mapped_column(BigInteger, default=0)
    credit: Mapped[int] = mapped_column(BigInteger, default=0)
    reference_type: Mapped[str] = mapped_column(String(32))
    reference_id: Mapped[str] = mapped_column(String(64))
    memo: Mapped[str | None] = mapped_column(Text)
    created_at: Mapped[datetime] = mapped_column(UTCDateTime, default=clock.now)


class TaxRule(Base):
    __tablename__ = "tax_rules"

    code: Mapped[str] = mapped_column(String(32), primary_key=True)
    description: Mapped[str] = mapped_column(Text)
    # Tarif dalam basis poin (150 = 1,5%). Dibulatkan ke bawah per transaksi.
    rate_bps: Mapped[int] = mapped_column(Integer)
    # Berlaku bila nilai bruto transaksi melebihi angka ini.
    threshold: Mapped[int] = mapped_column(BigInteger, default=0)
    is_active: Mapped[bool] = mapped_column(default=True)
