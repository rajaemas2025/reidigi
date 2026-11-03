import { beforeEach, describe, expect, it } from 'vitest'
import { MockDb } from './core'
import { USERS } from './seed'
import { ApiError } from './types'

const user = (id: string) => USERS.find((u) => u.id === id)!
const staffA = user('gudang-a')
const staffB = user('gudang-b')
const spv = user('spv')
const finance = user('finance')

function expectCode(fn: () => unknown, code: string) {
  try {
    fn()
  } catch (e) {
    expect((e as ApiError).code).toBe(code)
    return
  }
  throw new Error(`Seharusnya gagal dengan ${code}`)
}

describe('data awal', () => {
  it('memenuhi seluruh invarian', () => {
    const db = new MockDb()
    expect(db.checkInvariants().filter((r) => !r.ok)).toEqual([])
  })
})

describe('stock opname', () => {
  let db: MockDb
  const LOCATION = 1
  const present = () => db.listUnits({ locationId: LOCATION }).filter((u) => !['SHIPPED', 'DELIVERED'].includes(u.status))

  beforeEach(() => {
    db = new MockDb()
  })

  it('menyembunyikan jumlah harapan selama menghitung', () => {
    const count = db.createCount(spv, LOCATION)
    expect(count.expectedUnitIds).toEqual([])
    expect(db.state.counts[0].expectedUnitIds.length).toBeGreaterThan(0)
  })

  it('tanpa selisih langsung siap disetujui', () => {
    const count = db.createCount(spv, LOCATION)
    present().forEach((u) => db.scan(staffA, count.id, u.serial))
    expect(db.submitCount(staffA, count.id).status).toBe('AWAITING_APPROVAL')
  })

  it('pindaian ganda tidak dihitung dua kali', () => {
    const count = db.createCount(spv, LOCATION)
    const serial = present()[0].serial
    expect(db.scan(staffA, count.id, serial).duplicate).toBe(false)
    expect(db.scan(staffA, count.id, ` ${serial.toLowerCase()} `).duplicate).toBe(true)
  })

  it('selisih tidak mengubah stok sampai disetujui', () => {
    const count = db.createCount(spv, LOCATION)
    const [missing, ...rest] = present()
    rest.forEach((u) => db.scan(staffA, count.id, u.serial))
    const statusBefore = missing.status
    db.submitCount(staffA, count.id)
    expect(db.listUnits({ q: missing.serial })[0].status).toBe(statusBefore)
  })

  it('hitung ulang wajib oleh orang berbeda', () => {
    const count = db.createCount(spv, LOCATION)
    const [, ...rest] = present()
    rest.forEach((u) => db.scan(staffA, count.id, u.serial))
    const submitted = db.submitCount(staffA, count.id)
    const varianceId = submitted.variances[0].id
    expectCode(() => db.recordRecount(staffA, count.id, varianceId, false), 'SAME_COUNTER')
    expect(db.recordRecount(staffB, count.id, varianceId, false).status).toBe('AWAITING_APPROVAL')
  })

  it('keping bertoken yang hilang: token pindah ke keping lain', () => {
    const tokened = present().find((u) => u.tokenCode && u.status === 'ALLOCATED')!
    const tokenCode = tokened.tokenCode!
    const count = db.createCount(spv, LOCATION)
    present().filter((u) => u.id !== tokened.id).forEach((u) => db.scan(staffA, count.id, u.serial))
    const submitted = db.submitCount(staffA, count.id)
    db.recordRecount(staffB, count.id, submitted.variances[0].id, false)
    db.approveCount(spv, count.id)

    expect(db.listUnits({ q: tokened.serial })[0].status).toBe('QUARANTINE')
    const moved = db.state.tokens.find((t) => t.code === tokenCode)!
    expect(moved.unitId).not.toBe(tokened.id)
    expect(db.state.units.find((u) => u.id === moved.unitId)!.status).toBe('ALLOCATED')
    expect(db.checkInvariants().filter((r) => !r.ok)).toEqual([])
  })

  it('keping yang ditemukan saat hitung ulang tidak berubah', () => {
    const count = db.createCount(spv, LOCATION)
    const [found, ...rest] = present()
    rest.forEach((u) => db.scan(staffA, count.id, u.serial))
    const submitted = db.submitCount(staffA, count.id)
    db.recordRecount(staffB, count.id, submitted.variances[0].id, true)
    db.approveCount(spv, count.id)
    expect(db.listUnits({ q: found.serial })[0].status).toBe(found.status)
  })

  it('keping yang keluar sah selama sesi bukan selisih', () => {
    const count = db.createCount(spv, LOCATION)
    const [sold, ...rest] = present()
    rest.forEach((u) => db.scan(staffA, count.id, u.serial))
    db.state.units.find((u) => u.id === sold.id)!.status = 'SHIPPED'
    expect(db.submitCount(staffA, count.id).variances).toEqual([])
  })

  it('seri asing dan seri lokasi lain tercatat sebagai selisih', () => {
    const other = db.listUnits({ locationId: LOCATION, status: 'AVAILABLE' })[0]
    db.state.units.find((u) => u.id === other.id)!.locationId = 3
    const count = db.createCount(spv, LOCATION)
    present().forEach((u) => db.scan(staffA, count.id, u.serial))
    db.scan(staffA, count.id, 'SERI-ASING-1')
    db.scan(staffA, count.id, other.serial)
    const types = db.submitCount(staffA, count.id).variances.map((v) => v.type).sort()
    expect(types).toEqual(['UNEXPECTED', 'WRONG_LOCATION'])
  })

  it('selisih besar menunggu persetujuan Finance', () => {
    const heavy = db.state.units.find((u) => u.skuId === 9 && u.status === 'AVAILABLE')!
    const count = db.createCount(spv, LOCATION)
    present().filter((u) => u.id !== heavy.id).forEach((u) => db.scan(staffA, count.id, u.serial))
    const submitted = db.submitCount(staffA, count.id)
    db.recordRecount(staffB, count.id, submitted.variances[0].id, false)
    expect(db.approveCount(spv, count.id).status).toBe('AWAITING_FINANCE')
    expect(db.listUnits({ q: heavy.serial })[0].status).toBe('AVAILABLE')
    expectCode(() => db.approveCount(spv, count.id), 'FORBIDDEN')
    expect(db.approveCount(finance, count.id).status).toBe('APPROVED')
    expect(db.listUnits({ q: heavy.serial })[0].status).toBe('QUARANTINE')
  })

  it('satu lokasi hanya boleh punya satu sesi aktif', () => {
    db.createCount(spv, LOCATION)
    expectCode(() => db.createCount(spv, LOCATION), 'COUNT_IN_PROGRESS')
  })
})

