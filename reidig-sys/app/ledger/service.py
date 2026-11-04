"""Pembukuan double-entry internal. Semua jurnal wajib seimbang sebelum ditulis."""

import uuid

from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.ledger.models import LedgerAccount, LedgerEntry, TaxRule

# Kode akun. Bagan akun final perlu ditinjau tim akuntansi.
CUSTOMER_FUNDS = "ASSET_CUSTOMER_FUNDS_BANK"  # rekening penampungan dana pengguna
OPERATING = "ASSET_OPERATING_BANK"
PG_CLEARING = "ASSET_PG_CLEARING"  # dana di iFortepay yang belum diselesaikan ke rekening
FINANCING_RECEIVABLE = "ASSET_FINANCING_RECEIVABLE"
WALLET_LIABILITY = "LIABILITY_USER_WALLET"
TAX_PAYABLE = "LIABILITY_TAX_PAYABLE"
GOLD_SALES = "REVENUE_GOLD_SALES"
GOLD_BUYBACK = "EXPENSE_GOLD_BUYBACK"

ACCOUNTS = [
    (CUSTOMER_FUNDS, "Rekening penampungan dana pengguna", "ASSET", "DEBIT"),
    (OPERATING, "Rekening operasional", "ASSET", "DEBIT"),
    (PG_CLEARING, "Kliring payment gateway", "ASSET", "DEBIT"),
    (FINANCING_RECEIVABLE, "Piutang mitra pembiayaan", "ASSET", "DEBIT"),
    (WALLET_LIABILITY, "Kewajiban saldo pengguna", "LIABILITY", "CREDIT"),
    (TAX_PAYABLE, "Utang pajak (PPh 22)", "LIABILITY", "CREDIT"),
    (GOLD_SALES, "Penjualan emas", "REVENUE", "CREDIT"),
    (GOLD_BUYBACK, "Pembelian kembali emas", "EXPENSE", "DEBIT"),
]


class UnbalancedJournal(Exception):
    pass


def post(db: Session, lines: list[tuple[str, int, int]], reference_type: str, reference_id, memo: str = "") -> str:
    """lines: (kode akun, debit, kredit). Baris bernilai nol dilewati."""
    lines = [(a, d, c) for a, d, c in lines if d or c]
    if not lines:
        raise UnbalancedJournal("Jurnal kosong")
    if any(d < 0 or c < 0 or (d and c) for _, d, c in lines):
        raise UnbalancedJournal("Setiap baris hanya boleh debit atau kredit, tidak negatif")
    if sum(d for _, d, _ in lines) != sum(c for _, _, c in lines):
        raise UnbalancedJournal("Total debit tidak sama dengan total kredit")
    journal_id = str(uuid.uuid4())
    for account, debit, credit in lines:
        db.add(
            LedgerEntry(
                journal_id=journal_id,
                account_code=account,
                debit=debit,
                credit=credit,
                reference_type=reference_type,
                reference_id=str(reference_id),
                memo=memo,
            )
        )
    return journal_id


def balance(db: Session, account_code: str) -> int:
    """Saldo menurut sisi normal akun."""
    account = db.get(LedgerAccount, account_code)
    debit, credit = db.execute(
        select(func.coalesce(func.sum(LedgerEntry.debit), 0), func.coalesce(func.sum(LedgerEntry.credit), 0)).where(
            LedgerEntry.account_code == account_code
        )
    ).one()
    return int(debit - credit) if account.normal_side == "DEBIT" else int(credit - debit)


def sell_tax(db: Session, gross: int) -> int:
    rule = db.get(TaxRule, "PPH22_BUYBACK")
    if not rule or not rule.is_active or gross <= rule.threshold:
        return 0
    return gross * rule.rate_bps // 10_000
