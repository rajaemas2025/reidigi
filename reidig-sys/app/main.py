from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from sqlalchemy.orm.exc import StaleDataError

import app.models  # noqa: F401  (mendaftarkan semua tabel)
from app.admin.router import router as admin_router
from app.catalog.router import router as catalog_router
from app.core.config import get_settings
from app.core.db import Base, SessionLocal, engine
from app.core.errors import install_error_handlers
from app.core.guards import install_db_guards
from app.financing.router import router as financing_router
from app.fulfillment.router import router as fulfillment_router
from app.identity.router import router as identity_router
from app.orders.router import router as orders_router
from app.payments.router import dev as dev_router
from app.payments.router import router as payments_router
from app.tokens.router import router as tokens_router
from app.wallet.router import router as wallet_router


def init_db() -> None:
    """Dev: buat tabel dan isi data contoh. Produksi: `alembic upgrade head` lalu `seed_reference`."""
    from app.seed import seed_demo, seed_reference

    settings = get_settings()
    if settings.auto_create_schema:
        Base.metadata.create_all(engine)
        with engine.begin() as conn:
            install_db_guards(conn)
    with SessionLocal() as db:
        if settings.seed_demo:
            seed_demo(db)
        else:
            seed_reference(db)
        db.commit()


@asynccontextmanager
async def lifespan(_: FastAPI):
    init_db()
    yield


def create_app(run_init: bool = True) -> FastAPI:
    app = FastAPI(
        title="REIDIG API",
        version="0.1.0",
        description="Backend emas digital REIDIG. Rupiah dikirim sebagai string bilangan bulat; berat dalam miligram; waktu UTC.",
        lifespan=lifespan if run_init else None,
    )
    install_error_handlers(app)

    @app.exception_handler(StaleDataError)
    async def _stale(_, __):
        return JSONResponse(status_code=409, content={"error": {"code": "CONFLICT", "message": "Data berubah bersamaan. Coba lagi."}})

    app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_methods=["*"], allow_headers=["*"])

    for router in (
        identity_router, catalog_router, orders_router, tokens_router, wallet_router,
        fulfillment_router, financing_router, payments_router, dev_router, admin_router,
    ):
        app.include_router(router)

    @app.get("/health", tags=["Sistem"])
    def health():
        return {"status": "ok"}

    return app


app = create_app()