describe('hak akses dan pemeriksaan keping', () => {
  let db: MockDb
  beforeEach(() => {
    db = new MockDb()
  })

  it('petugas tidak bisa mengubah SKU atau menyetujui QC', () => {
    expectCode(() => db.updateSku(staffA, 1, { active: false }), 'FORBIDDEN')
    const unit = db.listUnits({ status: 'INBOUND_QC' })[0]
    expectCode(() => db.decideQc(staffA, unit.id, true), 'FORBIDDEN')
  })

  it('penerimaan menolak nomor seri ganda', () => {
    const serial = db.state.units[0].serial
    expectCode(() => db.receiveUnits(staffA, { skuId: 1, locationId: 1, serials: [serial] }), 'DUPLICATE')
  })

  it('keping baru tidak bisa dijual sebelum QC', () => {
    db.receiveUnits(staffA, { skuId: 1, locationId: 1, serials: ['BARU-1'] })
    const unit = db.listUnits({ q: 'BARU-1' })[0]
    expect(unit.status).toBe('INBOUND_QC')
    db.decideQc(spv, unit.id, true)
    expect(db.listUnits({ q: 'BARU-1' })[0].status).toBe('AVAILABLE')
  })

  it('picking cetak wajib memindai seri milik token', () => {
    const order = db.listPrintOrders().find((o) => o.method === 'SHIP')!
    const item = order.items[0]
    expectCode(() => db.pickPrintOrder(staffA, order.id, 'SALAH'), 'SERIAL_MISMATCH')
    db.pickPrintOrder(staffA, order.id, item.serial)
    db.shipPrintOrder(staffA, order.id, 'RESI1')
    expect(db.checkInvariants().filter((r) => !r.ok)).toEqual([])
    db.completePrintOrder(staffA, order.id)
    expect(db.state.tokens.find((t) => t.code === item.tokenCode)!.status).toBe('PRINTED')
    expect(db.checkInvariants().filter((r) => !r.ok)).toEqual([])
  })

  describe('ambil di cabang', () => {
    /** Token aktif milik satu pengguna, dengan berat tertentu. */
    function activeTokens(skuId: number, count: number, owner = 'Pengguna Uji') {
      const tokens = db.state.tokens.filter((t) => t.skuId === skuId && t.status === 'ACTIVE').slice(0, count)
      tokens.forEach((t) => (t.owner = owner))
      return tokens.map((t) => t.code)
    }
    const BRANCH = 3
    const SKU_0_5G = 2
    const SKU_1G = 3
    const SKU_2G = 4

    it('menolak total di bawah 2 gr, dan token tetap aktif', () => {
      const tokenCodes = activeTokens(SKU_1G, 1)
      expectCode(
        () => db.createPrintOrder({ owner: 'Pengguna Uji', tokenCodes, method: 'PICKUP', branchId: BRANCH }),
        'PICKUP_MIN_WEIGHT_NOT_MET',
      )
      expect(db.state.tokens.find((t) => t.code === tokenCodes[0])!.status).toBe('ACTIVE')
    })

    it('menerima tepat 2 gr, satu keping atau gabungan', () => {
      const single = db.createPrintOrder({ owner: 'Pengguna Uji', tokenCodes: activeTokens(SKU_2G, 1), method: 'PICKUP', branchId: BRANCH })
      expect(single.status).toBe('WAITING')
      const combined = db.createPrintOrder({ owner: 'Pengguna Uji', tokenCodes: activeTokens(SKU_1G, 2), method: 'PICKUP', branchId: BRANCH })
      expect(combined.items).toHaveLength(2)
    })

    it('di bawah 2 gr tetap bisa dikirim JNE', () => {
      const order = db.createPrintOrder({ owner: 'Pengguna Uji', tokenCodes: activeTokens(SKU_0_5G, 1), method: 'SHIP', address: 'Surabaya' })
      expect(order.destination).toBe('JNE ke Surabaya')
    })

    it('hanya boleh mengambil token milik sendiri dan cabang yang valid', () => {
      const tokenCodes = activeTokens(SKU_2G, 1)
      expectCode(() => db.createPrintOrder({ owner: 'Orang Lain', tokenCodes, method: 'PICKUP', branchId: BRANCH }), 'TOKEN_NOT_OWNED')
      // Lokasi 1 adalah gudang, bukan cabang.
      expectCode(() => db.createPrintOrder({ owner: 'Pengguna Uji', tokenCodes, method: 'PICKUP', branchId: 1 }), 'INVALID')
    })

    it('alur penuh: gudang, perjalanan, tiba, lalu diambil', () => {
      const order = db.listPrintOrders().find((o) => o.method === 'PICKUP')!
      const id = order.id
      const [a, b] = order.items

      expectCode(() => db.shipPrintOrder(staffA, id, 'INT-1'), 'INVALID_STATE')
      db.pickPrintOrder(staffA, id, a.serial)
      expect(db.printOrderStatus(id)).toBe('WAITING')
      expectCode(() => db.pickPrintOrder(staffA, id, a.serial), 'DUPLICATE')
      db.pickPrintOrder(staffA, id, b.serial)
      expect(db.printOrderStatus(id)).toBe('PICKED')

      db.shipPrintOrder(staffA, id, 'INT-1')
      expect(db.printOrderStatus(id)).toBe('IN_TRANSIT')
      expectCode(() => db.completePickup(staffB, id, '000000', true), 'INVALID_STATE')

      db.receiveAtBranch(staffB, id, a.serial)
      expect(db.printOrderStatus(id)).toBe('IN_TRANSIT')
      db.receiveAtBranch(staffB, id, b.serial)
      expect(db.printOrderStatus(id)).toBe('READY_PICKUP')
      expect(db.listUnits({ q: a.serial })[0]).toMatchObject({ status: 'READY_PICKUP', locationId: order.branchId })
      expect(db.checkInvariants().filter((r) => !r.ok)).toEqual([])

      const code = db.listPrintOrders().find((o) => o.id === id)!.pickupCode!
      expectCode(() => db.completePickup(staffB, id, code, false), 'INVALID')
      expectCode(() => db.completePickup(staffB, id, 'SALAH', true), 'INVALID_PICKUP_CODE')
      db.completePickup(staffB, id, code, true)

      expect(db.printOrderStatus(id)).toBe('DONE')
      expect(db.listUnits({ q: b.serial })[0].status).toBe('DELIVERED')
      expect(db.state.tokens.find((t) => t.code === b.tokenCode)!.status).toBe('PRINTED')
      expect(db.checkInvariants().filter((r) => !r.ok)).toEqual([])
    })

    it('keping dalam perjalanan bukan selisih opname gudang', () => {
      const order = db.listPrintOrders().find((o) => o.method === 'PICKUP')!
      order.items.forEach((i) => db.pickPrintOrder(staffA, order.id, i.serial))
      const origin = db.state.units.find((u) => u.id === order.items[0].unitId)!.locationId
      const count = db.createCount(spv, origin)
      db.shipPrintOrder(staffA, order.id, 'INT-1')
      db.listUnits({ locationId: origin })
        .filter((u) => !['SHIPPED', 'DELIVERED'].includes(u.status))
        .forEach((u) => db.scan(staffA, count.id, u.serial))
      expect(db.submitCount(staffA, count.id).variances).toEqual([])
    })
  })

  it('transaksi hanya bisa dibuka kembali oleh orang berbeda', () => {
    const a = user('treasury-a')
    db.haltTrading(a, 'Harga tidak wajar')
    expectCode(() => db.resumeTrading(a), 'MAKER_CHECKER')
    db.resumeTrading(user('treasury-b'))
    expect(db.getTrading().halted).toBe(false)
  })

  it('penarikan berstatus tidak jelas tidak bisa dicoba ulang', () => {
    const unknown = db.listWithdrawals().find((w) => w.status === 'UNKNOWN')!
    expectCode(() => db.retryWithdrawal(finance, unknown.id), 'INVALID_STATE')
  })

  it('rekonsiliasi mendeteksi data yang dirusak', () => {
    db.state.units.find((u) => u.status === 'ALLOCATED' && db.state.tokens.some((t) => t.unitId === u.id && t.status === 'ACTIVE'))!.status = 'AVAILABLE'
    db.state.finance.walletTotal += 1
    const failed = db.checkInvariants().filter((r) => !r.ok).map((r) => r.id)
    expect(failed).toEqual(expect.arrayContaining(['INV-3', 'INV-4', 'INV-10']))
  })
})
