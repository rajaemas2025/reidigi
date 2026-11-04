import json

from fastapi import APIRouter, Depends, Header, Request
from sqlalchemy.orm import Session

from app.core.config import get_settings
from app.core.db import get_db
from app.core.deps import current_user
from app.core.errors import ApiError
from app.core.security import hmac_sha256
from app.core.types import Schema
from app.financing import service as financing
from app.identity.models import User
from app.payments import gateway
from app.payments import service as payments
from app.payments.models import Disbursement, Payment
from app.wallet import withdrawals

router = APIRouter(prefix="/v1", tags=["Webhook"])


async def raw_body(request: Request) -> bytes:
    return await request.body()


class Ack(Schema):
    ok: bool = True
    status: str


@router.post("/webhooks/ifortepay/payments", response_model=Ack)
def payment_callback(body: bytes = Depends(raw_body), db: Session = Depends(get_db), x_signature: str | None = Header(default=None)):
    payment = payments.handle_callback(db, body, x_signature)
    db.commit()
    return Ack(status=payment.status)


@router.post("/webhooks/ifortepay/disbursements", response_model=Ack)
def disbursement_callback(body: bytes = Depends(raw_body), db: Session = Depends(get_db), x_signature: str | None = Header(default=None)):
    d = withdrawals.handle_callback(db, body, x_signature)
    db.commit()
    return Ack(status=d.status)


@router.post("/webhooks/financing-partner", response_model=Ack)
def partner_webhook(body: bytes = Depends(raw_body), db: Session = Depends(get_db), x_signature: str | None = Header(default=None)):
    event = financing.handle_partner_webhook(db, body, x_signature)
    db.commit()
    return Ack(status=event.type)


# ----- Hanya dev: meniru iFortepay agar tombol "Saya sudah bayar" di aplikasi bisa dicoba -----

dev = APIRouter(prefix="/v1/dev", tags=["Dev"])


def _require_dev():
    if not get_settings().is_dev:
        raise ApiError("NOT_FOUND", "Tidak ditemukan.")


@dev.post("/payments/{payment_id}/simulate-paid", response_model=Ack, dependencies=[Depends(_require_dev)])
def simulate_paid(payment_id: int, user: User = Depends(current_user), db: Session = Depends(get_db)):
    payment = db.get(Payment, payment_id)
    if payment is None or payment.user_id != user.id:
        raise ApiError("NOT_FOUND", "Pembayaran tidak ditemukan.")
    body = json.dumps({"provider_ref": payment.provider_ref, "status": "PAID", "amount": str(payment.amount)}).encode()
    result = payments.handle_callback(db, body, gateway.sign(body))
    db.commit()
    return Ack(status=result.status)


class DisbursementResult(Schema):
    status: str = "SUCCESS"


@dev.post("/disbursements/{disbursement_id}/simulate", response_model=Ack, dependencies=[Depends(_require_dev)])
def simulate_disbursement(disbursement_id: int, body: DisbursementResult, db: Session = Depends(get_db)):
    d = db.get(Disbursement, disbursement_id)
    if d is None or not d.provider_ref:
        raise ApiError("NOT_FOUND", "Pencairan belum dikirim.")
    raw = json.dumps({"provider_ref": d.provider_ref, "status": body.status}).encode()
    result = withdrawals.handle_callback(db, raw, gateway.sign(raw))
    db.commit()
    return Ack(status=result.status)


class PartnerEvent(Schema):
    event_id: str
    contract_ref: str
    type: str
    paid_months: int | None = None


@dev.post("/financing-partner/simulate", response_model=Ack, dependencies=[Depends(_require_dev)])
def simulate_partner(body: PartnerEvent, db: Session = Depends(get_db)):
    raw = json.dumps(body.model_dump(exclude_none=True)).encode()
    event = financing.handle_partner_webhook(db, raw, hmac_sha256(get_settings().financing_partner_secret, raw))
    db.commit()
    return Ack(status=event.type)
