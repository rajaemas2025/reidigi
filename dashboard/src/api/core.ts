import { createSeed } from './seed'
import type {
  AuditEntry,
  DbState,
  InvariantResult,
  PrintOrder,
  Role,
  Sku,
  SkuRow,
  StockCount,
  Token,
  TokenStatus,
  Unit,
  UnitRow,
  UnitStatus,
  User,
  Variance,
} from './types'
import { ApiError } from './types'

const CLOSED_TOKENS: TokenStatus[] = ['RETURNED', 'PRINTED', 'CANCELLED']
const PHYSICALLY_PRESENT: UnitStatus[] = ['AVAILABLE', 'RESERVED', 'ALLOCATED', 'PICKING', 'READY_PICKUP', 'QUARANTINE', 'INBOUND_QC']
const LOCKED_UNIT: UnitStatus[] = ['ALLOCATED', 'PICKING', 'SHIPPED', 'READY_PICKUP', 'QUARANTINE']
/** Selisih hilang seberat ini atau lebih memerlukan persetujuan Finance. */
const FINANCE_APPROVAL_MG = 100_000
/** Total berat minimal per pesanan untuk diambil di cabang. */
export const PRINT_PICKUP_MIN_WEIGHT_MG = 2_000
const PICKUP_DEADLINE_DAYS = 14
/** Kode tetap selama belum ada aplikasi yang menerimanya. */
const PICKUP_CODE = '246810'

const isOpen = (t: Token) => !CLOSED_TOKENS.includes(t.status)

function require(actor: User, ...roles: Role[]) {
  if (!roles.includes(actor.role)) {
    throw new ApiError('FORBIDDEN', 'Peran Anda tidak berwenang melakukan aksi ini.')
  }
}

/**
 * Backend tiruan di dalam browser. Aturannya mengikuti docs/02-spesifikasi-backend.md
 * supaya dashboard bisa diganti ke API sungguhan tanpa mengubah halaman.
 */
export class MockDb {
  state: DbState

  constructor(state: DbState = createSeed()) {
    this.state = state
  }

  private now() {
    return new Date().toISOString()
  }

  private nextId() {
    return ++this.state.seq
  }

  private log(actor: User, action: string, entity: string, detail: string) {
    const entry: AuditEntry = { id: this.nextId(), at: this.now(), actor: actor.name, action, entity, detail }
    this.state.audit.unshift(entry)
  }

  private sku(id: number) {
    const sku = this.state.skus.find((s) => s.id === id)
    if (!sku) throw new ApiError('NOT_FOUND', 'SKU tidak ditemukan.')
    return sku
  }

  private unit(id: number) {
    const unit = this.state.units.find((u) => u.id === id)
    if (!unit) throw new ApiError('NOT_FOUND', 'Keping tidak ditemukan.')
    return unit
  }

  private openToken(unitId: number) {
    return this.state.tokens.find((t) => t.unitId === unitId && isOpen(t))
  }

  private count(id: number) {
    const count = this.state.counts.find((c) => c.id === id)
    if (!count) throw new ApiError('NOT_FOUND', 'Sesi opname tidak ditemukan.')
    return count
  }

  // ----- SKU -----

  listSkus(): SkuRow[] {
    return this.state.skus.map((sku) => {
      const units = this.state.units.filter((u) => u.skuId === sku.id)
      const available = units.filter((u) => u.status === 'AVAILABLE').length
      const locked = units.filter((u) => this.openToken(u.id)).length
      const inStock = units.filter((u) => PHYSICALLY_PRESENT.includes(u.status)).length
      return { ...sku, available, locked, other: inStock - available - locked }
    })
  }

  createSku(actor: User, input: Pick<Sku, 'code' | 'product' | 'weightMg' | 'buyPrice' | 'buybackPrice'>) {
    require(actor, 'WAREHOUSE_SUPERVISOR')
    const code = input.code.trim().toUpperCase()
    if (!code) throw new ApiError('INVALID', 'Kode SKU wajib diisi.')
    if (this.state.skus.some((s) => s.code === code)) throw new ApiError('DUPLICATE', 'Kode SKU sudah dipakai.')
    if (input.weightMg <= 0) throw new ApiError('INVALID', 'Berat harus lebih dari 0.')
    if (input.buybackPrice >= input.buyPrice) throw new ApiError('INVALID', 'Harga buyback harus lebih rendah dari harga beli.')
    const sku: Sku = { ...input, code, id: this.nextId(), active: true }
    this.state.skus.push(sku)
    this.log(actor, 'SKU_CREATED', code, `${input.product}, ${input.weightMg / 1000} gr`)
    return sku
  }

