from datetime import datetime

from fastapi import APIRouter, Depends
from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.catalog.models import Sku
from app.core.db import get_db
from app.core.models import SystemFlag
from app.core.types import Rupiah, Schema
from app.inventory.models import Location, Unit
from app.pricing.models import SkuPrice
from app.pricing.service import TRADING_HALTED

router = APIRouter(prefix="/v1", tags=["Katalog"])


class SkuOut(Schema):
    code: str
    brand: str
    product: str
    weight_mg: int
    buy_price: Rupiah | None
    buyback_price: Rupiah | None
    stock: int
    trading_halted: bool
    price_updated_at: datetime | None


class CatalogOut(Schema):
    trading_halted: bool
    skus: list[SkuOut]


class BranchOut(Schema):
    code: str
    name: str
    area: str | None
    pickup_estimate: str | None


def latest_prices(db: Session) -> dict[int, SkuPrice]:
    latest = select(SkuPrice.sku_id, func.max(SkuPrice.id).label("id")).group_by(SkuPrice.sku_id).subquery()
    return {p.sku_id: p for p in db.scalars(select(SkuPrice).join(latest, latest.c.id == SkuPrice.id))}


@router.get("/catalog/skus", response_model=CatalogOut)
def list_skus(db: Session = Depends(get_db)):
    prices = latest_prices(db)
    stock = dict(db.execute(select(Unit.sku_id, func.count()).where(Unit.status == "AVAILABLE").group_by(Unit.sku_id)).all())
    halted = db.get(SystemFlag, TRADING_HALTED)
    skus = []
    for sku in db.scalars(select(Sku).where(Sku.is_active.is_(True)).order_by(Sku.weight_mg, Sku.code)):
        price = prices.get(sku.id)
        skus.append(
            SkuOut(
                code=sku.code, brand=sku.brand, product=sku.product, weight_mg=sku.weight_mg,
                buy_price=price.buy_price if price else None, buyback_price=price.buyback_price if price else None,
                stock=stock.get(sku.id, 0), trading_halted=sku.trading_halted,
                price_updated_at=price.source_updated_at if price else None,
            )
        )
    return CatalogOut(trading_halted=bool(halted and halted.enabled), skus=skus)


@router.get("/branches", response_model=list[BranchOut])
def list_branches(db: Session = Depends(get_db)):
    return [BranchOut.model_validate(b) for b in db.scalars(select(Location).where(Location.type == "BRANCH").order_by(Location.id))]
