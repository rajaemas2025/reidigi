import json

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.core import clock
from app.core.errors import ApiError
from app.payments import gateway
from app.payments.models import Payment


def create_payment(db: Session, *, user_id: int, purpose: str, reference_id: int, method: str, amount: int, expires_at) -> Payment:
    provider_ref, code = gateway.create_charge(method, amount, f"{purpose}-{reference_id}")
    payment = Payment(
        user_id=user_id,
        purpose=purpose,
        reference_id=reference_id,
        method=method,
        amount=amount,
        status="PENDING",
        provider_ref=provider_ref,
        payment_code=code,
        expires_at=expires_at,
    )
    db.add(payment)
    db.flush()
    return payment


def close_pending(db: Session, purpose: str, reference_id: int, status: str) -> None:
    for payment in db.scalars(
        select(Payment).where(Payment.purpose == purpose, Payment.reference_id == reference_id, Payment.status == "PENDING")
    ):
        payment.status = status


def handle_callback(db: Session, body: bytes, signature: str | None) -> Payment:
    """Callback iFortepay: tanda tangan dan nominal diperiksa sebelum apa pun berubah."""
    from app.financing import service as financing
    from app.orders import service as orders

    if not gateway.verify(body, signature):
        raise ApiError("INVALID_SIGNATURE", "Tanda tangan tidak valid.")
    try:
        data = json.loads(body)
        provider_ref = str(data["provider_ref"])
        status = str(data["status"])
        amount = int(str(data["amount"]))
    except (ValueError, KeyError, TypeError):
        raise ApiError("INVALID", "Isi callback tidak valid.")

    payment = db.scalar(select(Payment).where(Payment.provider_ref == provider_ref).with_for_update())
    if payment is None:
        raise ApiError("NOT_FOUND", "Pembayaran tidak ditemukan.")
    payment.last_callback = data
    if status != "PAID":
        return payment
    if payment.status in ("PAID", "REFUNDED"):
        return payment  # callback ganda
    if amount != payment.amount:
        raise ApiError("AMOUNT_MISMATCH", "Nominal pembayaran tidak sesuai tagihan.")

    payment.paid_at = clock.now()
    if payment.purpose == "ORDER":
        orders.on_payment_paid(db, payment)
    else:
        financing.on_down_payment_paid(db, payment)
    return payment
