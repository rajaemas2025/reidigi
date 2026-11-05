"""
Data contoh untuk dev, sama dengan data tiruan di aplikasi Android dan dashboard.
Semua lewat service agar riwayat event, ledger, dan invarian sama seperti data sungguhan.

  python -m app.seed
"""

from datetime import UTC, date, datetime, timedelta

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.admin.models import ROLES, AdminUser, Role
from app.catalog.models import Sku
from app.core import clock
from app.core.security import hash_secret, sha256_hex
from app.financing.models import FinancingAgreementVersion
from app.identity import service as identity
from app.identity.models import User
from app.inventory import service as inventory
from app.inventory.models import Location, Unit
from app.ledger import service as ledger
from app.ledger.models import LedgerAccount, TaxRule
from app.orders import service as orders
from app.pricing import service as pricing
from app.wallet import service as wallet
from app.wallet.models import Topup, Wallet

DEMO_EMAIL = "demo@reidig.id"
DEMO_PASSWORD = "demo1234"
DEMO_PIN = "147258"
ADMIN_PASSWORD = "reidig-dev-123"
BUYBACK_PER_GRAM = 2_310_000

# Harga dari halaman publik emasmurniasli.co.id, 3 November 2026.
CATALOG = [
    ("LMRE-0.1G", "LM Raja Emas", 100, 305_000, 40),
    ("LMRE-0.5G", "LM Raja Emas", 500, 1_308_500, 30),
    ("LMRE-1G", "LM Raja Emas", 1_000, 2_513_500, 40),
    ("LMRE-2G", "LM Raja Emas", 2_000, 5_007_000, 20),
    ("LMRE-5G", "LM Raja Emas", 5_000, 12_442_500, 16),
    ("LMRE-10G", "LM Raja Emas", 10_000, 25_135_000, 12),
    ("LMRE-25G", "LM Raja Emas", 25_000, 61_737_500, 8),
    ("LMRE-50G", "LM Raja Emas", 50_000, 123_375_000, 6),
    ("LMRE-100G", "LM Raja Emas", 100_000, 246_550_000, 4),
    ("LMRE-250G", "LM Raja Emas", 250_000, 614_265_000, 2),
    ("LMRE-500G", "LM Raja Emas", 500_000, 1_228_320_000, 2),
    ("LMRE-1000G", "LM Raja Emas", 1_000_000, 2_456_600_000, 1),
    ("KITKAT-20G", "Kitkat Gold", 20_000, 50_070_000, 3),
    ("LMWD-1G", "LM Wedding", 1_000, 2_528_500, 0),
]

LOCATIONS = [
    ("jkt", "Gudang Jakarta", "WAREHOUSE", "Jakarta", None),
    ("cgk", "Cengkareng", "BRANCH", "Jakarta Barat", "2–3 hari kerja"),
    ("klg", "Kelapa Gading", "BRANCH", "Jakarta Utara", "2–3 hari kerja"),
    ("ptj", "Petojo", "BRANCH", "Jakarta Pusat", "1–2 hari kerja"),
    ("amb", "Mal Ambasador Kuningan", "BRANCH", "Jakarta Selatan", "1–2 hari kerja"),
]

ADMINS = [
    ("gudang-a", "Petugas Gudang A", "WAREHOUSE_STAFF"),
    ("gudang-b", "Petugas Gudang B", "WAREHOUSE_STAFF"),
    ("spv", "Supervisor Gudang", "WAREHOUSE_SUPERVISOR"),
    ("treasury-a", "Treasury A", "TREASURY"),
    ("treasury-b", "Treasury B", "TREASURY"),
    ("finance", "Staf Finance", "FINANCE"),
    ("compliance", "Staf Compliance", "COMPLIANCE"),
    ("cs", "Staf CS", "CS"),
    ("auditor", "Auditor", "AUDITOR"),
]

AGREEMENT_VERSION = "CICIL-1"
AGREEMENT_BODY = """PERJANJIAN CICIL EMAS (DRAF, BELUM DITINJAU LEGAL)

1. Pembiayaan diberikan oleh mitra pembiayaan, bukan oleh REIDIG.
2. Emas tetap terkunci sebagai token cicilan sampai seluruh angsuran lunas, dan tidak bisa dijual atau dicetak sebelum itu.
3. Angsuran per bulan yang ditampilkan belum termasuk bunga dan biaya mitra.
4. Bila terjadi gagal bayar, token cicilan dapat dibatalkan sesuai ketentuan mitra.
5. Pengguna menyetujui pembagian data identitas dan transaksi kepada mitra untuk keperluan pembiayaan.
"""


