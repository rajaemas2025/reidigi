# REIDIG — Goldin (Raja Emas Indonesia Digital)

Platform tabungan, jual-beli instan (*buyback*), pencetakan fisik, dan cicilan emas fisik 24K yang terhubung langsung **1:1** dengan kepingan emas ber-SN (*serial number*) resmi di brankas terpusat Raja Emas Indonesia.

---

## 🏛️ Arsitektur Ekosistem

Ekosistem REIDIG terdiri dari tiga subsistem utama dan dokumentasi teknis terpadu:

```text
reidig/
├── reidig-sys/       # Backend API (Python 3.12+, FastAPI, SQLAlchemy 2.0, Alembic, Celery)
├── android/          # Aplikasi Nasabah Android — Desain Goldin Prototype v1 (Kotlin, Jetpack Compose)
├── dashboard/        # Web Portal Operasional (React 18, Vite, TypeScript, Tailwind CSS)
└── docs/             # Spesifikasi Teknis Backend, Skema Database, & Panduan Integrasi
```

| Folder | Subsistem | Peran & Deskripsi |
|---|---|---|
| [`reidig-sys/`](file:///Users/rajaemasindonesia/Documents/emdig/reidig-sys) | **Core & API Backend** | Layanan RESTful API untuk perdagangan emas tunai (`reidig_core`), brankas fisik, manajemen token, pembayaran, akuntansi *double-entry ledger*, dan integrasi cicilan agregator (`reidig_installment`). |
| [`android/`](file:///Users/rajaemasindonesia/Documents/emdig/android) | **Aplikasi Mobile (Goldin v1)** | Aplikasi Android nasabah berbasis Jetpack Compose yang mengimplementasikan desain **Goldin Prototype v1** secara presisi (font `Lato`, `Geist`, `Geist Mono`, ikon Tabler SVG, & aset 3D). |
| [`dashboard/`](file:///Users/rajaemasindonesia/Documents/emdig/dashboard) | **Back-Office Portal** | Portal operasional berbasis *Role-Based Access Control* (RBAC) untuk tim Gudang (inbound/QC/picking), Treasury (harga/quote), Finance (rekonsiliasi/pencairan), dan Compliance. |
| [`docs/`](file:///Users/rajaemasindonesia/Documents/emdig/docs) | **Dokumentasi Teknis** | [Spesifikasi Teknis & Panduan Backend](file:///Users/rajaemasindonesia/Documents/emdig/docs/spesifikasi-backend.md), aturan invarian, skema dua database, dan pemetaan endpoint API. |

---

## 🎨 Desain Antarmuka Mobile (Goldin Prototype v1)

Aplikasi Android di [`android/`](file:///Users/rajaemasindonesia/Documents/emdig/android) diselaraskan penuh dengan spesifikasi **Goldin Prototype v1**:

1. **Tipografi Resmi (`res/font/`):**
   - **Judul (`TitleFontFamily`):** `Lato` (`700` Bold, `900` Black)
   - **Body & Label (`BodyFontFamily`):** `Geist` (`400` Regular, `500` Medium, `600` SemiBold)
   - **Angka, Harga & Gramasi (`MonoFontFamily`):** `Geist Mono` (`500` Medium, `600` SemiBold)
2. **Palet Warna & Visual:**
   - Latar hangat `Paper` (`#FBF9F5`), aksen utama `Primary` (`#371F76` – `#5436AA`), dan aksen emas `Gold` (`#E1C162` / `#B59438`).
   - Seluruh ikon menggunakan vektor **Tabler SVG** (`24x24`, `strokeWidth = 1.75dp`) serta aset resmi (`img_gold_bars_hero.png`, `ic_whatsapp.png`, `logo_qris.png`, `logo_bca.png`, `logo_bri.png`, `logo_mandiri.png`, `logo_bni.png`).
3. **Alur Layar Utama:**
   - **01 Landing (`WelcomeScreen`):** Aplikasi langsung dibuka pada layar *landing* (tanpa *splash screen* terpisah) dengan header `"Goldin"`, pil harga emas *live*, dan karusel 3 slide interaktif (ilustrasi 3D emas batangan mengambang, kalkulator simulasi `Rp10rb`–`Rp500rb`, dan informasi keamanan brankas).
   - **02–06 Autentikasi & Registrasi:** Login, pendaftaran 3 langkah dengan *stepper* (`Buat akun` → `Verifikasi` 6 kotak OTP → `Data diri`), serta layar `06 Selesai` dengan kartu ungu *Tabungan Emas*.
   - **07 Beranda (`HomeScreen`):**
     - Header sapaan pengguna & tombol notifikasi/tabel harga.
     - Banner ajakan verifikasi e-KYC (bagi akun yang belum terverifikasi).
     - Kartu utama ungu tua (*Saldo emas* dalam gram + estimasi *buyback* & *Saldo kas* Rupiah).
     - **4 Menu Aksi Cepat (*Quick Actions*):** `Beli emas`, `Jual emas`, `Kelola kas`, dan `Cetak fisik`.
     - Kartu **Simulasi transaksi** (`Beli` / `Jual` dengan konversi instan `Rp` ⇄ `gr`).
     - Kartu **Harga emas per gram** lengkap dengan grafik interaktif (`1H`, `1M`, `1B`, `6B`, `1T`).
     - Kartu jaminan keamanan *Gold Encryption AES-256 & terdaftar resmi Bappebti*.
   - **07b–12 Verifikasi Identitas (e-KYC):** Persetujuan layanan, input nomor HP (`+62`), pemilihan kanal OTP (`WhatsApp` / `SMS`), kamera foto e-KTP + peninjauan data OCR, pengenalan *liveness* wajah, pembuatan 6 digit PIN, hingga layar selesai dengan `Wallet ID` terenkripsi.
   - **Navigasi Bawah 4 Tab (`ReidigBottomBar`):** `Beranda`, `Dompet` (*Dompet Emas*, *Dompet Rupiah*, *Rekening bank*, *Mutasi terbaru*), `Transaksi` (*Beli*, *Jual*, *Cetak*), dan `Profil` (termasuk akses pengajuan *Cicil Emas* dan daftar kontrak cicilan aktif).

---

## 💎 Konsep Inti & Invarian Sistem

1. **Jaminan Fisik 1:1:** Setiap token emas digital di aplikasi diikat secara eksklusif ke tepat satu keping fisik nyata di brankas (*serial number* terlacak audit). Tidak ada emas fiktif.
2. **Tanpa Bilangan Pecahan (*Zero Floats*):** Nilai uang disimpan dalam satuan Rupiah bulat (`integer`), dan bobot emas disimpan dalam miligram bulat (`integer`, misal: `1 gr = 1.000 mg`).
3. **Akuntansi Ledger Double-Entry:** Setiap transaksi moneter dicatat berpasangan (`Total Debit = Total Kredit`) serta dilindungi *append-only audit trail*.
4. **Rekonsiliasi Otomatis & Circuit Breaker:** Pemindaian integritas otomatis setiap 5 menit. Jika terdeteksi anomali stok atau ketidaksesuaian saldo nasabah dengan rekening penampungan (*escrow*), perdagangan otomatis dibekukan sementara.

---

## 🤝 Skema Pembiayaan Cicilan Agregator Syariah

REIDIG menerapkan arsitektur dua database terpisah (*Two-Tier Decoupled Architecture*) untuk memisahkan kepemilikan emas tunai sah dengan piutang pembiayaan:

```text
[ Nasabah (Mobile) ]
        │  1. Simulasi Cicilan (White-Label Menu)
        ▼
[ Installment Service ] ──> Akad Murabahah ──> [ Mitra Syariah: BPRS / Koperasi ]
  (Database Cicil)                                      │ 2. Pelunasan 100% di Muka
                                                        ▼
                                               [ Core REIDIG Service ]
                                                  (Database Beli)
                                                        │
                                                        ▼ Keping Fisik Brankas Terkunci
                                                  [ LOCKED_INSTALLMENT ]
        ┌───────────────────────────────────────────────┘
        │ 3. Angsuran Bulanan ke Agregator
        ▼
[ Pelunasan 100% ] ──(Webhook PAID_OFF)──> [ Aktivasi Token di Database Beli ]
                                                        │
                                                        ▼
                                                [ Portofolio Aktif Nasabah ]
```

- **Menu White-Label:** Menu pengajuan cicilan di aplikasi tampil bersih tanpa mencantumkan nama/logo pihak ketiga. Identitas resmi lembaga keuangan syariah mitra (**BPRS / Koperasi Syariah**) dan agregator hanya ditampilkan secara transparan pada lembar **Perjanjian Cicilan (*Akad Murabahah*)**.
- **Pelunasan 100% di Awal (*Upfront Settlement*):** Mitra pembiayaan melunasi 100% harga emas ke REIDIG di awal akad. Keping fisik di brankas langsung dikunci dengan status `LOCKED_INSTALLMENT`.
- **Migrasi Saat Lunas:** Ketika seluruh angsuran lunas (`PAID_OFF`), webhook agregator memicu penerbitan token kepemilikan sah (`ACTIVE`) di **Database Beli (`reidig_core`)**, sehingga emas masuk ke portofolio aktif nasabah.

---

## 🚀 Panduan Memulai Cepat (Quick Start)

### 1. Menjalankan Backend (`reidig-sys`)

```bash
cd reidig-sys
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements-dev.txt

# Menjalankan server lokal (otomatis membuat SQLite reidig.db & seed data awal)
uvicorn app.main:app --reload --port 8000
```
- **Swagger UI (Dokumentasi Interaktif):** <http://localhost:8000/docs>
- **Background Worker (Celery):** `.venv/bin/celery -A app.worker worker --beat`
- **Menjalankan Test Suite Backend:** `.venv/bin/python -m pytest`

### 2. Menjalankan Aplikasi Android (`android`)

```bash
# Menjalankan unit test aturan bisnis shared module
./android/gradlew -p android :shared:jvmTest

# Mengompilasi & membuat APK debug
./android/gradlew -p android :app:assembleDebug
```
- Buka folder [`android/`](file:///Users/rajaemasindonesia/Documents/emdig/android) di **Android Studio Ladybug (atau versi lebih baru)** dengan **JDK 17 / 21**.
- Dari Android Emulator, koneksi ke backend lokal mengarah ke `http://10.0.2.2:8000`.

### 3. Menjalankan Dashboard Operasional (`dashboard`)

```bash
cd dashboard
npm install
npm run dev
```
- **Portal Web Operasional:** <http://localhost:5173>
- **Menjalankan Test Suite Web:** `npm test`

---

## 🐳 Panduan Deployment di Dokploy (3 Deployment Terpisah)

Repositori ini telah dilengkapi `Dockerfile` khusus untuk masing-masing layanan (**API**, **Dashboard**, dan **APK Download Server**) yang mendukung *Build Context* baik dari root (`.`) maupun dari masing-masing subfolder:

| Layanan Deployment | Build Type | Context Path | Dockerfile Path | Container Port (Traefik) |
|---|---|---|---|---|
| **1. API Backend** | `Dockerfile` | `./reidig-sys` *(atau `.`)* | `reidig-sys/Dockerfile` *(atau `Dockerfile` jika context `./reidig-sys`)* | **`8000`** |
| **2. Web Dashboard** | `Dockerfile` | `./dashboard` *(atau `.`)* | `dashboard/Dockerfile` *(atau `Dockerfile` jika context `./dashboard`)* | **`80`** |
| **3. Android APK** | `Dockerfile` | `./android` *(atau `.`)* | `android/Dockerfile` *(atau `Dockerfile` jika context `./android`)* | **`80`** |

> **Catatan Deployment APK di Dokploy:** [`android/Dockerfile`](file:///Users/rajaemasindonesia/Documents/emdig/android/Dockerfile) menggunakan *multi-stage build* (JDK 21 + Android SDK 36 untuk mem-build `app-debug.apk`, lalu Nginx pada port `80` untuk menyajikan halaman web unduh `/reidig-goldin.apk`) sehingga container tetap hidup (*running*) dan dapat diakses langsung melalui domain Dokploy Anda.

---

## 👥 Kredensial Uji Coba Default

- **Akun Nasabah (Mobile):** `demo@reidig.id` / `demo1234` (PIN Transaksi: `147258`, OTP Email Demo: `654321`, OTP HP Demo: `123456`)
- **Supervisor Brankas:** `spv@reidig.id` / `reidig-dev-123`
- **Petugas Gudang:** `gudang-a@reidig.id` / `reidig-dev-123`
- **Treasury:** `treasury-a@reidig.id` / `reidig-dev-123`
- **Finance:** `finance@reidig.id` / `reidig-dev-123`
- **Compliance:** `compliance@reidig.id` / `reidig-dev-123`

---

## 📄 Dokumentasi & Panduan Backend Lengkap

Untuk rincian arsitektur, skema database, panduan konfigurasi server, daftar lengkap endpoint API, dan simulasi webhook, baca:
- 👉 [**Spesifikasi Teknis & Panduan Backend REIDIG (`docs/spesifikasi-backend.md`)**](file:///Users/rajaemasindonesia/Documents/emdig/docs/spesifikasi-backend.md)
- 👉 [**Panduan Operasional Layanan Backend (`reidig-sys/README.md`)**](file:///Users/rajaemasindonesia/Documents/emdig/reidig-sys/README.md)
- 👉 [**Panduan Aplikasi Android (`android/README.md`)**](file:///Users/rajaemasindonesia/Documents/emdig/android/README.md)
