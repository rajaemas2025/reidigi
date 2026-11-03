# Dashboard REIDIG

Dashboard internal untuk gudang, treasury, finance, compliance, dan CS. Dibangun dengan Vite, React, dan TypeScript.

**Status: memakai data tiruan.** Semua data berasal dari backend tiruan di browser. Belum terhubung ke backend atau stok sungguhan.

## Menjalankan

```bash
npm install
npm run dev
```

| Perintah | Fungsi |
|---|---|
| `npm test` | Menguji aturan bisnis di API tiruan |
| `npm run typecheck` | Memeriksa tipe TypeScript |
| `npm run build` | Build produksi |

Ganti peran lewat pilihan "Masuk sebagai" di kanan atas. Pilihan ini nanti diganti SSO + MFA. Kode pengambilan di cabang saat ini selalu `246810`.

## Struktur

```text
src/api/core.ts      aturan bisnis tiruan, mengikuti docs/02-spesifikasi-backend.md
src/api/seed.ts      data contoh
src/api/index.ts     satu pintu ke data; localStorage untuk data tiruan
src/pages/           halaman per modul
src/components/ui.tsx komponen bersama
src/lib/             konteks aplikasi, format, dan label status
```

## Menghubungkan ke backend

Ganti objek `api` di `src/api/index.ts` dengan panggilan ke `/admin/v1`. Halaman tidak perlu diubah selama bentuk datanya sama.

Pembatasan peran di dashboard hanya untuk tampilan. **Backend wajib menegakkan semua aturan ini lagi**, termasuk hitung buta, hitung ulang oleh orang berbeda, dan persetujuan dua orang.
