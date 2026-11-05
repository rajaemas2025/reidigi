from datetime import datetime

from fastapi import APIRouter, Depends, Header
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.core.db import get_db
from app.core.deps import current_user
from app.core.idempotency import run_idempotent
from app.core.types import Rupiah, Schema
from app.identity.models import BankAccount, User
from app.wallet import withdrawals
from app.wallet.models import Wallet, WalletEntry, Withdrawal

router = APIRouter(prefix="/v1/wallet", tags=["Saldo"])


class WalletOut(Schema):
    available: Rupiah
    held: Rupiah


class EntryOut(Schema):
    id: int
    type: str
    amount: Rupiah
    reference_type: str
    reference_id: str
    created_at: datetime


class WithdrawIn(Schema):
    amount: Rupiah
    bank_account_id: int
    pin: str


class WithdrawalOut(Schema):
    id: int
    amount: Rupiah
    status: str
    bank_code: str
    account_number_masked: str
    created_at: datetime


class TopupIn(Schema):
    amount: Rupiah


def withdrawal_out(db: Session, w: Withdrawal) -> WithdrawalOut:
    account = db.get(BankAccount, w.bank_account_id)
    return WithdrawalOut(
        id=w.id, amount=w.amount, status=w.status, bank_code=account.bank_code,
        account_number_masked="•••• " + account.account_number[-4:], created_at=w.created_at,
    )


@router.get("", response_model=WalletOut)
def get_wallet(user: User = Depends(current_user), db: Session = Depends(get_db)):
    wallet = db.get(Wallet, user.id)
    return WalletOut(available=wallet.available if wallet else 0, held=wallet.held if wallet else 0)


@router.get("/entries", response_model=list[EntryOut])
def entries(user: User = Depends(current_user), db: Session = Depends(get_db)):
    rows = db.scalars(select(WalletEntry).where(WalletEntry.user_id == user.id).order_by(WalletEntry.id.desc()).limit(200))
    return [EntryOut.model_validate(e) for e in rows]


@router.post("/withdrawals", response_model=WithdrawalOut, status_code=201)
def withdraw(
    body: WithdrawIn,
    user: User = Depends(current_user),
    db: Session = Depends(get_db),
    idempotency_key: str | None = Header(default=None),
):
    def action():
        return withdrawal_out(db, withdrawals.request(db, user, body.amount, body.bank_account_id, body.pin))

    result = run_idempotent(db, f"user:{user.id}:withdraw", idempotency_key, body.model_dump(exclude={"pin"}), WithdrawalOut, action)
    # Pencairan dikirim setelah transaksi tersimpan; bila gagal, worker mengirim ulang.
    try:
        withdrawals.send_pending(db)
        db.commit()
    except Exception:
        db.rollback()
    return result


@router.get("/withdrawals", response_model=list[WithdrawalOut])
def list_withdrawals(user: User = Depends(current_user), db: Session = Depends(get_db)):
    rows = db.scalars(select(Withdrawal).where(Withdrawal.user_id == user.id).order_by(Withdrawal.id.desc()))
    return [withdrawal_out(db, w) for w in rows]


@router.post("/topups", response_model=WalletOut, status_code=201)
def topup(body: TopupIn, user: User = Depends(current_user), db: Session = Depends(get_db)):
    """Nonaktif sampai cakupan izin saldo terkonfirmasi (REIDIG_TOPUP_ENABLED)."""
    withdrawals.topup(db, user, body.amount, "MANUAL")
    db.commit()
    wallet = db.get(Wallet, user.id)
    return WalletOut(available=wallet.available, held=wallet.held)
