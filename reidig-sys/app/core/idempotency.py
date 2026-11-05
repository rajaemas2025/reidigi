"""Header Idempotency-Key: permintaan yang sama dikirim ulang mendapat jawaban yang sama, tanpa diproses dua kali."""

import json
from collections.abc import Callable
from typing import TypeVar

from pydantic import BaseModel
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.core.errors import ApiError
from app.core.models import IdempotencyKey
from app.core.security import sha256_hex

M = TypeVar("M", bound=BaseModel)


def run_idempotent(
    db: Session,
    scope: str,
    key: str | None,
    payload: dict,
    model: type[M],
    action: Callable[[], M],
) -> M:
    """`action` tidak boleh commit; fungsi ini yang commit bersama catatan kuncinya."""
    if not key:
        result = action()
        db.commit()
        return result

    request_hash = sha256_hex(json.dumps(payload, sort_keys=True, default=str))
    existing = db.scalar(select(IdempotencyKey).where(IdempotencyKey.scope == scope, IdempotencyKey.key == key))
    if existing:
        if existing.request_hash != request_hash:
            raise ApiError("IDEMPOTENCY_KEY_REUSED", "Idempotency-Key ini sudah dipakai untuk permintaan lain.")
        return model.model_validate(existing.response)

    result = action()
    db.add(IdempotencyKey(scope=scope, key=key, request_hash=request_hash, response=result.model_dump(mode="json")))
    db.commit()
    return result