  updateSku(actor: User, id: number, patch: Partial<Pick<Sku, 'active'>>) {
    require(actor, 'WAREHOUSE_SUPERVISOR')
    const sku = this.sku(id)
    Object.assign(sku, patch)
    this.log(actor, 'SKU_UPDATED', sku.code, JSON.stringify(patch))
    return sku
  }

  // ----- Keping -----

  listLocations() {
    return this.state.locations
  }

  listUnits(filter: { skuId?: number; locationId?: number; status?: UnitStatus; q?: string } = {}): UnitRow[] {
    const q = filter.q?.trim().toUpperCase()
    return this.state.units
      .filter((u) => !filter.skuId || u.skuId === filter.skuId)
      .filter((u) => !filter.locationId || u.locationId === filter.locationId)
      .filter((u) => !filter.status || u.status === filter.status)
      .map((u) => {
        const sku = this.sku(u.skuId)
        return {
          ...u,
          skuCode: sku.code,
          product: sku.product,
          weightMg: sku.weightMg,
          locationName: this.state.locations.find((l) => l.id === u.locationId)?.name ?? '-',
          tokenCode: this.openToken(u.id)?.code,
        }
      })
      .filter((u) => !q || u.serial.includes(q) || u.tokenCode?.includes(q))
  }

  receiveUnits(actor: User, input: { skuId: number; locationId: number; serials: string[] }) {
    require(actor, 'WAREHOUSE_STAFF', 'WAREHOUSE_SUPERVISOR')
    const serials = [...new Set(input.serials.map((s) => s.trim().toUpperCase()).filter(Boolean))]
    if (!serials.length) throw new ApiError('INVALID', 'Masukkan minimal satu nomor seri.')
    const taken = serials.filter((s) => this.state.units.some((u) => u.serial === s))
    if (taken.length) throw new ApiError('DUPLICATE', `Nomor seri sudah terdaftar: ${taken.join(', ')}`)
    const sku = this.sku(input.skuId)
    for (const serial of serials) {
      this.state.units.push({ id: this.nextId(), serial, skuId: sku.id, locationId: input.locationId, status: 'INBOUND_QC' })
    }
    this.log(actor, 'UNITS_RECEIVED', sku.code, `${serials.length} keping masuk QC`)
    return serials.length
  }

  decideQc(actor: User, unitId: number, pass: boolean) {
    require(actor, 'WAREHOUSE_SUPERVISOR')
    const unit = this.unit(unitId)
    if (unit.status !== 'INBOUND_QC') throw new ApiError('INVALID_STATE', 'Keping tidak sedang menunggu QC.')
    unit.status = pass ? 'AVAILABLE' : 'QUARANTINE'
    this.log(actor, pass ? 'QC_PASSED' : 'QC_FAILED', unit.serial, '')
  }

  quarantine(actor: User, unitId: number, reason: string) {
    require(actor, 'WAREHOUSE_SUPERVISOR')
    if (!reason.trim()) throw new ApiError('INVALID', 'Alasan wajib diisi.')
    const unit = this.unit(unitId)
    if (!['AVAILABLE', 'ALLOCATED'].includes(unit.status)) {
      throw new ApiError('INVALID_STATE', 'Keping dengan status ini tidak bisa dikarantina.')
    }
    const replacement = this.quarantineUnit(actor, unit, reason)
    return { replacementSerial: replacement?.serial }
  }

