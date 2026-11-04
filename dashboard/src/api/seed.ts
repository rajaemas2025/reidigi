import type { DbState, Location, PrintOrderItem, Sku, Token, Unit, User } from './types'

/** Pengguna contoh untuk menguji hak akses. Nanti diganti SSO. */
export const USERS: User[] = [
  { id: 'gudang-a', name: 'Petugas Gudang A', role: 'WAREHOUSE_STAFF' },
  { id: 'gudang-b', name: 'Petugas Gudang B', role: 'WAREHOUSE_STAFF' },
  { id: 'spv', name: 'Supervisor Gudang', role: 'WAREHOUSE_SUPERVISOR' },
  { id: 'finance', name: 'Staf Finance A', role: 'FINANCE' },
  { id: 'finance-b', name: 'Staf Finance B', role: 'FINANCE' },
  { id: 'compliance', name: 'Staf Compliance', role: 'COMPLIANCE' },
  { id: 'cs', name: 'Staf CS', role: 'CS' },
  { id: 'auditor', name: 'Auditor', role: 'AUDITOR' },
]

export const BUYBACK_PER_GRAM = 2_310_000

/** Harga dari halaman publik emasmurniasli.co.id, 3 November 2026. */
const CATALOG: [string, string, number, number, number][] = [
  ['LMRE-0.1G', 'LM Raja Emas', 100, 305_000, 40],
  ['LMRE-0.5G', 'LM Raja Emas', 500, 1_308_500, 30],
  ['LMRE-1G', 'LM Raja Emas', 1_000, 2_513_500, 40],
  ['LMRE-2G', 'LM Raja Emas', 2_000, 5_007_000, 20],
  ['LMRE-5G', 'LM Raja Emas', 5_000, 12_442_500, 16],
  ['LMRE-10G', 'LM Raja Emas', 10_000, 25_135_000, 12],
  ['LMRE-25G', 'LM Raja Emas', 25_000, 61_737_500, 8],
  ['LMRE-50G', 'LM Raja Emas', 50_000, 123_375_000, 6],
  ['LMRE-100G', 'LM Raja Emas', 100_000, 246_550_000, 4],
  ['LMRE-250G', 'LM Raja Emas', 250_000, 614_265_000, 2],
  ['LMRE-500G', 'LM Raja Emas', 500_000, 1_228_320_000, 2],
  ['LMRE-1000G', 'LM Raja Emas', 1_000_000, 2_456_600_000, 1],
  ['KITKAT-20G', 'Kitkat Gold', 20_000, 50_070_000, 3],
  ['LMWD-1G', 'LM Wedding', 1_000, 2_528_500, 0],
]

const LOCATIONS: Location[] = [
  { id: 1, name: 'Gudang Jakarta', type: 'WAREHOUSE' },
  { id: 2, name: 'Cengkareng', type: 'BRANCH' },
  { id: 3, name: 'Kelapa Gading', type: 'BRANCH' },
  { id: 4, name: 'Petojo', type: 'BRANCH' },
  { id: 5, name: 'Mal Ambasador Kuningan', type: 'BRANCH' },
]

const BASE_TIME = Date.UTC(2026, 10, 3, 2, 13)
const hoursAgo = (h: number) => new Date(BASE_TIME - h * 3_600_000).toISOString()

