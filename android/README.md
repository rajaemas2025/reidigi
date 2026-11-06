# REIDIG Android (Goldin Prototype v1)

Aplikasi mobile nasabah Raja Emas Indonesia Digital dengan Kotlin dan Jetpack Compose, mengimplementasikan spesifikasi antarmuka **Goldin Prototype v1** secara menyeluruh.

---

## ✨ Sorotan Antarmuka & Fitur

1. **Tipografi & Palet Warna Presisi (`Theme.kt`):**
   - **Font Judul (`TitleFontFamily`):** `Lato` (`lato_bold.ttf`, `lato_black.ttf`)
   - **Font Isi & Label (`BodyFontFamily`):** `Geist` (`geist_regular.ttf`, `geist_medium.ttf`, `geist_semibold.ttf`)
   - **Font Angka & Harga (`MonoFontFamily`):** `Geist Mono` (`geist_mono_medium.ttf`, `geist_mono_semibold.ttf`)
   - **Palet Warna:** Latar `Paper` (`#FBF9F5`), warna utama `Primary` (`#371F76`), aksen `Gold` (`#E1C162` / `#B59438`), serta indikator status `Good` (`#15803D`), `Warn` (`#B45309`), dan `Critical` (`#B91C1C`).
2. **Ikonografi & Aset Visual (`OnboardingArt.kt` & `res/drawable-nodpi/`):**
   - Vektor **Tabler SVG** (`PrototypeIcons`, `24x24`, `strokeWidth = 1.75dp`) untuk seluruh navigasi, aksi cepat, form input, dan lencana keamanan.
   - Ilustrasi 3D emas batangan (`img_gold_bars_hero.png`) dengan animasi mengambang pada `01 Landing`.
   - Logo resmi kanal OTP (`ic_whatsapp.png`) dan metode pembayaran/bank (`logo_qris.png`, `logo_bca.png`, `logo_bri.png`, `logo_mandiri.png`, `logo_bni.png`).
3. **Struktur Layar:**
   - **01 Landing (`WelcomeScreen`):** Aplikasi langsung dimulai di layar *landing* (tanpa *splash screen* terpisah) dengan 3 slide interaktif.
   - **07 Beranda (`HomeScreen`):** Kartu saldo emas ungu tua, **4 menu aksi cepat** (`Beli emas`, `Jual emas`, `Kelola kas`, `Cetak fisik`), kalkulator **Simulasi transaksi** (`Beli`/`Jual`, `Rp`/`gr`), dan grafik **Harga emas per gram** (`1H`, `1M`, `1B`, `6B`, `1T`).
   - **07b–12 e-KYC:** Persetujuan layanan, verifikasi nomor HP via WhatsApp/SMS, kamera foto e-KTP + review OCR, *liveness* wajah, pembuatan PIN 6 digit, dan kartu `Wallet ID` terenkripsi.
   - **Navigasi Bawah 4 Tab (`ReidigBottomBar`):** `Beranda`, `Dompet`, `Transaksi`, dan `Profil`.
   - **Cicilan Emas White-Label:** Menu simulasi cicilan tampil bersih tanpa nama pihak ketiga; identitas mitra syariah (**BPRS / Koperasi Syariah**) dan agregator ditampilkan secara transparan pada layar **Perjanjian Cicilan (Akad Murabahah)**.

---

## 🚀 Menjalankan & Menguji

Buka folder `android/` di Android Studio, atau jalankan lewat terminal dari root repositori:

```bash
# Menjalankan unit test aturan bisnis (shared module)
./android/gradlew -p android :shared:jvmTest

# Mengompilasi Kotlin debug
./android/gradlew -p android :app:compileDebugKotlin

# Membuat APK debug
./android/gradlew -p android :app:assembleDebug

# Memasang APK ke emulator/perangkat yang terhubung
./android/gradlew -p android :app:installDebug
```

---

## 🧪 Akun & Kode Uji Coba (Mock Mode)

- **Masuk Akun Terverifikasi:** Masukkan email & kata sandi apa saja pada layar **Masuk**. Akun otomatis memiliki saldo kas, portofolio keping emas aktif, dan rekening bank BCA terdaftar.
  - **PIN Transaksi:** `147258`
- **Daftar Akun Baru & e-KYC:** Pilih **Buat akun** di layar awal untuk mencoba alur registrasi dan verifikasi identitas.
  - **Kode OTP Email:** `654321`
  - **Kode OTP Nomor HP (WhatsApp/SMS):** `123456`

---

## 📂 Struktur Modul

```text
android/
├── shared/   # Aturan bisnis murni (Rules), pemformatan Rupiah/gram, dan MockRepository (Kotlin Multiplatform)
└── app/      # Antarmuka Jetpack Compose (Theme, PrototypeIcons, Auth, Home, KYC, Wallet, Portfolio, Installment)
```

Saat diintegrasikan penuh dengan server produksi, implementasi data pada `MockRepository` dapat dihubungkan langsung ke endpoint REST API sesuai [Spesifikasi Teknis & Panduan Backend](../docs/spesifikasi-backend.md).