def seed_reference(db: Session) -> None:
    """Data acuan yang dibutuhkan semua lingkungan: akun ledger, peran, aturan pajak, perjanjian."""
    for code, name, type_, side in ledger.ACCOUNTS:
        if not db.get(LedgerAccount, code):
            db.add(LedgerAccount(code=code, name=name, type=type_, normal_side=side))
    for code, description in ROLES.items():
        if not db.get(Role, code):
            db.add(Role(code=code, description=description))
    if not db.get(TaxRule, "PPH22_BUYBACK"):
        db.add(
            TaxRule(
                code="PPH22_BUYBACK",
                description="PPh 22 atas pembelian kembali emas di atas Rp 10 juta. Tarif perlu konfirmasi konsultan pajak.",
                rate_bps=150,
                threshold=10_000_000,
            )
        )
    if not db.get(FinancingAgreementVersion, AGREEMENT_VERSION):
        db.add(FinancingAgreementVersion(version=AGREEMENT_VERSION, body=AGREEMENT_BODY, content_hash=sha256_hex(AGREEMENT_BODY), is_current=True))
    db.flush()


def seed_demo(db: Session) -> None:
    if db.scalar(select(Sku.id).limit(1)):
        return
    seed_reference(db)

    for code, name, type_, area, estimate in LOCATIONS:
        db.add(Location(code=code, name=name, type=type_, area=area, pickup_estimate=estimate))
    for slug, name, role in ADMINS:
        db.add(AdminUser(email=f"{slug}@reidig.id", name=name, role=role, password_hash=hash_secret(ADMIN_PASSWORD)))
    for code, product, weight_mg, _, _ in CATALOG:
        brand = "Raja Emas" if code.startswith("LM") else product
        db.add(Sku(code=code, brand=brand, product=product, weight_mg=weight_mg))
    db.flush()

    spv = db.scalar(select(AdminUser).where(AdminUser.role == "WAREHOUSE_SUPERVISOR"))
    warehouse = db.scalar(select(Location).where(Location.code == "jkt"))
    pricing.record_prices(
        db, None, "emasmurniasli.co.id",
        [(code, buy, BUYBACK_PER_GRAM * mg // 1000) for code, _, mg, buy, _ in CATALOG],
        source_updated_at=datetime(2026, 11, 3, 2, 13, tzinfo=UTC),
    )

    serial = 500_000
    for code, _, _, _, qty in CATALOG:
        if not qty:
            continue
        sku = db.scalar(select(Sku).where(Sku.code == code))
        prefix = code.replace("-", "").replace(".", "")
        serials = [f"{prefix}-{serial + i}" for i in range(qty)]
        serial += qty
        inventory.receive(db, spv, sku.id, warehouse.id, serials, "Stok awal")
        for unit in db.scalars(select(Unit).where(Unit.sku_id == sku.id, Unit.status == "INBOUND_QC")):
            inventory.decide_qc(db, spv, unit.id, True)

    # Pengguna contoh yang sudah terverifikasi, dengan token dan saldo, seperti akun demo Android.
    user = identity.register(db, "Pengguna Demo", DEMO_EMAIL, DEMO_PASSWORD, "81234567890")
    db.add(Wallet(user_id=user.id, available=0, held=0))
    db.flush()
    identity.submit_kyc(db, user, "Pengguna Demo", "3171000000000001", date(1990, 1, 1))
    identity.set_pin(db, user, DEMO_PIN, None)
    identity.add_bank_account(db, user, "BCA", "1234561234", "Pengguna Demo")

    with clock.frozen_at(clock.now() - timedelta(days=3)):
        purchases = [("LMRE-1G", 2), ("LMRE-0.5G", 1)]
        prices = {code: buy for code, _, _, buy, _ in CATALOG}
        starting = sum(prices[c] * q for c, q in purchases) + 2_310_000
        topup = Topup(user_id=user.id, amount=starting, status="SUCCESS", source="SEED")
        db.add(topup)
        db.flush()
        wallet.credit(db, user.id, starting, "TOPUP", "TOPUP", topup.id)
        ledger.post(db, [(ledger.CUSTOMER_FUNDS, starting, 0), (ledger.WALLET_LIABILITY, 0, starting)], "TOPUP", topup.id, "Saldo awal demo")
        for code, qty in purchases:
            quote = pricing.create_quote(db, user, code, qty, "BUY")
            orders.buy(db, user, quote.id, "WALLET")
    db.commit()


def main() -> None:
    from app.main import init_db

    init_db()
    print("Data contoh siap.")
    print(f"  Pengguna: {DEMO_EMAIL} / {DEMO_PASSWORD}, PIN {DEMO_PIN}")
    print(f"  Admin:    spv@reidig.id (dan lainnya) / {ADMIN_PASSWORD}")


if __name__ == "__main__":
    main()
