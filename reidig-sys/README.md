# REIDIG Sys

API emas digital REIDIG, mengikuti [spesifikasi backend](../docs/spesifikasi-backend.md). Dibangun dengan Python, FastAPI, SQLAlchemy, dan Alembic. Data disimpan di PostgreSQL 16, atau SQLite untuk pengembangan. Job latar belakang berjalan di Celery dengan Redis.

**Status: siap dicoba, belum siap produksi.** iFortepay, vendor KYC, dan mitra cicilan masih tiruan. Lihat [Yang masih tiruan](#yang-masih-tiruan).

## Menjalankan

```bash
cd reidig-sys
python3 -m venv .venv
.venv/bin/pip install -r requirements-dev.txt
.venv/bin/uvicorn app.main:app --reload --port 8000
```

Saat pertama dijalankan, server membuat database SQLite `reidig.db` dan mengisinya dengan data contoh. Dokumentasi API interaktif tersedia di <http://localhost:8000/docs>.

Dari emulator Android, alamat server adalah `http://10.0.2.2:8000`.

| Perintah | Fungsi |
|---|---|
| `.venv/bin/python -m pytest` | Menjalankan semua pengujian (SQLite) |
| `REIDIG_TEST_DATABASE_URL=postgresql+psycopg://… .venv/bin/python -m pytest` | Menjalankan pengujian di PostgreSQL. Isi database dihapus. |
| `.venv/bin/alembic upgrade head` | Menerapkan migrasi (produksi) |
| `.venv/bin/python -m app.seed` | Mengisi data contoh |
| `.venv/bin/celery -A app.worker worker --beat` | Menjalankan worker: penyapuan order kedaluwarsa, pengiriman pencairan, rekonsiliasi |

Konfigurasi memakai variabel lingkungan berawalan `REIDIG_`. Contohnya ada di [.env.example](.env.example).

## Akun contoh

- **Pengguna:** `demo@reidig.id` / `demo1234`, PIN `147258`. Akun ini sudah lolos KYC, punya saldo Rp 2.310.000 dan tiga token yang sudah bisa dijual.
- **Admin:** `spv@reidig.id`, `gudang-a@`, `gudang-b@`, `treasury-a@`, `treasury-b@`, `finance@`, `compliance@`, `cs@`, `auditor@` (semuanya `@reidig.id`), kata sandi `reidig-dev-123`.

## Daftar, masuk, dan lupa kata sandi

Alurnya sama dengan layar Android: email, lalu kode 6 angka, lalu data diri dan kata sandi.

1. `POST /v1/auth/email-code` dengan `{"email": "…", "purpose": "REGISTER"}`. Di dev, kode ikut dikembalikan di `dev_code` dan dicatat di log.
2. `POST /v1/auth/email-code/verify` dengan `{"email", "purpose", "code"}`. Hasilnya `email_ticket`, berlaku 30 menit dan sekali pakai.
3. `POST /v1/auth/register` dengan `{"email_ticket", "name", "email", "password", "date_of_birth"}`. Akun langsung masuk dan mendapat `access_token`.

Masuk lewat `POST /v1/auth/login`, keluar lewat `POST /v1/auth/logout`. Token dikirim sebagai `Authorization: Bearer …` dan berlaku 30 hari.

Lupa kata sandi memakai langkah 1 dan 2 dengan `purpose` `RESET_PASSWORD`, lalu `POST /v1/auth/password-reset` dengan `{"email_ticket", "new_password"}`. Semua sesi lama ikut keluar. Email yang tidak terdaftar tetap mendapat respons sukses, supaya tidak bisa dipakai menebak akun.

Batasan kode: berlaku 10 menit, kirim ulang setelah 30 detik, maksimal 5 kode per jam per email, dan maksimal 5 kali salah per kode.

## Mencoba pembayaran

QRIS dan VA mengembalikan kode tiruan. Di lingkungan dev, endpoint berikut meniru callback bertanda tangan dari luar:

- `POST /v1/dev/payments/{id}/simulate-paid` meniru pelunasan dari iFortepay, untuk tombol "Saya sudah bayar" di aplikasi.
- `POST /v1/dev/disbursements/{id}/simulate` dengan body `{"status": "SUCCESS" | "FAILED" | "TIMEOUT"}` meniru hasil transfer ke bank.
- `POST /v1/dev/financing-partner/simulate` meniru webhook mitra cicilan (`INSTALLMENT_PAID`, `OVERDUE`, `PAID_OFF`, `DEFAULTED`).

## Struktur

```text
app/
  core/            konfigurasi, database, error, Rupiah, ID token, idempotensi, trigger database
  catalog/         SKU
  pricing/         harga, quote, henti perdagangan
  inventory/       keping, penerimaan, QC, karantina, stock opname
  tokens/          siklus hidup token dan rantai hash event
  orders/          beli dan jual
  payments/        iFortepay (tiruan), callback, pencairan
  wallet/          saldo, penarikan, isi saldo
  fulfillment/     cetak fisik: JNE dan ambil di cabang
  financing/       cicilan lewat mitra
  identity/        akun, sesi, PIN, KYC, rekening bank
  ledger/          jurnal double-entry, aturan pajak
  admin/           API dashboard, RBAC, audit log
  reconciliation/  pemeriksaan invarian dan circuit breaker
migrations/        Alembic
tests/             pengujian alur dan invarian
```

## Cara invarian dijaga

| Invarian | Penjaga |
|---|---|
| 1. Satu unit, satu token aktif | Partial unique index `uq_unit_one_open_token` |
| 2–4. Keterkaitan token dan keping | State machine di service, diperiksa rekonsiliasi |
| 5. Tanpa stok fiktif | Keping hanya `AVAILABLE` lewat QC, diperiksa rekonsiliasi |
| 6. Status akhir token tidak berubah | Trigger `tokens_final_immutable` |
| 7. Jejak audit utuh | Trigger append-only, rantai hash di `token_events` |
| 8. Ledger seimbang | `ledger.post` menolak jurnal yang tidak seimbang, diperiksa rekonsiliasi |
| 9. Saldo tidak minus | `CHECK (available >= 0)` |
| 10. Dana pengguna tertutup | Rekonsiliasi: saldo = kewajiban ledger ≤ rekening penampungan |

Rekonsiliasi berjalan setiap 5 menit, atau manual lewat `POST /v1/admin/reconciliation/run`. Pelanggaran invarian 1–3 menghentikan perdagangan SKU terkait. Pelanggaran 9–10 membekukan penarikan dana sampai Finance membukanya kembali setelah hasil rekonsiliasi bersih.

Di produksi, cabut juga izin `UPDATE` dan `DELETE` pada tabel jejak dari role aplikasi. Perintahnya ada di `REVOKE_SQL` dalam [app/core/guards.py](app/core/guards.py).

## Konvensi API

- Rupiah dikirim sebagai string bilangan bulat (`"1450000"`). Nilai desimal ditolak.
- Berat dalam miligram. Waktu dalam UTC, format ISO 8601.
- Error selalu berbentuk `{"error": {"code": "INSUFFICIENT_STOCK", "message": "…"}}`. Kodenya sama dengan yang dipakai `MockRepository` di Android.
- Endpoint yang memindahkan uang atau emas menerima header `Idempotency-Key`.
- Endpoint jual, tarik dana, cetak, dan cicilan memerlukan `pin` di body. PIN terkunci setelah 5 kali salah.

## Yang masih tiruan

| Bagian | Sekarang | Nanti |
|---|---|---|
| iFortepay | Kode QR/VA dan tanda tangan HMAC rekaan (`app/payments/gateway.py`) | Klien dan format callback sesuai dokumentasi iFortepay |
| KYC | Langsung disetujui di dev (`REIDIG_KYC_AUTO_APPROVE`) | SDK atau API vendor KYC, kasus meragukan ke Compliance |
| Cek nama rekening | Dicocokkan dengan nama KTP saja | Inquiry rekening lewat iFortepay |
| Tarik harga | Input manual Treasury (`POST /v1/admin/prices`), batas usia harga nonaktif | Job penarik harga, lalu aktifkan `REIDIG_PRICE_MAX_AGE_SECONDS` |
| Mitra cicilan | Webhook rekaan; gagal bayar hanya mengubah status token dan keping | Kontrak mitra, termasuk penyelesaian keuangan saat gagal bayar |
| Login admin | Email dan kata sandi | SSO |
| Email kode verifikasi | Dicatat di log dan dikembalikan di `dev_code`. Di produksi ditolak (`app/identity/mailer.py`) | Penyedia email |
| OTP nomor HP | Belum ada | Penyedia OTP WhatsApp/SMS |
| Masuk dengan Google | Belum ada | Google Sign-In |

Nilai yang menunggu keputusan bisnis: tarif PPh 22 (sementara 1,5% di atas Rp 10 juta), batas pembelian harian, minimal penarikan, tenor cicilan, dan isi perjanjian cicilan (draf, belum ditinjau legal).

## Catatan terhadap spesifikasi

- **6.2a Penarikan dana** di dokumen spesifikasi terpotong. Alurnya mengikuti dashboard: dana ditahan dulu, baru keluar setelah bank mengonfirmasi. Status `UNKNOWN` tidak dicoba ulang otomatis; Finance memeriksa mutasi bank lalu mencatat hasilnya.
- **`tokens.origin_order_id`** diganti menjadi `origin_type` + `origin_id`, karena token cicilan tidak lahir dari order.
- **State machine** token dan unit sama persis dengan diagram di bagian 4, dikunci oleh [tests/test_state_machine.py](tests/test_state_machine.py). Keping pengganti saat tukar karantina diikat lewat `AVAILABLE → RESERVED → ALLOCATED`. Keping karantina yang masih bertoken (karena tukar gagal) dilepas lewat `QUARANTINE → AVAILABLE`, lalu diikat kembali dengan jalur yang sama.
- **Batal cetak:** pengguna hanya bisa membatalkan sebelum ada keping yang di-picking, sesuai diagram token. Supervisor gudang masih bisa membatalkan setelah picking (`PICKING → ALLOCATED` di diagram unit). Akibatnya token kembali `ACTIVE` setelah picking. Ini perlu dikonfirmasi, karena diagram token menulis "sebelum picking".
- **Check digit ID token** memakai Luhn mod 32, bukan check symbol Crockford mod 37, supaya tidak muncul simbol `* ~ $ =` pada kode yang dibaca dan diketik pengguna.