  /** Pengguna tidak boleh dirugikan masalah gudang: token pindah ke keping lain bila ada. */
  private quarantineUnit(actor: User, unit: Unit, reason: string) {
    unit.status = 'QUARANTINE'
    this.log(actor, 'UNIT_QUARANTINED', unit.serial, reason)
    const token = this.openToken(unit.id)
    if (!token) return undefined
    const replacement = this.state.units
      .filter((u) => u.skuId === unit.skuId && u.status === 'AVAILABLE' && u.id !== unit.id)
      .sort((a, b) => a.locationId - b.locationId)[0]
    if (!replacement) {
      this.log(actor, 'TOKEN_SWAP_FAILED', token.code, `Tidak ada keping pengganti untuk ${unit.serial}`)
      return undefined
    }
    token.unitId = replacement.id
    replacement.status = 'ALLOCATED'
    this.log(actor, 'TOKEN_SWAPPED', token.code, `${unit.serial} → ${replacement.serial}`)
    return replacement
  }

  releaseQuarantine(actor: User, unitId: number) {
    require(actor, 'WAREHOUSE_SUPERVISOR')
    const unit = this.unit(unitId)
    if (unit.status !== 'QUARANTINE') throw new ApiError('INVALID_STATE', 'Keping tidak sedang dikarantina.')
    unit.status = this.openToken(unit.id) ? 'ALLOCATED' : 'AVAILABLE'
    this.log(actor, 'UNIT_RELEASED', unit.serial, '')
  }

  // ----- Stock opname -----

  listCounts() {
    return this.state.counts.map((c) => this.viewCount(c))
  }

  getCount(id: number) {
    return this.viewCount(this.count(id))
  }

  /** Hitung buta: jumlah yang diharapkan sistem disembunyikan sampai hitungan dikirim. */
  private viewCount(count: StockCount): StockCount & { locationName: string } {
    return {
      ...count,
      expectedUnitIds: count.status === 'COUNTING' ? [] : count.expectedUnitIds,
      locationName: this.state.locations.find((l) => l.id === count.locationId)?.name ?? '-',
    }
  }

  createCount(actor: User, locationId: number) {
    require(actor, 'WAREHOUSE_SUPERVISOR')
    if (this.state.counts.some((c) => c.locationId === locationId && c.status !== 'APPROVED')) {
      throw new ApiError('COUNT_IN_PROGRESS', 'Lokasi ini masih punya sesi opname yang belum selesai.')
    }
    const expectedUnitIds = this.state.units
      .filter((u) => u.locationId === locationId && PHYSICALLY_PRESENT.includes(u.status))
      .map((u) => u.id)
    const count: StockCount = {
      id: this.nextId(),
      locationId,
      createdBy: actor.name,
      createdAt: this.now(),
      status: 'COUNTING',
      expectedUnitIds,
      scans: [],
      variances: [],
    }
    this.state.counts.unshift(count)
    this.log(actor, 'COUNT_CREATED', `Opname #${count.id}`, `Lokasi ${locationId}`)
    return this.viewCount(count)
  }

  scan(actor: User, id: number, rawSerial: string) {
    require(actor, 'WAREHOUSE_STAFF', 'WAREHOUSE_SUPERVISOR')
    const count = this.count(id)
    if (count.status !== 'COUNTING') throw new ApiError('INVALID_STATE', 'Sesi ini tidak lagi menerima pindaian.')
    const serial = rawSerial.trim().toUpperCase()
    if (!serial) throw new ApiError('INVALID', 'Nomor seri kosong.')
    if (count.scans.some((s) => s.serial === serial)) {
      return { duplicate: true, total: count.scans.length }
    }
    count.scans.push({ serial, by: actor.name, at: this.now() })
    return { duplicate: false, total: count.scans.length }
  }

