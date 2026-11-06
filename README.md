# REIDIG (Raja Emas Indonesia Digital)

Platform investasi emas digital terpercaya yang terhubung langsung 1:1 dengan kepingan emas fisik ber-SN (*serial number*) resmi di brankas terpusat. REIDIG menyediakan pengalaman jual-beli instan (*buyback*), pencetakan fisik (*fulfillment* ke ekspedisi atau ambil di cabang), serta pembiayaan cicilan syariah berbasis agregator institusi keuangan.

---

## 🏛️ Arsitektur Ekosistem

Ekosistem REIDIG terdiri dari tiga subsistem utama dan dokumentasi arsitektur terpadu:

```text
reidig/
├── reidig-sys/       # Backend API (Python 3.12+, FastAPI, SQLAlchemy, Alembic, Celery)
├── android/          # Aplikasi Nasabah Android (Kotlin, Jetpack Compose, Coroutines)
├── dashboard/        # Web Portal Operasional (React 18, Vite, TypeScript, Tailwind CSS)
└── docs/             # Spesifikasi Teknis Backend, Desain Arsitektur, & Panduan Bisnis
```

| Repositori / Folder | Subsistem | Peran & Deskripsi |
|---|---|---|
| [`reidig-sys/`](file:///Users/rajaemasindonesia/Documents/emdig/reidig-sys) | **Core & API Backend** | Layanan RESTful API untuk perdagangan, brankas fisik, manajemen token, pembayaran, akuntansi ledger ganda (*double-entry*), dan integrasi pembiayaan. |
| [`android/`](file:///Users/rajaemasindonesia/Documents/emdig/android) | **Aplikasi Mobile** | Aplikasi mobile nasabah dengan antarmuka modern Jetpack Compose. Menangani portofolio token, jual-beli tunai, cetak fisik, dan cicilan emas *white-label*. |
| [`dashboard/`](file:///Users/rajaemasindonesia/Documents/emdig/dashboard) | **Back-Office Portal** | Dashboard operasional berjenjang (RBAC) untuk tim Gudang (inbound/QC/picking), Treasury (harga/quote), Finance (rekonsiliasi/pencairan), dan Compliance. |
| [`docs/`](file:///Users/rajaemasindonesia/Documents/emdig/docs) | **Dokumentasi Teknis** | Spesifikasi lengkap backend, alur invarian, skema database, dan diagram state machine. |

---

## 💎 Konsep Inti & Invarian Sistem

1. **Jaminan Fisik 1:1:** Setiap token emas digital di aplikasi diikat secara eksklusif ke satu keping fisik nyata di brankas (*serial number* terlacak audit). Tidak ada emas fiktif.
2. **Tanpa Bilangan Pecahan (Zero Floats):** Nilai uang disimpan dalam satuan Rupiah bulat (`integer`), dan bobot emas disimpan dalam miligram bulat (`integer`, misal: `1 gr = 1.000 mg`).
3. **Akuntansi Ledger Double-Entry:** Setiap transaksi moneter dicatat berpasangan (debit & kredit seimbang) serta terlindungi *append-only audit trail*.
4. **Rekonsiliasi Otomatis & Circuit Breaker:** Pemindaian integritas otomatis setiap 5 menit. Jika terdeteksi anomali stok atau ketidaksesuaian saldo nasabah dengan rekening penampungan (*escrow*), perdagangan otomatis dibekukan demi melindungi dana.

---

## 🤝 Skema Pembiayaan Cicilan Agregator Syariah

REIDIG menerapkan arsitektur dua database terpisah (*Two-Tier Decoupled Architecture*) untuk memisahkan kepemilikan emas tunai sah dengan risiko piutang pembiayaan:

```
[ Nasabah (Mobile) ]
        │  1. Simulasi Cicilan (White-Label)
        ▼
[ Installment Service ] ──> PKS & Akad Murabahah ──> [ Mitra Syariah / BPRS ]
  (Database Cicil)                                           │ 2. Pembayaran Lunas 100% di Muka
                                                             ▼
                                                    [ Core REIDIG Service ]
                                                       (Database Beli)
                                                             │
                                                             ▼ Keping Brankas Terkunci
                                                       [ LOCKED_INSTALLMENT ]
        ┌────────────────────────────────────────────────────┘
        │ 3. Angsuran Bulanan ke Agregator
        ▼
[ Pelunasan 100% ] ──(Webhook Webhook / Event)──> [ Aktivasi Token di Database Beli ]
                                                             │
                                                             ▼
                                                     [ Portofolio Aktif Nasabah ]
```

- **White-Label pada Menu Nasabah:** Pengguna melihat menu "Cicil Emas" yang bersih dan elegan tanpa logo pihak ketiga. Transparansi nama lembaga keuangan syariah (BPRS / Koperasi Jasa Keuangan Syariah) dicantumkan secara penuh pada dokumen Perjanjian Pembiayaan (*Akad Murabahah*).
- **Pelunasan di Awal (*Upfront Settlement*):** Mitra pembiayaan membayarkan 100% harga emas ke kas REIDIG di awal akad. Keping emas fisik di brankas langsung dikunci dengan status `LOCKED_INSTALLMENT`.
- **Pemisahan Database:**
  - **Database Cicil (`reidig_installment`):** Menyimpan kontrak cicilan, data pengajuan, jadwal angsuran bulanan, dan status pelunasan.
  - **Database Beli (`reidig_core`):** Menyimpan kepemilikan aset aktif nasabah, mutasi ledger, dan status inventori fisik.
- **Migrasi Saat Lunas:** Setelah nasabah menyelesaikan seluruh angsuran, webhook agregator memicu pencetakan token sah ke akun pengguna di Database Beli, dan status keping di brankas berubah menjadi `ALLOCATED` (aktif di portofolio pengguna).

---

## 🚀 Panduan Memulai Cepat (Quick Start)

### 1. Menjalankan Backend (`reidig-sys`)

```bash
cd reidig-sys
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements-dev.txt

# Menjalankan server lokal (Auto-init SQLite & seed data)
uvicorn app.main:app --reload --port 8000
```
- Dokumentasi API Swagger: <http://localhost:8000/docs>
- Worker Celery: `.venv/bin/celery -A app.worker worker --beat`
- Menjalankan Test Suite: `.venv/bin/python -m pytest`

### 2. Menjalankan Dashboard Web (`dashboard`)

```bash
cd dashboard
npm install
npm run dev
```
- Dashboard aktif di: <http://localhost:5173>
- Menjalankan Test Suite: `npm test`
- Build Produksi: `npm run build`

### 3. Menjalankan Aplikasi Android (`android`)

- Buka folder `android/` di **Android Studio Ladybug (atau versi lebih baru)**.
- Gunakan JDK 17 atau 21.
- Jalankan di Android Emulator (Target API 34+).
- Koneksi emulator ke backend lokal otomatis mengarah ke `http://10.0.2.2:8000`.
- Menjalankan Test Suite: `./gradlew :shared:jvmTest`

---

## 👥 Kredensial Uji Coba Default

- **Akun Nasabah (Mobile):** `demo@reidig.id` / `demo1234` (PIN: `147258`)
- **Akun Supervisor Brankas:** `spv@reidig.id` / `reidig-dev-123`
- **Akun Petugas Gudang:** `gudang-a@reidig.id` / `reidig-dev-123`
- **Akun Treasury:** `treasury-a@reidig.id` / `reidig-dev-123`
- **Akun Finance:** `finance@reidig.id` / `reidig-dev-123`
- **Akun Compliance:** `compliance@reidig.id` / `reidig-dev-123`

---

## 📄 Spesifikasi Lengkap

Untuk rincian kontrak API, skema tabel relasional, diagram alur penarikan dana, serta pengujian state machine, silakan pelajari dokumen:
👉 [**Spesifikasi Teknis Backend REIDIG**](file:///Users/rajaemasindonesia/Documents/emdig/docs/spesifikasi-backend.md)