function makeRandom(seed: number) {
  let s = seed
  return () => {
    s = (s + 0x6d2b79f5) | 0
    let t = Math.imul(s ^ (s >>> 15), 1 | s)
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}

const ALPHABET = '0123456789ABCDEFGHJKMNPQRSTVWXYZ'

export function createSeed(): DbState {
  const random = makeRandom(20261103)
  const tokenSuffix = () =>
    Array.from({ length: 11 }, () => ALPHABET[Math.floor(random() * ALPHABET.length)]).join('')

  const skus: Sku[] = CATALOG.map(([code, product, weightMg, buyPrice], i) => ({
    id: i + 1,
    code,
    product,
    weightMg,
    buyPrice,
    buybackPrice: (BUYBACK_PER_GRAM * weightMg) / 1000,
    active: true,
  }))

  const units: Unit[] = []
  const tokens: Token[] = []
  let unitId = 1
  let owner = 1

  CATALOG.forEach(([code, , , , qty], skuIndex) => {
    const skuId = skuIndex + 1
    for (let i = 0; i < qty; i++) {
      const unit: Unit = {
        id: unitId++,
        serial: `${code.replace(/[-.]/g, '')}-${100000 + skuId * 1000 + i}`,
        skuId,
        // Stok digital hanya ada di gudang.
        locationId: 1,
        status: 'AVAILABLE',
      }
      units.push(unit)
      // Kira-kira sepertiga keping sudah dikunci token.
      if (i % 3 === 0 && qty > 1) {
        unit.status = 'ALLOCATED'
        tokens.push({
          code: `RET-${code.replace(/[-.]/g, '')}-${tokenSuffix()}`,
          owner: `Pengguna ${owner++}`,
          unitId: unit.id,
          skuId,
          status: 'ACTIVE',
        })
      }
    }
  })

  const availableOf = (skuId: number) => units.filter((u) => u.skuId === skuId && u.status === 'AVAILABLE')
  availableOf(3)[0].status = 'RESERVED'
  availableOf(1)[0].status = 'INBOUND_QC'
  availableOf(1)[0].status = 'INBOUND_QC'
  availableOf(2)[0].status = 'QUARANTINE'

  const printTokens = tokens.filter((t) => t.skuId === 3).slice(0, 3)
  printTokens.forEach((t) => (t.status = 'PRINT_PENDING'))
  // Dua token pada pesanan kedua milik pengguna yang sama (2 × 1 gr).
  printTokens[2].owner = printTokens[1].owner
  const printItem = (t: Token): PrintOrderItem => ({
    tokenCode: t.code,
    unitId: t.unitId,
    serial: units.find((u) => u.id === t.unitId)!.serial,
    weightMg: skus.find((s) => s.id === t.skuId)!.weightMg,
    picked: false,
    arrived: false,
  })
  const installmentTokens = tokens.filter((t) => t.skuId === 5).slice(0, 3)
  installmentTokens.forEach((t) => (t.status = 'LOCKED_INSTALLMENT'))

  return {
    skus,
    locations: LOCATIONS,
    units,
    tokens,
    counts: [],
    printOrders: [
      { id: 1, owner: printTokens[0].owner, items: [printItem(printTokens[0])], method: 'SHIP', destination: 'JNE ke Jl. Asia Afrika No. 1, Bandung', status: 'WAITING' },
      {
        id: 2,
        owner: printTokens[1].owner,
        items: [printItem(printTokens[1]), printItem(printTokens[2])],
        method: 'PICKUP',
        destination: 'Ambil di cabang Kelapa Gading',
        branchId: 3,
        status: 'WAITING',
      },
    ],
    withdrawals: [
      { id: 1, owner: 'Pengguna 2', bank: 'BCA •••• 1234', amount: 2_310_000, status: 'SUCCESS', createdAt: hoursAgo(5) },
      { id: 2, owner: 'Pengguna 7', bank: 'Mandiri •••• 8890', amount: 11_550_000, status: 'PROCESSING', createdAt: hoursAgo(1) },
      { id: 3, owner: 'Pengguna 11', bank: 'BNI •••• 4421', amount: 4_620_000, status: 'FAILED', createdAt: hoursAgo(3) },
      { id: 4, owner: 'Pengguna 15', bank: 'BRI •••• 7730', amount: 23_100_000, status: 'UNKNOWN', createdAt: hoursAgo(2) },
    ],
    kyc: [
      { id: 1, applicant: 'Pemohon 1', nikMasked: '3173••••••••0012', reason: 'Skor liveness rendah', status: 'PENDING' },
      { id: 2, applicant: 'Pemohon 2', nikMasked: '3275••••••••0044', reason: 'Nama KTP berbeda dengan profil', status: 'PENDING' },
      { id: 3, applicant: 'Pemohon 3', nikMasked: '3171••••••••0098', reason: 'Foto KTP buram', status: 'PENDING' },
    ],
    contracts: installmentTokens.map((t, i) => ({
      id: `MTR-${1001 + i}`,
      partner: 'Mitra Pembiayaan',
      owner: t.owner,
      tokenCode: t.code,
      price: 12_442_500,
      tenorMonths: 12,
      paidMonths: 2 + i * 3,
      status: i === 1 ? 'OVERDUE' : 'ON_TIME',
    })),
    audit: [],
    trading: { halted: false, priceUpdatedAt: new Date(BASE_TIME).toISOString(), source: 'emasmurniasli.co.id' },
    finance: { walletTotal: 482_150_000, ledgerLiability: 482_150_000, customerBankBalance: 500_000_000 },
    seq: 1000,
  }
}
