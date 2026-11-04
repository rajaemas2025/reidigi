from datetime import datetime
from typing import Literal

from fastapi import APIRouter, Depends, Header
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.catalog.models import Sku
from app.core.db import get_db
from app.core.deps import current_user
from app.core.errors import ApiError
from app.core.idempotency import run_idempotent
from app.core.types import Schema
from app.fulfillment import service
from app.fulfillment.models import PrintOrder, Shipment
from app.identity.models import User
from app.inventory.models import Location, Unit
from app.tokens.models import Token

router = APIRouter(prefix="/v1/print-orders", tags=["Cetak fisik"])


class PrintIn(Schema):
    token_codes: list[str]
    method: Literal["SHIP", "PICKUP"]
    address: str | None = None
    branch_code: str | None = None
    pin: str


class PrintItemOut(Schema):
    token_code: str
    serial_number: str
    weight_mg: int
    picked: bool
    arrived: bool


class PrintOut(Schema):
    id: int
    method: str
    destination: str
    status: str
    tracking_number: str | None
    pickup_code: str | None
    pickup_deadline: datetime | None
    estimate: str
    created_at: datetime
    items: list[PrintItemOut]


def print_out(db: Session, order: PrintOrder, show_code: bool = True) -> PrintOut:
    branch = db.get(Location, order.branch_location_id) if order.branch_location_id else None
    shipment = db.scalar(select(Shipment).where(Shipment.print_order_id == order.id).order_by(Shipment.id.desc()).limit(1))
    item_out = []
    for i in service.items(db, order.id):
        unit = db.get(Unit, i.unit_id)
        item_out.append(
            PrintItemOut(
                token_code=db.get(Token, i.token_id).token_code, serial_number=unit.serial_number,
                weight_mg=db.get(Sku, unit.sku_id).weight_mg, picked=i.picked, arrived=i.arrived,
            )
        )
    return PrintOut(
        id=order.id, method=order.method,
        destination=f"Ambil di cabang {branch.name}" if branch else f"JNE ke {order.address}",
        status=order.status, tracking_number=shipment.tracking_number if shipment else None,
        pickup_code=order.pickup_code if show_code else None, pickup_deadline=order.pickup_deadline,
        estimate=(branch.pickup_estimate if branch else "3–5 hari kerja") or "", created_at=order.created_at, items=item_out,
    )


@router.post("", response_model=PrintOut, status_code=201)
def create(
    body: PrintIn,
    user: User = Depends(current_user),
    db: Session = Depends(get_db),
    idempotency_key: str | None = Header(default=None),
):
    def action():
        order = service.create(db, user, body.token_codes, body.method, body.address, body.branch_code, body.pin)
        return print_out(db, order)

    return run_idempotent(db, f"user:{user.id}:print", idempotency_key, body.model_dump(exclude={"pin"}), PrintOut, action)


@router.get("", response_model=list[PrintOut])
def list_orders(user: User = Depends(current_user), db: Session = Depends(get_db)):
    return [print_out(db, o) for o in db.scalars(select(PrintOrder).where(PrintOrder.user_id == user.id).order_by(PrintOrder.id.desc()))]


@router.get("/{order_id}", response_model=PrintOut)
def get_order(order_id: int, user: User = Depends(current_user), db: Session = Depends(get_db)):
    order = db.get(PrintOrder, order_id)
    if order is None or order.user_id != user.id:
        raise ApiError("NOT_FOUND", "Pesanan cetak tidak ditemukan.")
    return print_out(db, order)


@router.post("/{order_id}/cancel", response_model=PrintOut)
def cancel(order_id: int, user: User = Depends(current_user), db: Session = Depends(get_db)):
    order = service.cancel_by_user(db, user, order_id)
    db.commit()
    return print_out(db, order)
