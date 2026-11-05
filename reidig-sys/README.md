# REIDIG Sys

API backend dan layanan inti sistem emas digital REIDIG, mengacu pada [spesifikasi backend](../docs/spesifikasi-backend.md). Mengimplementasikan **Two-Tier Decoupled Architecture** yang memisahkan **Core Emas Tunai (Database Beli)** dari **Sub-sistem Pembiayaan Cicilan Agregator (Database Cicil)**.

Dibangun dengan Python 3.12+, FastAPI, SQLAlchemy 2.0, dan Alembic. Penyimpanan data utama menggunakan PostgreSQL 16 (atau SQLite untuk pengujian lokal). Antrean tugas latar belakang dan penjadwalan dikelola dengan Celery dan Redis 7.

**Status: Siap pengujian integrasi (staging).** Integrasi payment gateway (iFortepay), vendor KYC, dan webhook agregator pembiayaan syariah menggunakan adapter mock sandbox.

---

## Menjalankan Server Lokal

```bash
cd reidig-sys
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements-dev.txt
uvicorn app.main:app --reload --port 8000
```

Saat pertama kali dijalankan, server secara otomatis menginisialisasi SQLite database lokal `reidig.db` dan menyuntikkan data *seed* awal.
- Dokumentasi interaktif Swagger UI: <http://localhost:8000/docs>
- Dari Android Emulator, arahkan host API ke `http://10.0.2.2:8000`.

| Perintah | Deskripsi & Tujuan |
|---|---|
| `.venv/bin/python -m pytest` | Menjalankan seluruh unit test & state machine test (SQLite) |
| `REIDIG_TEST_DATABASE_URL=postgresql+psycopg://… .venv/bin/python -m pytest` | Menjalankan pengujian di PostgreSQL (database uji akan dibersihkan) |
| `.venv/bin/alembic upgrade head` | Menjalankan migrasi skema database (lingkungan staging/produksi) |
| `.venv/bin/python -m app.seed` | Mengisi ulang data contoh (master SKU, brankas, harga, akun uji coba) |
| `.venv/bin/celery -A app.worker worker --beat` | Menjalankan background worker: auto-expire pesanan, rekonsiliasi berkala, sweep pencairan |

Konfigurasi environment dapat dilihat pada template [`.env.example`](.env.example).

---

## Akun Default Uji Coba

- **Pengguna Nasabah:** `demo@reidig.id` / `demo1234`, PIN: `147258`. Akun telah lolos KYC, memiliki saldo dompet Rp 2.310.000, dan 3 token aktif siap jual/cetak.
- **Akun Tim Operasional (Password: `reidig-dev-123`):**
  - Supervisor: `spv@reidig.id`
  - Petugas Gudang: `gudang-a@reidig.id`, `gudang-b@reidig.id`
  - Treasury: `treasury-a@reidig.id`, `treasury-b@reidig.id`
  - Finance: `finance@reidig.id`
  - Compliance: `compliance@reidig.id`
  - Customer Service: `cs@reidig.id`
  - Auditor: `auditor@reidig.id`

---

## Alur Autentikasi Pengguna

1. `POST /v1/auth/email-code` dengan body `{"email": "...", "purpose": "REGISTER"}`. Di mode dev, kode OTP dicatat pada log dan dikembalikan pada atribut `dev_code`.
2. `POST /v1/auth/email-code/verify` dengan body `{"email", "purpose", "code"}` menghasilkan `email_ticket` sekali pakai (berlaku 30 menit).
3. `POST /v1/auth/register` dengan menyertakan `email_ticket`, profil nasabah, dan kata sandi. Akun langsung aktif dan menerima Bearer `access_token`.

Untuk lupa kata sandi, alur menggunakan `purpose: RESET_PASSWORD` dan memanggil `POST /v1/auth/password-reset`.

---

## Simulasi Pembayaran & Webhook Agregator

Di lingkungan sandbox/dev, disediakan endpoint mock untuk menguji siklus hidup transaksi tanpa gateway eksternal:

