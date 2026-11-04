from datetime import date

from fastapi import APIRouter, Depends, Request
from pydantic import Field
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.core.db import get_db
from app.core.deps import bearer, client_info, current_user
from app.core.types import Schema
from app.identity import service
from app.identity.models import BankAccount, User
from app.wallet.models import Wallet

router = APIRouter(prefix="/v1", tags=["Akun"])


class RegisterIn(Schema):
    name: str
    email: str
    password: str
    phone: str | None = Field(default=None, description="Nomor setelah +62, tanpa 0 di depan")


class LoginIn(Schema):
    email: str
    password: str


class SessionOut(Schema):
    access_token: str
    token_type: str = "bearer"


class MeOut(Schema):
    id: int
    name: str
    email: str
    phone: str | None
    kyc_status: str
    verified: bool
    has_pin: bool
    pin_locked: bool


class PinIn(Schema):
    pin: str
    old_pin: str | None = None


class KycIn(Schema):
    full_name: str
    nik: str
    date_of_birth: date


class KycOut(Schema):
    id: int
    status: str
    full_name: str
    nik_last4: str


class BankAccountIn(Schema):
    bank_code: str
    account_number: str
    holder_name: str


class BankAccountOut(Schema):
    id: int
    bank_code: str
    account_number_masked: str
    holder_name: str
    verified: bool


def me_out(user: User) -> MeOut:
    return MeOut(
        id=user.id, name=user.name, email=user.email, phone=user.phone, kyc_status=user.kyc_status,
        verified=user.kyc_status == "APPROVED", has_pin=bool(user.pin_hash), pin_locked=user.pin_locked,
    )


def bank_out(a: BankAccount) -> BankAccountOut:
    return BankAccountOut(
        id=a.id, bank_code=a.bank_code, account_number_masked="•••• " + a.account_number[-4:], holder_name=a.holder_name, verified=a.verified
    )


@router.post("/auth/register", response_model=SessionOut, status_code=201)
def register(body: RegisterIn, request: Request, db: Session = Depends(get_db)):
    user = service.register(db, body.name, body.email, body.password, body.phone)
    db.add(Wallet(user_id=user.id, available=0, held=0))
    info = client_info(request)
    _, token = service.login(db, body.email, body.password, info["device_id"], info["user_agent"])
    db.commit()
    return SessionOut(access_token=token)


@router.post("/auth/login", response_model=SessionOut)
def login(body: LoginIn, request: Request, db: Session = Depends(get_db)):
    info = client_info(request)
    _, token = service.login(db, body.email, body.password, info["device_id"], info["user_agent"])
    db.commit()
    return SessionOut(access_token=token)


@router.post("/auth/logout", status_code=204)
def logout(token: str = Depends(bearer), db: Session = Depends(get_db)):
    service.logout(db, token)
    db.commit()


@router.get("/me", response_model=MeOut)
def me(user: User = Depends(current_user)):
    return me_out(user)


@router.put("/me/pin", status_code=204)
def set_pin(body: PinIn, user: User = Depends(current_user), db: Session = Depends(get_db)):
    service.set_pin(db, user, body.pin, body.old_pin)
    db.commit()


@router.post("/kyc", response_model=KycOut, status_code=201)
def submit_kyc(body: KycIn, user: User = Depends(current_user), db: Session = Depends(get_db)):
    record = service.submit_kyc(db, user, body.full_name, body.nik, body.date_of_birth)
    db.commit()
    return KycOut.model_validate(record)


@router.get("/me/bank-accounts", response_model=list[BankAccountOut])
def list_bank_accounts(user: User = Depends(current_user), db: Session = Depends(get_db)):
    return [bank_out(a) for a in db.scalars(select(BankAccount).where(BankAccount.user_id == user.id).order_by(BankAccount.id))]


@router.post("/me/bank-accounts", response_model=BankAccountOut, status_code=201)
def add_bank_account(body: BankAccountIn, user: User = Depends(current_user), db: Session = Depends(get_db)):
    account = service.add_bank_account(db, user, body.bank_code, body.account_number, body.holder_name)
    db.commit()
    return bank_out(account)