  submitCount(actor: User, id: number) {
    require(actor, 'WAREHOUSE_STAFF', 'WAREHOUSE_SUPERVISOR')
    const count = this.count(id)
    if (count.status !== 'COUNTING') throw new ApiError('INVALID_STATE', 'Sesi ini sudah dikirim.')
    if (!count.scans.length) throw new ApiError('INVALID', 'Belum ada pindaian.')

    const scanned = new Set(count.scans.map((s) => s.serial))
    const variances: Variance[] = []
    for (const unitId of count.expectedUnitIds) {
      const unit = this.unit(unitId)
      // Keping yang keluar secara sah selama sesi bukan selisih.
      const movedAway = unit.locationId !== count.locationId || !PHYSICALLY_PRESENT.includes(unit.status)
      if (!movedAway && !scanned.has(unit.serial)) {
        variances.push({ id: this.nextId(), type: 'MISSING', serial: unit.serial, unitId: unit.id, recountFound: null })
      }
    }
    for (const serial of scanned) {
      const unit = this.state.units.find((u) => u.serial === serial)
      if (!unit) {
        variances.push({ id: this.nextId(), type: 'UNEXPECTED', serial, recountFound: null })
      } else if (unit.locationId !== count.locationId || !PHYSICALLY_PRESENT.includes(unit.status)) {
        const leftLegitimately = count.expectedUnitIds.includes(unit.id)
        if (!leftLegitimately) {
          variances.push({ id: this.nextId(), type: 'WRONG_LOCATION', serial, unitId: unit.id, recountFound: null })
        }
      }
    }
    count.variances = variances
    count.status = variances.length ? 'RECOUNT' : 'AWAITING_APPROVAL'
    this.log(actor, 'COUNT_SUBMITTED', `Opname #${id}`, `${count.scans.length} pindaian, ${variances.length} selisih`)
    return this.viewCount(count)
  }

  recordRecount(actor: User, id: number, varianceId: number, found: boolean) {
    require(actor, 'WAREHOUSE_STAFF', 'WAREHOUSE_SUPERVISOR')
    const count = this.count(id)
    if (count.status !== 'RECOUNT') throw new ApiError('INVALID_STATE', 'Sesi ini tidak sedang hitung ulang.')
    if (count.scans.some((s) => s.by === actor.name)) {
      throw new ApiError('SAME_COUNTER', 'Hitung ulang harus dilakukan orang yang berbeda dari penghitung pertama.')
    }
    const variance = count.variances.find((v) => v.id === varianceId)
    if (!variance) throw new ApiError('NOT_FOUND', 'Selisih tidak ditemukan.')
    variance.recountFound = found
    variance.recountBy = actor.name
    this.log(actor, 'COUNT_RECOUNTED', variance.serial, found ? 'Ditemukan' : 'Tidak ditemukan')
    if (count.variances.every((v) => v.recountFound !== null)) count.status = 'AWAITING_APPROVAL'
    return this.viewCount(count)
  }

  private missingWeight(count: StockCount) {
    return count.variances
      .filter((v) => v.type === 'MISSING' && v.recountFound === false && v.unitId)
      .reduce((sum, v) => sum + this.sku(this.unit(v.unitId!).skuId).weightMg, 0)
  }

  /** Stok hanya berubah di sini, lewat penyesuaian yang disetujui. */
  approveCount(actor: User, id: number) {
    const count = this.count(id)
    if (count.status === 'AWAITING_APPROVAL') {
      require(actor, 'WAREHOUSE_SUPERVISOR')
      if (this.missingWeight(count) >= FINANCE_APPROVAL_MG) {
        count.status = 'AWAITING_FINANCE'
        this.log(actor, 'COUNT_ESCALATED', `Opname #${id}`, 'Selisih besar, menunggu Finance')
        return this.viewCount(count)
      }
    } else if (count.status === 'AWAITING_FINANCE') {
      require(actor, 'FINANCE')
    } else {
      throw new ApiError('INVALID_STATE', 'Sesi ini belum siap disetujui.')
    }

    for (const v of count.variances) {
      if (v.type === 'MISSING' && v.recountFound === false) {
        this.quarantineUnit(actor, this.unit(v.unitId!), `Hilang saat opname #${id}`)
      }
      if (v.type === 'WRONG_LOCATION' && v.recountFound) {
        this.unit(v.unitId!).locationId = count.locationId
        this.log(actor, 'UNIT_RELOCATED', v.serial, `Dikoreksi oleh opname #${id}`)
      }
      if (v.type === 'UNEXPECTED' && v.recountFound) {
        this.log(actor, 'UNEXPECTED_UNIT', v.serial, 'Perlu didaftarkan lewat penerimaan barang')
      }
    }
    count.status = 'APPROVED'
    count.approvedBy = actor.name
    this.log(actor, 'COUNT_APPROVED', `Opname #${id}`, `${count.variances.length} selisih diproses`)
    return this.viewCount(count)
  }