- `POST /v1/dev/payments/{id}/simulate-paid` — Mensimulasikan notifikasi pelunasan sukses dari payment gateway.
- `POST /v1/dev/disbursements/{id}/simulate` — Mensimulasikan status pengiriman dana bank (`SUCCESS`, `FAILED`, `TIMEOUT`).
- `POST /v1/dev/financing-partner/simulate` — Mensimulasikan webhook dari agregator cicilan (`INSTALLMENT_PAID`, `OVERDUE`, `PAID_OFF`, `DEFAULTED`). Ketika status `PAID_OFF` diterima, keping jaminan (`LOCKED_INSTALLMENT`) berubah menjadi `ALLOCATED` dan token kepemilikan sah otomatis terbit ke Database Beli milik pengguna.

---

## Struktur Direktori Modul

```text
app/
  core/            # Konfigurasi, koneksi database, penanganan error, helper Rupiah/Token, trigger DB
  catalog/         # Master data SKU dan lini gramatur emas
  pricing/         # Penentuan harga acuan, quote lock TTL, dan circuit breaker henti dagang
  inventory/       # Pengelolaan keping brankas: inbound, QC, karantina, stock opname, jaminan cicilan
  tokens/          # Penerbitan token sah, mutasi status, dan rantai audit hash SHA-256
  orders/          # Alur order beli tunai dan jual kembali (buyback)
  payments/        # Adapter payment gateway inbound & pencairan outbound
  wallet/          # Saldo rupiah, penarikan dana, dan mutasi dompet
  fulfillment/     # Pemenuhan cetak fisik: pengiriman ekspedisi dan ambil cabang
  financing/       # Integrasi cicilan agregator, kontrak syariah Murabahah, dan webhook
  identity/        # Akun nasabah, sesi, PIN transaksi, verifikasi KYC, dan rekening bank
  ledger/          # Pembukuan akuntansi double-entry (keseimbangan debit/kredit dan pajak)
  admin/           # Endpoint operasional dashboard, Role-Based Access Control (RBAC), log audit
  reconciliation/  # Job berkala verifikasi invarian sistem dan auto-freeze proteksi dana
migrations/        # Riwayat migrasi skema Alembic
tests/             # Suite pengujian otomatis (invarian, state machine, dan alur transaksi)
```

---

## Penjagaan Invarian Sistem

| Invarian | Mekanisme Penjaga Teknis |
|---|---|
| 1. Satu unit, maksimal satu hak aktif | Partial unique index `uq_unit_one_open_token` |
| 2–4. Keterkaitan token dan keping fisik | State machine terisolasi pada service layer, diverifikasi berkala oleh worker rekonsiliasi |
| 5. Larangan stok fiktif | Keping fisik wajib lolos status `INBOUND_QC` menjadi `AVAILABLE` sebelum dapat dipesan atau dicicil |
| 6. Imutabilitas status akhir | Database trigger `tokens_final_immutable` |
| 7. Jejak audit utuh | Database trigger append-only & rantai hash pada tabel `token_events` |
| 8. Ledger berimbang | `ledger.post` menolak keras pencatatan jurnal yang debit != kredit |
| 9. Saldo anti minus | Batasan integritas database: `CHECK (available >= 0)` |
| 10. Jaminan kecukupan dana | Rekonsiliasi otomatis: `Total Saldo Pengguna = Total Kewajiban Ledger <= Rekening Escrow` |

Rekonsiliasi dijalankan berkala oleh Celery Beat setiap 5 menit (atau via pemicu manual `POST /v1/admin/reconciliation/run`). Jika terdeteksi inkonsistensi pada Invarian 1–3, perdagangan SKU terkait otomatis dihentikan sementara.

---

## Konvensi Standar API

- **Mata Uang:** Nilai Rupiah selalu dikirim sebagai bilangan bulat dalam format string (contoh: `"1450000"`). Desimal tidak diperbolehkan.
- **Berat Emas:** Menggunakan bilangan bulat dalam satuan miligram (contoh: `1000` untuk 1 gram).
- **Format Error:** Standar JSON seragam: `{"error": {"code": "KODE_ERROR", "message": "Pesan deskriptif"}}`.
- **Idempotensi:** Seluruh endpoint finansial dan mutasi stok mewajibkan header `Idempotency-Key`.
- **Otorisasi Transaksi:** Operasi jual, penarikan dana, cetak fisik, dan persetujuan akad cicilan mewajibkan verifikasi `pin` nasabah. PIN otomatis terkunci setelah 5 kali kegagalan berturut-turut.
