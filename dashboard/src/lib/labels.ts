import type { Tone } from '../components/ui'
import type { CountStatus, PrintOrder, Role, UnitStatus, VarianceType, Withdrawal } from '../api/types'

export const ROLE_LABEL: Record<Role, string> = {
  WAREHOUSE_STAFF: 'Petugas Gudang',
  WAREHOUSE_SUPERVISOR: 'Supervisor Gudang',
  TREASURY: 'Treasury',
  FINANCE: 'Finance',
  COMPLIANCE: 'Compliance',
  CS: 'Customer Service',
  AUDITOR: 'Auditor Internal',
}

export const UNIT_STATUS: Record<UnitStatus, [string, Tone]> = {
  INBOUND_QC: ['Menunggu QC', 'info'],
  AVAILABLE: ['Siap Dijual', 'good'],
  RESERVED: ['Sedang Dipesan', 'info'],
  ALLOCATED: ['Terikat Emas Digital', 'neutral'],
  PICKING: ['Sedang Disiapkan', 'info'],
  SHIPPED: ['Dalam Pengiriman', 'info'],
  READY_PICKUP: ['Siap Diambil di Cabang', 'info'],
  DELIVERED: ['Diterima Pelanggan', 'neutral'],
  QUARANTINE: ['Karantina Khusus', 'critical'],
  WRITTEN_OFF: ['Dihapusbukukan', 'critical'],
}

export const COUNT_STATUS: Record<CountStatus, [string, Tone]> = {
  COUNTING: ['Sedang Dihitung', 'info'],
  RECOUNT: ['Hitung Ulang', 'warning'],
  AWAITING_APPROVAL: ['Menunggu Supervisor', 'warning'],
  AWAITING_FINANCE: ['Menunggu Persetujuan Finance', 'warning'],
  APPROVED: ['Selesai & Disetujui', 'good'],
}

export const VARIANCE: Record<VarianceType, string> = {
  MISSING: 'Fisik Tidak Ditemukan',
  UNEXPECTED: 'Nomor Seri Tidak Terdaftar',
  WRONG_LOCATION: 'Salah Penempatan Lokasi',
}

export const PRINT_STATUS: Record<PrintOrder['status'], [string, Tone]> = {
  WAITING: ['Menunggu Penyiapan', 'warning'],
  PICKED: ['Siap Dikirim / Diserahkan', 'info'],
  SHIPPED: ['Dikirim via JNE', 'info'],
  IN_TRANSIT: ['Menuju Cabang Tujuan', 'info'],
  READY_PICKUP: ['Siap Diambil Nasabah', 'good'],
  DONE: ['Selesai Diterima', 'neutral'],
}

export const WITHDRAWAL_STATUS: Record<Withdrawal['status'], [string, Tone]> = {
  PROCESSING: ['Sedang Diproses', 'info'],
  SUCCESS: ['Berhasil Ditransfer', 'good'],
  FAILED: ['Gagal Mentransfer', 'critical'],
  UNKNOWN: ['Menunggu Konfirmasi Bank', 'warning'],
}
