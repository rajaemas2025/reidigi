from datetime import datetime
from typing import Literal

from fastapi import APIRouter, Depends, Header, Request
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.catalog.models import Sku
from app.core.db import get_db
from app.core.deps import client_info, current_user
from app.core.idempotency import run_idempotent
from app.core.types import Rupiah, Schema
from app.financing import service
from app.financing.models import FinancingApplication, FinancingContract
from app.identity.models import User
from app.orders.router import PaymentOut
from app.payments.models import Payment
from app.tokens.models import Token

router = APIRouter(prefix="/v1/financing", tags=["Cicilan"])


class AgreementOut(Schema):
    version: str
    content_hash: str
    body: str


class SimulationIn(Schema):
    sku_code: str
    down_payment: Rupiah
    tenor_months: int


class SimulationOut(Schema):
    sku_code: str
    price: Rupiah
    down_payment: Rupiah
    tenor_months: int
    monthly_before_fees: Rupiah
    partner: str


class ApplicationIn(SimulationIn):
    expected_price: Rupiah
    agreement_version: str
    agreement_hash: str
    agreed_terms: bool
    agreed_data_sharing: bool
    pin: str


class ApplicationOut(Schema):
    id: int
    sku_code: str
    price: Rupiah
    down_payment: Rupiah
    tenor_months: int
    monthly_before_fees: Rupiah
    status: str
    pay_before: datetime


class DownPaymentIn(Schema):
    method: Literal["WALLET", "QRIS", "VIRTUAL_ACCOUNT"]
    pin: str | None = None


class ContractOut(Schema):
    id: int
    contract_ref: str
    partner: str
    token_code: str
    price: Rupiah
    down_payment: Rupiah
    tenor_months: int
    paid_months: int
    status: str
    agreement_version: str
    agreed_at: datetime


class DownPaymentOut(Schema):
    application: ApplicationOut
    contract: ContractOut | None
    payment: PaymentOut | None


def application_out(db: Session, a: FinancingApplication) -> ApplicationOut:
    return ApplicationOut(
        id=a.id, sku_code=db.get(Sku, a.sku_id).code, price=a.price, down_payment=a.down_payment, tenor_months=a.tenor_months,
        monthly_before_fees=a.monthly_before_fees, status=a.status, pay_before=a.pay_before,
    )


def contract_out(db: Session, c: FinancingContract) -> ContractOut:
    return ContractOut(
        id=c.id, contract_ref=c.contract_ref, partner=c.partner, token_code=db.get(Token, c.token_id).token_code, price=c.price,
        down_payment=c.down_payment, tenor_months=c.tenor_months, paid_months=c.paid_months, status=c.status,
        agreement_version=c.agreement_version, agreed_at=c.agreed_at,
    )


@router.get("/agreement", response_model=AgreementOut)
def agreement(db: Session = Depends(get_db)):
    return AgreementOut.model_validate(service.current_agreement(db))


@router.post("/simulations", response_model=SimulationOut)
def simulate(body: SimulationIn, db: Session = Depends(get_db), _: User = Depends(current_user)):
    return SimulationOut(**service.simulate(db, body.sku_code, body.down_payment, body.tenor_months))


@router.post("/applications", response_model=ApplicationOut, status_code=201)
def apply(
    body: ApplicationIn,
    request: Request,
    user: User = Depends(current_user),
    db: Session = Depends(get_db),
    idempotency_key: str | None = Header(default=None),
):
    info = client_info(request)

    def action():
        a = service.apply(
            db, user, sku_code=body.sku_code, down_payment=body.down_payment, tenor_months=body.tenor_months,
            expected_price=body.expected_price, agreement_version=body.agreement_version, agreement_hash=body.agreement_hash,
            agreed_terms=body.agreed_terms, agreed_data_sharing=body.agreed_data_sharing, pin=body.pin, **info,
        )
        return application_out(db, a)

    return run_idempotent(db, f"user:{user.id}:financing", idempotency_key, body.model_dump(exclude={"pin"}), ApplicationOut, action)


@router.post("/applications/{application_id}/down-payment", response_model=DownPaymentOut)
def down_payment(
    application_id: int,
    body: DownPaymentIn,
    user: User = Depends(current_user),
    db: Session = Depends(get_db),
    idempotency_key: str | None = Header(default=None),
):
    def action():
        contract, payment = service.pay_down_payment(db, user, application_id, body.method, body.pin)
        return DownPaymentOut(
            application=application_out(db, db.get(FinancingApplication, application_id)),
            contract=contract_out(db, contract) if contract else None,
            payment=PaymentOut.model_validate(payment) if payment else None,
        )

    payload = {"application_id": application_id, "method": body.method}
    return run_idempotent(db, f"user:{user.id}:dp", idempotency_key, payload, DownPaymentOut, action)


@router.post("/applications/{application_id}/cancel", response_model=ApplicationOut)
def cancel(application_id: int, user: User = Depends(current_user), db: Session = Depends(get_db)):
    a = service.cancel(db, user, application_id)
    db.commit()
    return application_out(db, a)


@router.get("/applications", response_model=list[ApplicationOut])
def list_applications(user: User = Depends(current_user), db: Session = Depends(get_db)):
    rows = db.scalars(select(FinancingApplication).where(FinancingApplication.user_id == user.id).order_by(FinancingApplication.id.desc()))
    return [application_out(db, a) for a in rows]


@router.get("/contracts", response_model=list[ContractOut])
def list_contracts(user: User = Depends(current_user), db: Session = Depends(get_db)):
    rows = db.scalars(select(FinancingContract).where(FinancingContract.user_id == user.id).order_by(FinancingContract.id.desc()))
    return [contract_out(db, c) for c in rows]


@router.get("/applications/{application_id}/payment", response_model=PaymentOut | None)
def latest_payment(application_id: int, user: User = Depends(current_user), db: Session = Depends(get_db)):
    return db.scalar(
        select(Payment).where(Payment.purpose == "FINANCING_DP", Payment.reference_id == application_id, Payment.user_id == user.id)
        .order_by(Payment.id.desc()).limit(1)
    )
