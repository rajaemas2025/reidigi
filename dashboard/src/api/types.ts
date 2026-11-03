export type Role =
  | 'WAREHOUSE_STAFF'
  | 'WAREHOUSE_SUPERVISOR'
  | 'TREASURY'
  | 'FINANCE'
  | 'COMPLIANCE'
  | 'CS'
  | 'AUDITOR'

export interface User {
  id: string
  name: string
  role: Role
}

export type UnitStatus =
  | 'INBOUND_QC'
  | 'AVAILABLE'
  | 'RESERVED'
  | 'ALLOCATED'
  | 'PICKING'
  | 'SHIPPED'
  | 'READY_PICKUP'
  | 'DELIVERED'
  | 'QUARANTINE'
  | 'WRITTEN_OFF'

export type TokenStatus =
  | 'ACTIVE'
  | 'LOCKED_INSTALLMENT'
  | 'PRINT_PENDING'
  | 'RETURNED'
  | 'PRINTED'
  | 'CANCELLED'

export interface Sku {
  id: number
  code: string
  product: string
  weightMg: number
  active: boolean
  buyPrice: number
  buybackPrice: number
}

export interface Location {
  id: number
  name: string
  type: 'WAREHOUSE' | 'BRANCH'
}

export interface Unit {
  id: number
  serial: string
  skuId: number
  locationId: number
  status: UnitStatus
}

export interface Token {
  code: string
  owner: string
  unitId: number
  skuId: number
  status: TokenStatus
}

export interface UnitRow extends Unit {
  skuCode: string
  product: string
  weightMg: number
  locationName: string
  tokenCode?: string
}

export interface SkuRow extends Sku {
  available: number
  locked: number
  other: number
}

export type VarianceType = 'MISSING' | 'UNEXPECTED' | 'WRONG_LOCATION'

export interface Variance {
  id: number
  type: VarianceType
  serial: string
  unitId?: number
  /** null sampai hitung ulang selesai. */
  recountFound: boolean | null
  recountBy?: string
}

export type CountStatus = 'COUNTING' | 'RECOUNT' | 'AWAITING_APPROVAL' | 'AWAITING_FINANCE' | 'APPROVED'

export interface StockCount {
  id: number
  locationId: number
  createdBy: string
  createdAt: string
  status: CountStatus
  /** Tidak boleh dikirim ke petugas selama status COUNTING (hitung buta). */
  expectedUnitIds: number[]
  scans: { serial: string; by: string; at: string }[]
  variances: Variance[]
  approvedBy?: string
}

export interface PrintOrderItem {
  tokenCode: string
  unitId: number
  serial: string
  weightMg: number
  picked: boolean
  arrived: boolean
}

export interface PrintOrder {
  id: number
  owner: string
  items: PrintOrderItem[]
  method: 'SHIP' | 'PICKUP'
  destination: string
  branchId?: number
  status: 'WAITING' | 'PICKED' | 'SHIPPED' | 'IN_TRANSIT' | 'READY_PICKUP' | 'DONE'
  trackingNumber?: string
  pickupCode?: string
  pickupDeadline?: string
}

export interface Withdrawal {
  id: number
  owner: string
  bank: string
  amount: number
  status: 'PROCESSING' | 'SUCCESS' | 'FAILED' | 'UNKNOWN'
  createdAt: string
}

export interface KycCase {
  id: number
  applicant: string
  nikMasked: string
  reason: string
  status: 'PENDING' | 'APPROVED' | 'REJECTED'
  decisionNote?: string
}

export interface FinancingContract {
  id: string
  partner: string
  owner: string
  tokenCode: string
  price: number
  tenorMonths: number
  paidMonths: number
  status: 'ON_TIME' | 'OVERDUE' | 'PAID_OFF'
}

export interface AuditEntry {
  id: number
  at: string
  actor: string
  action: string
  entity: string
  detail: string
}

export interface TradingState {
  halted: boolean
  haltedBy?: string
  reason?: string
  priceUpdatedAt: string
  source: string
}

export interface FinanceTotals {
  walletTotal: number
  ledgerLiability: number
  customerBankBalance: number
}

export interface InvariantResult {
  id: string
  label: string
  ok: boolean
  detail: string
}

export interface DbState {
  skus: Sku[]
  locations: Location[]
  units: Unit[]
  tokens: Token[]
  counts: StockCount[]
  printOrders: PrintOrder[]
  withdrawals: Withdrawal[]
  kyc: KycCase[]
  contracts: FinancingContract[]
  audit: AuditEntry[]
  trading: TradingState
  finance: FinanceTotals
  seq: number
}

export class ApiError extends Error {
  code: string
  constructor(code: string, message: string) {
    super(message)
    this.code = code
  }
}