  // ----- Pemenuhan cetak -----

  listPrintOrders() {
    return this.state.printOrders
  }

  printOrderStatus(id: number) {
    return this.printOrder(id).status
  }

  private printOrder(id: number) {
    const order = this.state.printOrders.find((o) => o.id === id)
    if (!order) throw new ApiError('NOT_FOUND', 'Pesanan cetak tidak ditemukan.')
    return order
  }

  /** Dipanggil aplikasi mobile saat pengguna memilih cetak. */
  createPrintOrder(input: { owner: string; tokenCodes: string[]; method: 'SHIP' | 'PICKUP'; address?: string; branchId?: number }) {
    const tokens = input.tokenCodes.map((code) => {
      const token = this.state.tokens.find((t) => t.code === code)
      if (!token) throw new ApiError('NOT_FOUND', 'Token tidak ditemukan.')
      if (token.owner !== input.owner) throw new ApiError('TOKEN_NOT_OWNED', 'Token bukan milik pengguna ini.')
      if (token.status !== 'ACTIVE') throw new ApiError('TOKEN_LOCKED', 'Token tidak bisa dicetak saat ini.')
      return token
    })
    if (!tokens.length) throw new ApiError('INVALID', 'Pilih minimal satu token.')
    const items = tokens.map((t) => ({
      tokenCode: t.code,
      unitId: t.unitId,
      serial: this.unit(t.unitId).serial,
      weightMg: this.sku(t.skuId).weightMg,
      picked: false,
      arrived: false,
    }))

    let destination: string
    if (input.method === 'PICKUP') {
      const totalMg = items.reduce((sum, item) => sum + item.weightMg, 0)
      if (totalMg < PRINT_PICKUP_MIN_WEIGHT_MG) {
        throw new ApiError('PICKUP_MIN_WEIGHT_NOT_MET', 'Ambil di cabang memerlukan total minimal 2 gr. Pilih pengiriman JNE atau tambah token.')
      }
      const branch = this.state.locations.find((l) => l.id === input.branchId && l.type === 'BRANCH')
      if (!branch) throw new ApiError('INVALID', 'Pilih cabang pengambilan.')
      destination = `Ambil di cabang ${branch.name}`
    } else {
      if (!input.address?.trim()) throw new ApiError('INVALID', 'Alamat pengiriman wajib diisi.')
      destination = `JNE ke ${input.address.trim()}`
    }

    tokens.forEach((t) => (t.status = 'PRINT_PENDING'))
    const order: PrintOrder = {
      id: this.nextId(),
      owner: input.owner,
      items,
      method: input.method,
      destination,
      branchId: input.branchId,
      status: 'WAITING',
    }
    this.state.printOrders.unshift(order)
    this.log({ id: 'app', name: 'Aplikasi mobile', role: 'CS' }, 'PRINT_REQUESTED', `Cetak #${order.id}`, destination)
    return order
  }

  pickPrintOrder(actor: User, id: number, scannedSerial: string) {
    require(actor, 'WAREHOUSE_STAFF', 'WAREHOUSE_SUPERVISOR')
    const order = this.printOrder(id)
    if (order.status !== 'WAITING') throw new ApiError('INVALID_STATE', 'Pesanan ini sudah diproses.')
    const serial = scannedSerial.trim().toUpperCase()
    const item = order.items.find((i) => i.serial === serial)
    if (!item) throw new ApiError('SERIAL_MISMATCH', 'Nomor seri tidak cocok dengan keping milik token.')
    if (item.picked) throw new ApiError('DUPLICATE', 'Keping ini sudah dipindai.')
    item.picked = true
    this.unit(item.unitId).status = 'PICKING'
    if (order.items.every((i) => i.picked)) order.status = 'PICKED'
    this.log(actor, 'PRINT_PICKED', item.serial, item.tokenCode)
    return order
  }

