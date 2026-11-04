"""Aturan emas di spesifikasi bagian 3, dijaga oleh constraint database dan diperiksa rekonsiliasi."""

import uuid

import pytest
from sqlalchemy import select, text
from sqlalchemy.exc import IntegrityError

from app.core.ids import is_valid_token_code, new_token_code
from app.inventory.models import Unit
from app.ledger import service as ledger
from app.reconciliation import service as reconciliation
from app.tokens.models import Token
from app.wallet.models import Wallet


def failed(db):
    return [(r.id, r.detail) for r in reconciliation.check_invariants(db) if not r.ok]


def test_seed_memenuhi_semua_invarian(db):
    assert failed(db) == []


def test_kode_token_sesuai_format_dan_check_digit():
    code = new_token_code("LMRE-1G")
    assert code.startswith("RET-LMRE1G-") and len(code.split("-")[2]) == 11
    assert is_valid_token_code(code)
    tampered = code[:-2] + ("0" if code[-2] != "0" else "1") + code[-1]
    assert not is_valid_token_code(tampered)


def test_inv1_satu_unit_satu_token_aktif_dijaga_index(db):
    token = db.scalar(select(Token).where(Token.status == "ACTIVE").limit(1))
    with pytest.raises(IntegrityError):
        db.add(
            Token(
                id=uuid.uuid4(), token_code="RET-X-0000000000", user_id=token.user_id, unit_id=token.unit_id, sku_id=token.sku_id,
                status="ACTIVE", acquired_price=1, origin_type="ORDER", origin_id=0,
            )
        )
        db.flush()


def test_inv6_token_final_tidak_bisa_diubah_di_database(db):
    token = db.scalar(select(Token).where(Token.status == "ACTIVE").limit(1))
    db.execute(text("UPDATE tokens SET status='RETURNED' WHERE id=:id"), {"id": token.id.hex})
    with pytest.raises(Exception, match="immutable"):
        db.execute(text("UPDATE tokens SET status='ACTIVE' WHERE id=:id"), {"id": token.id.hex})


@pytest.mark.parametrize("table", ["token_events", "unit_events", "wallet_entries", "ledger_entries", "audit_logs"])
def test_inv7_tabel_jejak_append_only(db, table):
    with pytest.raises(Exception, match="append-only"):
        db.execute(text(f"UPDATE {table} SET id = id WHERE id = (SELECT MIN(id) FROM {table})"))
    db.rollback()
    with pytest.raises(Exception, match="append-only"):
        db.execute(text(f"DELETE FROM {table}"))


def test_inv8_jurnal_tidak_seimbang_ditolak(db):
    with pytest.raises(ledger.UnbalancedJournal):
        ledger.post(db, [(ledger.WALLET_LIABILITY, 100, 0), (ledger.GOLD_SALES, 0, 99)], "TEST", 1)


def test_inv9_saldo_minus_ditolak_database(db):
    wallet = db.scalar(select(Wallet).limit(1))
    with pytest.raises(IntegrityError):
        db.execute(text("UPDATE wallets SET available = -1 WHERE user_id = :id"), {"id": wallet.user_id})


def test_pelanggaran_stok_menghentikan_sku_dan_saldo_membekukan_penarikan(db, user, admin):
    # Rusak data di luar aplikasi: keping bertoken dibuat AVAILABLE (melanggar INV-2 dan INV-3).
    token = db.scalar(select(Token).where(Token.status == "ACTIVE").limit(1))
    db.execute(text("UPDATE units SET status='AVAILABLE' WHERE id=:id"), {"id": token.unit_id})
    # Rusak saldo: read-model tidak lagi cocok dengan ledger.
    db.execute(text("UPDATE wallets SET available = available + 1 WHERE user_id=:id"), {"id": token.user_id})
    db.commit()

    finance = admin("finance")
    results = {r["id"]: r["ok"] for r in finance.ok("post", "/v1/admin/reconciliation/run")}
    assert not results["INV-3"] and not results["INV-10"]

    sku_code = db.scalar(text("SELECT code FROM skus WHERE id=:id"), {"id": token.sku_id})
    r = user.post("/v1/quotes", {"sku_code": sku_code, "quantity": 1})
    assert r.json()["error"]["code"] == "TRADING_HALTED"
    r = user.post("/v1/wallet/withdrawals", {"amount": "50000", "bank_account_id": 1, "pin": "147258"})
    assert r.json()["error"]["code"] == "WITHDRAWALS_FROZEN"
    # Penarikan baru boleh dibuka setelah rekonsiliasi bersih.
    assert finance.post("/v1/admin/withdrawals/unfreeze").json()["error"]["code"] == "INVALID_STATE"


def test_invarian_4_stok_dan_token_seimbang_setelah_banyak_transaksi(db, user, fake_clock):
    for _ in range(3):
        assert user.buy("LMRE-0.1G").status_code == 201
    fake_clock.advance(days=2)
    codes = [t["code"] for t in user.tokens() if t["sku_code"] == "LMRE-0.1G"]
    q = user.quote("LMRE-0.1G", 2, "SELL")
    assert user.post("/v1/orders/sell", {"quote_id": q["id"], "token_codes": codes[:2], "pin": "147258"}).status_code == 201
    db.expire_all()
    assert failed(db) == []
    assert db.scalar(select(Unit).where(Unit.status == "RESERVED")) is None
