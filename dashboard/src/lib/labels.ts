import type { Tone } from '../components/ui'
import type { CountStatus, PrintOrder, Role, UnitStatus, VarianceType, Withdrawal } from '../api/types'

export const ROLE_LABEL: Record<Role, string> = {
  WAREHOUSE_STAFF: 'Petugas Gudang',
  WAREHOUSE_SUPERVISOR: 'Supervisor Gudang',
  TREASURY: 'Treasury',
  FINANCE: 'Finance',
  COMPLIANCE: 'Compliance',
  CS: 'Customer Service',
  AUDITOR: 'Auditor',
}

export const UNIT_STATUS: Record<UnitStatus, [string, Tone]> = {
  INBOUND_QC: ['Menunggu QC', 'info'],
  AVAILABLE: ['Tersedia', 'good'],
  RESERVED: ['Dipesan', 'info'],
  ALLOCATED: ['Terkunci token', 'neutral'],
  PICKING: ['Picking', 'info'],
  SHIPPED: ['Dikirim', 'info'],
  READY_PICKUP: ['Siap diambil', 'info'],
  DELIVERED: ['Diterima pengguna', 'neutral'],
  QUARANTINE: ['Karantina', 'critical'],
  WRITTEN_OFF: ['Dihapusbukukan', 'critical'],
}

export const COUNT_STATUS: Record<CountStatus, [string, Tone]> = {
  COUNTING: ['Menghitung', 'info'],
  RECOUNT: ['Hitung ulang', 'warning'],
  AWAITING_APPROVAL: ['Menunggu supervisor', 'warning'],
  AWAITING_FINANCE: ['Menunggu Finance', 'warning'],
  APPROVED: ['Disetujui', 'good'],
}

export const VARIANCE: Record<VarianceType, string> = {
  MISSING: 'Tidak ditemukan',
  UNEXPECTED: 'Seri tidak dikenal',
  WRONG_LOCATION: 'Salah lokasi',
}

export const PRINT_STATUS: Record<PrintOrder['status'], [string, Tone]> = {
  WAITING: ['Menunggu picking', 'warning'],
  PICKED: ['Siap dikirim', 'info'],
  SHIPPED: ['Dikirim JNE', 'info'],
  IN_TRANSIT: ['Dalam perjalanan ke cabang', 'info'],
  READY_PICKUP: ['Siap diambil', 'good'],
  DONE: ['Selesai', 'neutral'],
}

export const WITHDRAWAL_STATUS: Record<Withdrawal['status'], [string, Tone]> = {
  PROCESSING: ['Diproses', 'info'],
  SUCCESS: ['Berhasil', 'good'],
  FAILED: ['Gagal', 'critical'],
  UNKNOWN: ['Status tidak jelas', 'warning'],
}
