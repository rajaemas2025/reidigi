import { useState } from 'react'
import type { ComponentType, FormEvent } from 'react'
import {
  Banknote,
  Bell,
  Boxes,
  ClipboardCheck,
  CreditCard,
  LayoutGrid,
  PackageOpen,
  RotateCcw,
  ScrollText,
  Search,
  ShieldCheck,
  Tag,
  Tags,
} from 'lucide-react'
import { BrowserRouter, Link, NavLink, Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom'
import { resetDemoData, api } from './api'
import { USERS } from './api/seed'
import type { Role } from './api/types'
import { AppProvider, useApp, useData } from './lib/app'
import { ROLE_LABEL } from './lib/labels'
import { AuditPage, FinancePage, FinancingPage, KycPage, PricingPage } from './pages/business'
import { CountDetailPage, CountsPage } from './pages/counts'
import { OverviewPage } from './pages/overview'
import { FulfillmentPage } from './pages/fulfillment'
import { SkuPage, StockPage } from './pages/warehouse'

const ALL: Role[] = ['WAREHOUSE_STAFF', 'WAREHOUSE_SUPERVISOR', 'TREASURY', 'FINANCE', 'COMPLIANCE', 'CS', 'AUDITOR']
const STOCK_READERS: Role[] = ['WAREHOUSE_STAFF', 'WAREHOUSE_SUPERVISOR', 'TREASURY', 'FINANCE', 'CS', 'AUDITOR']

interface NavItem {
  to: string
  label: string
  icon: ComponentType<{ size?: number }>
  roles: Role[]
}

const NAV: { group: string; items: NavItem[] }[] = [
  { group: 'Dasbor Utama', items: [{ to: '/', label: 'Ringkasan', icon: LayoutGrid, roles: ALL }] },
  {
    group: 'Operasional Brankas',
    items: [
      { to: '/sku', label: 'Katalog Produk SKU', icon: Tags, roles: STOCK_READERS },
      { to: '/stok', label: 'Stok Keping Fisik', icon: Boxes, roles: STOCK_READERS },
      { to: '/opname', label: 'Stock Opname Brankas', icon: ClipboardCheck, roles: ['WAREHOUSE_STAFF', 'WAREHOUSE_SUPERVISOR', 'FINANCE', 'AUDITOR'] },
      { to: '/cetak', label: 'Cetak Fisik', icon: PackageOpen, roles: ['WAREHOUSE_STAFF', 'WAREHOUSE_SUPERVISOR', 'AUDITOR'] },
    ],
  },
  {
    group: 'Bisnis & Keuangan',
    items: [
      { to: '/harga', label: 'Harga & Pasar Emas', icon: Tag, roles: ['TREASURY', 'FINANCE', 'CS', 'AUDITOR'] },
      { to: '/keuangan', label: 'Kas & Rekonsiliasi Saldo', icon: Banknote, roles: ['FINANCE', 'TREASURY', 'COMPLIANCE', 'CS', 'AUDITOR'] },
      { to: '/cicilan', label: 'Cicilan Emas Mitra', icon: CreditCard, roles: ['FINANCE', 'COMPLIANCE', 'CS', 'AUDITOR'] },
      { to: '/kyc', label: 'Verifikasi Nasabah (KYC)', icon: ShieldCheck, roles: ['COMPLIANCE', 'CS', 'AUDITOR'] },
    ],
  },
  {
    group: 'Kepatuhan & Pengawasan',
    items: [{ to: '/audit', label: 'Jejak Audit Sistem', icon: ScrollText, roles: ['WAREHOUSE_SUPERVISOR', 'FINANCE', 'COMPLIANCE', 'AUDITOR'] }],
  },
]

const ALL_ITEMS = NAV.flatMap((g) => g.items)

export function canOpen(role: Role, path: string) {
  const item = ALL_ITEMS.find((i) => i.to === path)
  return !item || item.roles.includes(role)
}

function Guard({ path, children }: { path: string; children: React.ReactNode }) {
  const { actor } = useApp()
  if (!canOpen(actor.role, path)) {
    return (
      <div className="empty empty-page">
        <strong>Akses Halaman Dibatasi</strong>
        <p className="muted" style={{ maxWidth: 440 }}>
          Halaman ini memerlukan wewenang khusus dan saat ini tidak tersedia untuk peran {ROLE_LABEL[actor.role]}.
        </p>
        <Link className="link" to="/">
          Kembali ke ringkasan utama
        </Link>
      </div>
    )
  }
  return <>{children}</>
}

function Sidebar() {
  const { actor } = useApp()
  return (
    <aside className="sidebar">
      <Link to="/" className="brand">
        <img className="brand-mark" src="/logo-rei.png" alt="Logo Raja Emas Indonesia" />
        <span>
          REIDIG
          <small>Raja Emas Indonesia</small>
        </span>
      </Link>
      <nav aria-label="Menu navigasi utama">
        {NAV.map((group) => {
          const items = group.items.filter((i) => i.roles.includes(actor.role))
          if (!items.length) return null
          return (
            <div key={group.group} className="nav-group">
              <div className="nav-title">{group.group}</div>
              {items.map(({ to, label, icon: Icon }) => (
                <NavLink key={to} to={to} end={to === '/'} className="nav-link">
                  <Icon size={20} />
                  {label}
                </NavLink>
              ))}
            </div>
          )
        })}
      </nav>
      <button
        type="button"
        className="reset"
        onClick={() => {
          if (window.confirm('Kembalikan semua data simulasi ke kondisi awal? Seluruh perubahan uji coba Anda akan diatur ulang.')) {
            resetDemoData()
          }
        }}
      >
        <RotateCcw size={18} /> Atur ulang data simulasi
      </button>
    </aside>
  )
}

function Topbar() {
  const { actor, setActor } = useApp()
  const navigate = useNavigate()
  const [q, setQ] = useState('')
  const alerts = useData(() => api.listOpenAlerts())
  const urgent = alerts ? alerts.problemWithdrawals : 0
  const canSearch = canOpen(actor.role, '/stok')

  const submit = (e: FormEvent) => {
    e.preventDefault()
    if (q.trim()) navigate(`/stok?q=${encodeURIComponent(q.trim())}`)
  }

  return (
    <header className="topbar">
      <form className="search" onSubmit={submit}>
        <Search size={18} />
        <input
          aria-label="Cari nomor seri keping atau kode token"
          placeholder={canSearch ? 'Cari nomor seri keping atau kode token...' : 'Pencarian keping hanya untuk tim terkait'}
          disabled={!canSearch}
          value={q}
          onChange={(e) => setQ(e.target.value)}
        />
      </form>
      <span
        className="icon-btn bell"
        title={urgent > 0 ? `${urgent} penarikan dana membutuhkan perhatian segera` : 'Tidak ada hal mendesak saat ini'}
        aria-label={urgent > 0 ? `${urgent} hal mendesak` : 'Tidak ada kendala'}
      >
        <Bell size={18} />
        {urgent > 0 && <i>{urgent}</i>}
      </span>
      <div className="divider" />
      <div className="user">
        <span className="avatar">{actor.name.charAt(0)}</span>
        <label>
          <small>Bertugas sebagai</small>
          <select aria-label="Ganti peran pengguna" value={actor.id} onChange={(e) => setActor(e.target.value)}>
            {USERS.map((u) => (
              <option key={u.id} value={u.id}>
                {u.name} ({ROLE_LABEL[u.role]})
              </option>
            ))}
          </select>
        </label>
      </div>
    </header>
  )
}

function Shell() {
  const location = useLocation()
  return (
    <div className="shell">
      <Sidebar />
      <div className="main">
        <Topbar />
        <main key={location.pathname}>
          <Routes>
            <Route path="/" element={<OverviewPage />} />
            <Route path="/sku" element={<Guard path="/sku"><SkuPage /></Guard>} />
            <Route path="/stok" element={<Guard path="/stok"><StockPage /></Guard>} />
            <Route path="/opname" element={<Guard path="/opname"><CountsPage /></Guard>} />
            <Route path="/opname/:id" element={<Guard path="/opname"><CountDetailPage /></Guard>} />
            <Route path="/cetak" element={<Guard path="/cetak"><FulfillmentPage /></Guard>} />
            <Route path="/harga" element={<Guard path="/harga"><PricingPage /></Guard>} />
            <Route path="/keuangan" element={<Guard path="/keuangan"><FinancePage /></Guard>} />
            <Route path="/cicilan" element={<Guard path="/cicilan"><FinancingPage /></Guard>} />
            <Route path="/kyc" element={<Guard path="/kyc"><KycPage /></Guard>} />
            <Route path="/audit" element={<Guard path="/audit"><AuditPage /></Guard>} />
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </main>
      </div>
    </div>
  )
}

export default function App() {
  return (
    <BrowserRouter>
      <AppProvider>
        <Shell />
      </AppProvider>
    </BrowserRouter>
  )
}
