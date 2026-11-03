# REIDIG Android

Aplikasi emas digital Raja Emas Indonesia, dengan Kotlin dan Jetpack Compose.

**Status: memakai data tiruan.** Semua data berasal dari backend tiruan di dalam aplikasi dan hilang saat aplikasi ditutup. Belum terhubung ke backend, iFortepay, atau vendor KYC.

## Menjalankan

Buka folder ini di Android Studio, atau lewat terminal:

```bash
./gradlew :app:installDebug
```

| Perintah | Fungsi |
|---|---|
| `./gradlew :shared:jvmTest` | Menguji aturan bisnis |
| `./gradlew :app:assembleDebug` | Membuat APK debug |

## Mencoba

- **Akun terverifikasi:** pada layar Masuk, isi email dan kata sandi apa pun. Akun ini sudah punya token dan saldo. PIN `147258`.
- **Akun baru:** pilih Buat akun untuk mencoba pendaftaran dan KYC. Kode email `654321`, kode HP `123456`.

## Struktur

```text
shared/   aturan bisnis dan backend tiruan, tanpa ketergantungan Android, siap dipakai iOS
app/      layar Compose
```

## Yang masih simulasi

| Bagian | Sekarang | Nanti |
|---|---|---|
| Foto KTP | Kamera sungguhan; foto hanya di memori, tidak dibaca dan tidak dikirim | SDK atau API vendor KYC |
| Pindai wajah | Kamera depan sungguhan; wajah dan gerakan belum diperiksa, dan selalu lolos | SDK liveness vendor KYC |
| Pembayaran QRIS dan VA | Kode tiruan; tombol "Saya sudah bayar" langsung melunasi | Backend memanggil iFortepay |
| Estimasi waktu, biaya cetak, mitra cicilan | Nilai sementara | Menunggu keputusan bisnis |
| Pelunasan cicilan | Belum bisa dicoba di layar, hanya di pengujian | Callback dari mitra |

Saat backend siap, ganti `MockRepository` dengan klien HTTP sesuai [spesifikasi backend](../docs/02-spesifikasi-backend.md). 
