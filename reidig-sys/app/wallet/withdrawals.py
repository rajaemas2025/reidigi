"""
Penarikan dana. Spesifikasi 6.2a terpotong; alur ini mengikuti dashboard:
dana ditahan dulu, baru dikeluarkan setelah bank mengonfirmasi berhasil.
Status UNKNOWN tidak boleh dikirim ulang otomatis karena uang bisa jadi sudah keluar.
"""

import json

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.admin.audit import audit
from app.core import clock
from app.core.config import get_settings
from app.core.errors import ApiError
from app.core.models import SystemFlag, emit
from app.identity.models import BankAccount
from app.identity.service import require_verified, verify_pin
from app.ledger import service as ledger
from app.payments import gateway
from app.payments.models import Disbursement
from app.pricing.service import WITHDRAWALS_FROZEN
from app.wallet import service as wallet
from app.wallet.models import Withdrawal


def _ensure_not_frozen(db: Session) -> None:
    frozen = db.get(SystemFlag, WITHDRAWALS_FROZEN)
    if frozen and frozen.enabled:
        raise ApiError("WITHDRAWALS_FROZEN", "Penarikan dana sedang dihentikan sementara untuk pemeriksaan.")


def _hold_and_queue(db: Session, w: Withdrawal) -> Disbursement:
    wallet.hold(db, w.user_id, w.amount, "WITHDRAWAL", f"{w.id}:{w.attempts}")
    disbursement = Disbursement(withdrawal_id=w.id, attempt=w.attempts, amount=w.amount, status="PENDING")
    db.add(disbursement)
    db.flush()
    emit(db, "disbursement.requested", disbursement_id=disbursement.id)
    return disbursement


def request(db: Session, user, amount: int, bank_account_id: int, pin: str | None) -> Withdrawal:
    verify_pin(db, user, pin)
    require_verified(user)
    _ensure_not_frozen(db)
    if amount < get_settings().min_withdrawal:
        raise ApiError("INVALID", f"Minimal penarikan Rp {get_settings().min_withdrawal:,}".replace(",", "."))
    account = db.get(BankAccount, bank_account_id)
    if account is None or account.user_id != user.id or not account.verified:
        raise ApiError("INVALID", "Pilih rekening tujuan.")
    w = Withdrawal(user_id=user.id, bank_account_id=account.id, amount=amount, status="PROCESSING", attempts=1)
    db.add(w)
    db.flush()
    _hold_and_queue(db, w)
    return w


def send_pending(db: Session) -> int:
    """Worker: kirim pencairan yang belum dikirim ke iFortepay."""
    sent = 0
    for d in db.scalars(select(Disbursement).where(Disbursement.status == "PENDING").with_for_update(skip_locked=True)):
        w = db.get(Withdrawal, d.withdrawal_id)
        account = db.get(BankAccount, w.bank_account_id)
        d.provider_ref = gateway.create_disbursement(d.amount, account.bank_code, account.account_number, f"WD-{w.id}-{d.attempt}")
        d.status = "SENT"
        d.updated_at = clock.now()
        sent += 1
    return sent


def _settle(db: Session, w: Withdrawal, d: Disbursement) -> None:
    d.status = "SUCCESS"
    w.status = "SUCCESS"
    wallet.settle(db, w.user_id, w.amount, "WITHDRAWAL", f"{w.id}:{d.attempt}")
    ledger.post(db, [(ledger.WALLET_LIABILITY, w.amount, 0), (ledger.CUSTOMER_FUNDS, 0, w.amount)], "WITHDRAWAL", w.id, "Tarik dana ke rekening")


def _fail(db: Session, w: Withdrawal, d: Disbursement, reason: str) -> None:
    d.status = "FAILED"
    d.failure_reason = reason
    w.status = "FAILED"
    wallet.release(db, w.user_id, w.amount, "WITHDRAWAL", f"{w.id}:{d.attempt}")


def handle_callback(db: Session, body: bytes, signature: str | None) -> Disbursement:
    if not gateway.verify(body, signature):
        raise ApiError("INVALID_SIGNATURE", "Tanda tangan tidak valid.")
    try:
        data = json.loads(body)
        provider_ref, status = str(data["provider_ref"]), str(data["status"])
    except (ValueError, KeyError, TypeError):
        raise ApiError("INVALID", "Isi callback tidak valid.")
    d = db.scalar(select(Disbursement).where(Disbursement.provider_ref == provider_ref).with_for_update())
    if d is None:
        raise ApiError("NOT_FOUND", "Pencairan tidak ditemukan.")
    if d.status in ("SUCCESS", "FAILED"):
        return d  # callback ganda
    w = db.get(Withdrawal, d.withdrawal_id, with_for_update=True)
    if status == "SUCCESS":
        _settle(db, w, d)
    elif status == "FAILED":
        _fail(db, w, d, str(data.get("reason", "Ditolak bank")))
    else:
        d.status = w.status = "UNKNOWN"
    d.updated_at = w.updated_at = clock.now()
    emit(db, "withdrawal.updated", withdrawal_id=w.id, status=w.status)
    return d


def retry(db: Session, actor, withdrawal_id: int) -> Withdrawal:
    w = db.get(Withdrawal, withdrawal_id, with_for_update=True)
    if w is None:
        raise ApiError("NOT_FOUND", "Penarikan tidak ditemukan.")
    if w.status != "FAILED":
        raise ApiError("INVALID_STATE", "Hanya penarikan yang gagal final yang bisa dicoba ulang.")
    _ensure_not_frozen(db)
    w.attempts += 1
    w.status = "PROCESSING"
    w.updated_at = clock.now()
    _hold_and_queue(db, w)
    audit(db, actor, "WITHDRAWAL_RETRIED", f"Penarikan #{w.id}", f"Percobaan ke-{w.attempts}")
    return w


def resolve_unknown(db: Session, actor, withdrawal_id: int, succeeded: bool, note: str) -> Withdrawal:
    """Finance memastikan status ke bank secara manual, lalu mencatat hasilnya."""
    if not note.strip():
        raise ApiError("INVALID", "Catatan pemeriksaan wajib diisi.")
    w = db.get(Withdrawal, withdrawal_id, with_for_update=True)
    if w is None or w.status != "UNKNOWN":
        raise ApiError("INVALID_STATE", "Penarikan ini tidak berstatus belum pasti.")
    d = db.scalar(select(Disbursement).where(Disbursement.withdrawal_id == w.id).order_by(Disbursement.attempt.desc()).limit(1))
    _settle(db, w, d) if succeeded else _fail(db, w, d, note.strip())
    w.updated_at = clock.now()
    audit(db, actor, "WITHDRAWAL_RESOLVED", f"Penarikan #{w.id}", f"{'Berhasil' if succeeded else 'Gagal'}: {note.strip()}")
    return w


def topup(db: Session, user, amount: int, source: str):
    from app.wallet.models import Topup

    if not get_settings().topup_enabled:
        raise ApiError("FEATURE_DISABLED", "Isi saldo belum tersedia.")
    require_verified(user)
    t = Topup(user_id=user.id, amount=amount, status="SUCCESS", source=source)
    db.add(t)
    db.flush()
    wallet.credit(db, user.id, amount, "TOPUP", "TOPUP", t.id)
    ledger.post(db, [(ledger.CUSTOMER_FUNDS, amount, 0), (ledger.WALLET_LIABILITY, 0, amount)], "TOPUP", t.id, "Isi saldo")
    return t
