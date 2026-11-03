import { ArrowRight, ArrowUpRight, Boxes, Lock, PackageCheck, Truck } from 'lucide-react'
import { Link } from 'react-router-dom'
import { api } from '../api'
import { Badge, Card, StatTile, Table } from '../components/ui'
import { canOpen } from '../App'
import { dateTime, gram, rupiah, useApp, useData } from '../lib/app'

const LOW_STOCK_LIMIT = 2
const WEIGHT_BANDS = [
  { label: 'Di bawah 1 gr', from: 0, to: 1_000 },
  { label: '1–5 gr', from: 1_000, to: 10_000 },
  { label: '10–50 gr', from: 10_000, to: 100_000 },
  { label: '100 gr ke atas', from: 100_000, to: Infinity },
]

export function OverviewPage() {
  const { actor } = useApp()
  const skus = useData(() => api.listSkus())
  const alerts = useData(() => api.listOpenAlerts())
  const trading = useData(() => api.getTrading())
  const invariants = useData(() => api.checkInvariants())
  if (!skus || !alerts || !trading || !invariants) return null

  const available = skus.reduce((sum, s) => sum + s.available, 0)
  const locked = skus.reduce((sum, s) => sum + s.locked, 0)
  const total = available + locked + skus.reduce((sum, s) => sum + s.other, 0)
  const lockedPercent = total ? Math.round((locked / total) * 100) : 0
  const failedChecks = invariants.filter((r) => !r.ok)

  const bands = WEIGHT_BANDS.map((band) => ({
    name: band.label,
    count: skus.filter((s) => s.weightMg >= band.from && s.weightMg < band.to).reduce((sum, s) => sum + s.available, 0),
  }))
  const maxCount = Math.max(1, ...bands.map((b) => b.count))
  const lowStock = skus.filter((s) => s.active && s.available <= LOW_STOCK_LIMIT).slice(0, 6)

  const tasks = [
    { to: '/pemenuhan', label: 'Cetak menunggu picking', value: alerts.waitingPrint, tone: alerts.waitingPrint ? 'warning' : 'good' },
    { to: '/keuangan', label: 'Penarikan bermasalah', value: alerts.problemWithdrawals, tone: alerts.problemWithdrawals ? 'critical' : 'good' },
    { to: '/kyc', label: 'KYC menunggu tinjauan', value: alerts.pendingKyc, tone: alerts.pendingKyc ? 'warning' : 'good' },
  ].filter((t) => canOpen(actor.role, t.to)) as { to: string; label: string; value: number; tone: 'critical' | 'warning' | 'good' }[]

  return (
    <div className="overview">
      <div className="overview-main">
        <section className="hero">
          <span className="hero-eyebrow">Stok gudang Jakarta</span>
          <h1>
            {available} keping emas digital siap dijual
          </h1>
          {trading.halted ? (
            <p>Transaksi sedang dihentikan: {trading.reason}</p>
          ) : (
            <p>Harga dari {trading.source}, diperbarui {dateTime(trading.priceUpdatedAt)}</p>
          )}
          {canOpen(actor.role, '/opname') && (
            <Link className="hero-cta" to="/opname">
              Buka stock opname
              <span>
                <ArrowRight size={16} />
              </span>
            </Link>
          )}
        </section>

        <div className="stat-row">
          <StatTile icon={Boxes} value={available} label="Keping tersedia" />
          <StatTile icon={Lock} tint="pink" value={locked} label="Terkunci token" />
          <StatTile icon={Truck} tint="sky" value={alerts.waitingPrint} label="Cetak menunggu" />
        </div>

        <div className="section-head">
          <h2>Perlu tindakan</h2>
        </div>
        <div className="task-grid">
          {tasks.map((t) => (
            <Link key={t.label} to={t.to} className="task">
              <strong>{t.value}</strong>
              <span>{t.label}</span>
              <Badge tone={t.tone}>{t.value ? (t.tone === 'critical' ? 'Mendesak' : 'Perlu dicek') : 'Aman'}</Badge>
            </Link>
          ))}
        </div>

        <div className="section-head">
          <h2>Stok menipis</h2>
          {canOpen(actor.role, '/sku') && (
            <Link className="link" to="/sku">
              Lihat semua
            </Link>
          )}
        </div>
        <Card flush>
          <Table head={['SKU', 'Berat', 'Tersedia', 'Harga beli', '']}>
            {lowStock.map((s) => (
              <tr key={s.id}>
                <td className="mono">{s.code}</td>
                <td>{gram(s.weightMg)}</td>
                <td>{s.available}</td>
                <td>{rupiah(s.buyPrice)}</td>
                <td>
                  {canOpen(actor.role, '/stok') && (
                    <Link className="round-link" to={`/stok?q=${encodeURIComponent(s.code.replace(/[-.]/g, ''))}`} aria-label={`Lihat stok ${s.code}`}>
                      <ArrowUpRight size={16} />
                    </Link>
                  )}
                </td>
              </tr>
            ))}
          </Table>
        </Card>
      </div>

      <aside className="rail">
        <h2>Statistik</h2>
        <div
          className="ring"
          style={{ ['--p' as string]: `${lockedPercent}%` }}
          role="img"
          aria-label={`${lockedPercent} persen keping terkunci token`}
        >
          <div className="ring-inner">
            <Lock size={30} />
          </div>
          <span className="ring-badge">{lockedPercent}%</span>
        </div>
        <div className="rail-greeting">
          <strong>
            {actor.name}
          </strong>
          <span>{lockedPercent}% keping terkunci token</span>
        </div>

        <div className="bars-card">
          <div className="bars-title">Keping tersedia per berat</div>
          <div className="bars">
            {bands.map((l) => (
              <div key={l.name} className="bar-col" tabIndex={0} aria-label={`${l.name}: ${l.count} keping`}>
                <div className="bar-tip">
                  {l.name}: {l.count} keping
                </div>
                <span className="bar-value">{l.count}</span>
                <div className={`bar ${l.count === maxCount ? 'bar-max' : ''}`} style={{ height: `${Math.max(6, (l.count / maxCount) * 100)}%` }} />
                <span className="bar-label">{l.name}</span>
              </div>
            ))}
          </div>
        </div>

        <div className="rail-section">
          <h2>Rekonsiliasi</h2>
          <div className="check-list">
            {invariants.map((r) => (
              <div key={r.id} className="check-item">
                <span>{r.label}</span>
                {r.ok ? <Badge tone="good">Cocok</Badge> : <Badge tone="critical">Tidak cocok</Badge>}
              </div>
            ))}
          </div>
          {failedChecks.length === 0 && (
            <div className="rail-ok">
              <PackageCheck size={16} /> Semua cocok
            </div>
          )}
        </div>
      </aside>
    </div>
  )
}
