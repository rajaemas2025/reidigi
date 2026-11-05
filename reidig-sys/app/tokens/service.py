"""Siklus hidup token. Setiap perubahan status menulis satu token_event berantai hash."""

import hashlib
import json

from sqlalchemy import select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.core import clock
from app.core.errors import ApiError
from app.core.ids import new_token_code
from app.tokens.models import FINAL_STATUSES, Token, TokenEvent

# Sama persis dengan diagram "Siklus Hidup Token" di spesifikasi bagian 4.
TRANSITIONS: dict[str, set[str]] = {
    "ACTIVE": {"RETURNED", "PRINT_PENDING"},
    "LOCKED_INSTALLMENT": {"ACTIVE", "CANCELLED"},
    "PRINT_PENDING": {"ACTIVE", "PRINTED"},
    "RETURNED": set(),
    "PRINTED": set(),
    "CANCELLED": set(),
}
INITIAL = {"ACTIVE", "LOCKED_INSTALLMENT"}


def event_hash(prev: bytes | None, token_id, from_status, to_status, actor_type, actor_id, reason, created_at) -> bytes:
    body = json.dumps(
        [str(token_id), from_status, to_status, actor_type, actor_id, reason, created_at.isoformat()],
        separators=(",", ":"),
    ).encode()
    return hashlib.sha256((prev or b"") + body).digest()


def _write_event(db: Session, token: Token, from_status, to_status, actor_type, actor_id, reason):
    actor_id = str(actor_id) if actor_id is not None else None
    prev = db.scalar(
        select(TokenEvent.hash).where(TokenEvent.token_id == token.id).order_by(TokenEvent.id.desc()).limit(1)
    )
    at = clock.now()
    db.add(
        TokenEvent(
            token_id=token.id,
            from_status=from_status,
            to_status=to_status,
            actor_type=actor_type,
            actor_id=actor_id,
            reason=reason,
            prev_hash=prev,
            hash=event_hash(prev, token.id, from_status, to_status, actor_type, actor_id, reason, at),
            created_at=at,
        )
    )
    db.flush()


def mint(
    db: Session,
    *,
    user_id: int,
    unit,
    sku,
    status: str,
    price: int,
    origin_type: str,
    origin_id: int,
    actor_type: str = "SYSTEM",
    actor_id: str | None = None,
) -> Token:
    assert status in INITIAL
    for _ in range(5):
        token = Token(
            token_code=new_token_code(sku.code),
            user_id=user_id,
            unit_id=unit.id,
            sku_id=sku.id,
            status=status,
            acquired_price=price,
            origin_type=origin_type,
            origin_id=origin_id,
            created_at=clock.now(),
        )
        try:
            with db.begin_nested():
                db.add(token)
                db.flush()
            break
        except IntegrityError as exc:
            # Tabrakan kode acak sangat jarang; tabrakan unit berarti invarian #1 terjaga oleh index.
            if "token_code" not in str(exc.orig):
                raise
    else:
        raise RuntimeError("Gagal membuat kode token unik")
    _write_event(db, token, None, status, actor_type, actor_id, f"Terbit dari {origin_type} {origin_id}")
    return token


def transition(db: Session, token: Token, to_status: str, actor_type: str, actor_id=None, reason: str | None = None):
    if token.status in FINAL_STATUSES or to_status not in TRANSITIONS[token.status]:
        raise ApiError("INVALID_STATE", f"Token {token.token_code} tidak bisa berubah dari {token.status} ke {to_status}.")
    from_status = token.status
    token.status = to_status
    if to_status in FINAL_STATUSES:
        token.closed_at = clock.now()
    _write_event(db, token, from_status, to_status, actor_type, actor_id, reason)


def lock_user_tokens(db: Session, user_id: int, codes: list[str]) -> list[Token]:
    """Mengunci token milik pengguna. Token milik orang lain dilaporkan sebagai tidak ditemukan."""
    tokens = db.scalars(
        select(Token).where(Token.token_code.in_(codes), Token.user_id == user_id).order_by(Token.id).with_for_update()
    ).all()
    if len(tokens) != len(set(codes)):
        raise ApiError("TOKEN_NOT_OWNED", "Token tidak ditemukan.")
    return list(tokens)
