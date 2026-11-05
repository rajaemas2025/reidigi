from datetime import datetime, timedelta

from fastapi import APIRouter, Depends
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.catalog.models import Sku
from app.core.config import get_settings
from app.core.db import get_db
from app.core.deps import current_user
from app.core.errors import ApiError
from app.core.ids import normalize_token_code
from app.core.types import Rupiah, Schema
from app.identity.models import User
from app.inventory.models import Unit
from app.tokens.models import Token, TokenEvent

router = APIRouter(prefix="/v1/tokens", tags=["Token"])


class TokenOut(Schema):
    code: str
    sku_code: str
    weight_mg: int
    serial_number: str
    status: str
    acquired_price: Rupiah
    acquired_at: datetime
    sellable_at: datetime


class TokenEventOut(Schema):
    from_status: str | None
    to_status: str
    reason: str | None
    created_at: datetime


class TokenDetailOut(TokenOut):
    history: list[TokenEventOut]


def token_out(db: Session, t: Token) -> TokenOut:
    sku = db.get(Sku, t.sku_id)
    unit = db.get(Unit, t.unit_id)
    return TokenOut(
        code=t.token_code, sku_code=sku.code, weight_mg=sku.weight_mg, serial_number=unit.serial_number, status=t.status,
        acquired_price=t.acquired_price, acquired_at=t.created_at,
        sellable_at=t.created_at + timedelta(seconds=get_settings().holding_period_seconds),
    )


@router.get("", response_model=list[TokenOut])
def list_tokens(include_closed: bool = False, user: User = Depends(current_user), db: Session = Depends(get_db)):
    query = select(Token).where(Token.user_id == user.id).order_by(Token.created_at.desc())
    if not include_closed:
        query = query.where(Token.status.not_in(("RETURNED", "PRINTED", "CANCELLED")))
    return [token_out(db, t) for t in db.scalars(query)]


@router.get("/{code}", response_model=TokenDetailOut)
def get_token(code: str, user: User = Depends(current_user), db: Session = Depends(get_db)):
    # Kode token bukan kredensial: kepemilikan selalu diperiksa.
    token = db.scalar(select(Token).where(Token.token_code == normalize_token_code(code), Token.user_id == user.id))
    if token is None:
        raise ApiError("TOKEN_NOT_OWNED", "Token tidak ditemukan.")
    events = db.scalars(select(TokenEvent).where(TokenEvent.token_id == token.id).order_by(TokenEvent.id))
    return TokenDetailOut(**token_out(db, token).model_dump(), history=[TokenEventOut.model_validate(e) for e in events])