  /** JNE untuk pengiriman ke pengguna, atau pengiriman internal gudang ke cabang. */
  shipPrintOrder(actor: User, id: number, trackingNumber: string) {
    require(actor, 'WAREHOUSE_STAFF', 'WAREHOUSE_SUPERVISOR')
    const order = this.printOrder(id)
    if (order.status !== 'PICKED') throw new ApiError('INVALID_STATE', 'Pesanan belum bisa dikirim.')
    if (!trackingNumber.trim()) throw new ApiError('INVALID', 'Nomor pengiriman wajib diisi.')
    order.items.forEach((i) => (this.unit(i.unitId).status = 'SHIPPED'))
    order.status = order.method === 'PICKUP' ? 'IN_TRANSIT' : 'SHIPPED'
    order.trackingNumber = trackingNumber.trim()
    this.log(actor, 'PRINT_SHIPPED', `Cetak #${id}`, order.trackingNumber)
  }

  /** Petugas cabang memindai setiap keping yang tiba dari gudang. */
  receiveAtBranch(actor: User, id: number, scannedSerial: string) {
    require(actor, 'WAREHOUSE_STAFF', 'WAREHOUSE_SUPERVISOR')
    const order = this.printOrder(id)
    if (order.status !== 'IN_TRANSIT') throw new ApiError('INVALID_STATE', 'Pesanan tidak sedang dikirim ke cabang.')
    const serial = scannedSerial.trim().toUpperCase()
    const item = order.items.find((i) => i.serial === serial)
    if (!item) throw new ApiError('SERIAL_MISMATCH', 'Nomor seri tidak termasuk pesanan ini.')
    if (item.arrived) throw new ApiError('DUPLICATE', 'Keping ini sudah dipindai.')
    const unit = this.unit(item.unitId)
    item.arrived = true
    unit.status = 'READY_PICKUP'
    unit.locationId = order.branchId!
    if (order.items.every((i) => i.arrived)) {
      order.status = 'READY_PICKUP'
      order.pickupCode = PICKUP_CODE
      order.pickupDeadline = new Date(Date.now() + PICKUP_DEADLINE_DAYS * 86_400_000).toISOString()
    }
    this.log(actor, 'PRINT_ARRIVED_AT_BRANCH', item.serial, `Cetak #${id}`)
    return order
  }

  private finishPrintOrder(actor: User, order: PrintOrder) {
    for (const item of order.items) {
      this.state.tokens.find((t) => t.code === item.tokenCode)!.status = 'PRINTED'
      this.unit(item.unitId).status = 'DELIVERED'
    }
    order.status = 'DONE'
    this.log(actor, 'PRINT_DONE', `Cetak #${order.id}`, order.items.map((i) => i.serial).join(', '))
  }

  /** Pengiriman JNE sudah diterima pengguna. */
  completePrintOrder(actor: User, id: number) {
    require(actor, 'WAREHOUSE_STAFF', 'WAREHOUSE_SUPERVISOR')
    const order = this.printOrder(id)
    if (order.status !== 'SHIPPED') throw new ApiError('INVALID_STATE', 'Pesanan belum bisa diselesaikan.')
    this.finishPrintOrder(actor, order)
  }

  completePickup(actor: User, id: number, code: string, identityChecked: boolean) {
    require(actor, 'WAREHOUSE_STAFF', 'WAREHOUSE_SUPERVISOR')
    const order = this.printOrder(id)
    if (order.status !== 'READY_PICKUP') throw new ApiError('INVALID_STATE', 'Pesanan belum siap diambil.')
    if (!identityChecked) throw new ApiError('INVALID', 'Periksa KTP pengguna terlebih dahulu.')
    if (code.trim() !== order.pickupCode) throw new ApiError('INVALID_PICKUP_CODE', 'Kode pengambilan salah.')
    this.finishPrintOrder(actor, order)
  }

  // ----- Harga -----

  getTrading() {
    return this.state.trading
  }

  haltTrading(actor: User, reason: string) {
    require(actor, 'TREASURY')
    if (!reason.trim()) throw new ApiError('INVALID', 'Alasan wajib diisi.')
    this.state.trading = { ...this.state.trading, halted: true, haltedBy: actor.name, reason: reason.trim() }
    this.log(actor, 'TRADING_HALTED', 'Transaksi', reason.trim())
  }

