"""Saldo pengguna. Setiap perubahan menulis wallet_entries; pemanggil menulis jurnal ledger yang sesuai."""

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.core.errors import ApiError
from app.wallet.models import Wallet, WalletEntry


def lock_wallet(db: Session, user_id: int) -> Wallet:
    """Urutan kunci: units → tokens → wallets. Panggil ini paling akhir."""
    wallet = db.scalar(select(Wallet).where(Wallet.user_id == user_id).with_for_update())
    if wallet is None:
        wallet = Wallet(user_id=user_id, available=0, held=0)
        db.add(wallet)
        db.flush()
    return wallet


def _entry(db, wallet: Wallet, type_: str, amount: int, ref_type: str, ref_id, withdrawable_at=None):
    db.add(
        WalletEntry(
            user_id=wallet.user_id,
            type=type_,
            amount=amount,
            reference_type=ref_type,
            reference_id=str(ref_id),
            withdrawable_at=withdrawable_at,
        )
    )


def credit(db: Session, user_id: int, amount: int, type_: str, ref_type: str, ref_id, withdrawable_at=None) -> Wallet:
    assert amount > 0
    wallet = lock_wallet(db, user_id)
    wallet.available += amount
    _entry(db, wallet, type_, amount, ref_type, ref_id, withdrawable_at)
    return wallet


def debit(db: Session, user_id: int, amount: int, type_: str, ref_type: str, ref_id) -> Wallet:
    assert amount > 0
    wallet = lock_wallet(db, user_id)
    if wallet.available < amount:
        raise ApiError("INSUFFICIENT_BALANCE", "Saldo tidak mencukupi.")
    wallet.available -= amount
    _entry(db, wallet, type_, -amount, ref_type, ref_id)
    return wallet


def hold(db: Session, user_id: int, amount: int, ref_type: str, ref_id) -> Wallet:
    wallet = lock_wallet(db, user_id)
    if wallet.available < amount:
        raise ApiError("INSUFFICIENT_BALANCE", "Saldo tidak mencukupi.")
    wallet.available -= amount
    wallet.held += amount
    _entry(db, wallet, "WITHDRAW_HOLD", -amount, ref_type, ref_id)
    return wallet


def release(db: Session, user_id: int, amount: int, ref_type: str, ref_id) -> Wallet:
    wallet = lock_wallet(db, user_id)
    wallet.held -= amount
    wallet.available += amount
    _entry(db, wallet, "WITHDRAW_RELEASE", amount, ref_type, ref_id)
    return wallet


def settle(db: Session, user_id: int, amount: int, ref_type: str, ref_id) -> Wallet:
    wallet = lock_wallet(db, user_id)
    wallet.held -= amount
    _entry(db, wallet, "WITHDRAW_SETTLE", -amount, ref_type, ref_id)
    return wallet