  resumeTrading(actor: User) {
    require(actor, 'TREASURY')
    if (this.state.trading.haltedBy === actor.name) {
      throw new ApiError('MAKER_CHECKER', 'Transaksi harus dibuka kembali oleh orang yang berbeda.')
    }
    this.state.trading = { ...this.state.trading, halted: false, haltedBy: undefined, reason: undefined }
    this.log(actor, 'TRADING_RESUMED', 'Transaksi', '')
  }

  // ----- Keuangan -----

  listWithdrawals() {
    return this.state.withdrawals
  }

  getFinance() {
    return this.state.finance
  }

  retryWithdrawal(actor: User, id: number) {
    require(actor, 'FINANCE')
    const withdrawal = this.state.withdrawals.find((w) => w.id === id)
    if (!withdrawal) throw new ApiError('NOT_FOUND', 'Penarikan tidak ditemukan.')
    // Status UNKNOWN tidak boleh dikirim ulang: uang bisa jadi sudah keluar.
    if (withdrawal.status !== 'FAILED') {
      throw new ApiError('INVALID_STATE', 'Hanya penarikan yang gagal final yang bisa dicoba ulang.')
    }
    withdrawal.status = 'PROCESSING'
    this.log(actor, 'WITHDRAWAL_RETRIED', `Penarikan #${id}`, '')
  }

  checkInvariants(): InvariantResult[] {
    const { units, tokens, skus, finance } = this.state
    const open = tokens.filter(isOpen)
    const seen = new Set<number>()
    const doubled = open.filter((t) => seen.has(t.unitId) || !seen.add(t.unitId))
    const availableWithToken = units.filter((u) => u.status === 'AVAILABLE' && open.some((t) => t.unitId === u.id))
    const skuMismatch = skus.filter((sku) => {
      const openCount = open.filter((t) => t.skuId === sku.id).length
      const lockedUnits = units.filter(
        (u) => u.skuId === sku.id && LOCKED_UNIT.includes(u.status) && open.some((t) => t.unitId === u.id),
      ).length
      return openCount !== lockedUnits
    })
    const walletGap = finance.walletTotal - finance.ledgerLiability
    return [
      { id: 'INV-1', label: 'Satu keping paling banyak satu token aktif', ok: !doubled.length, detail: `${doubled.length} pelanggaran` },
      { id: 'INV-3', label: 'Keping tersedia tidak punya token aktif', ok: !availableWithToken.length, detail: `${availableWithToken.length} pelanggaran` },
      { id: 'INV-4', label: 'Jumlah token aktif sama dengan keping terkunci per SKU', ok: !skuMismatch.length, detail: `${skuMismatch.length} SKU tidak cocok` },
      { id: 'INV-10', label: 'Total saldo pengguna sama dengan kewajiban di ledger', ok: walletGap === 0, detail: `Selisih ${walletGap}` },
      {
        id: 'INV-10B',
        label: 'Saldo pengguna tertutup rekening dana pengguna',
        ok: finance.customerBankBalance >= finance.walletTotal,
        detail: `Kelebihan ${finance.customerBankBalance - finance.walletTotal}`,
      },
    ]
  }

  // ----- KYC, cicilan, audit -----

  listKyc() {
    return this.state.kyc
  }

  decideKyc(actor: User, id: number, approve: boolean, note: string) {
    require(actor, 'COMPLIANCE')
    const item = this.state.kyc.find((k) => k.id === id)
    if (!item || item.status !== 'PENDING') throw new ApiError('INVALID_STATE', 'Kasus ini tidak aktif.')
    if (!approve && !note.trim()) throw new ApiError('INVALID', 'Alasan penolakan wajib diisi.')
    item.status = approve ? 'APPROVED' : 'REJECTED'
    item.decisionNote = note.trim() || undefined
    this.log(actor, approve ? 'KYC_APPROVED' : 'KYC_REJECTED', `KYC #${id}`, note.trim())
  }

  listContracts() {
    return this.state.contracts
  }

  listAudit() {
    return this.state.audit
  }

  listOpenAlerts() {
    return {
      waitingPrint: this.state.printOrders.filter((o) => o.status === 'WAITING').length,
      pendingKyc: this.state.kyc.filter((k) => k.status === 'PENDING').length,
      problemWithdrawals: this.state.withdrawals.filter((w) => w.status === 'FAILED' || w.status === 'UNKNOWN').length,
    }
  }
}
