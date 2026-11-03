import { useState } from 'react'
import { Landmark, ShieldCheck, Wallet } from 'lucide-react'
import { api } from '../api'
import { Badge, Button, Card, Empty, Field, Modal, PageHeader, StatTile, Table } from '../components/ui'
import { dateTime, gram, rupiah, useApp, useData } from '../lib/app'
import { WITHDRAWAL_STATUS } from '../lib/labels'

export function PricingPage() {
  const { actor, act } = useApp()
  const trading = useData(() => api.getTrading())
  const skus = useData(() => api.listSkus())
  const [halting, setHalting] = useState(false)
  const isTreasury = actor.role === 'TREASURY'

  return (
    <>
      <PageHeader
        title="Harga"
        subtitle={trading && `Sumber ${trading.source}, diperbarui ${dateTime(trading.priceUpdatedAt)}`}
        actions={
          isTreasury &&
          trading &&
          (trading.halted ? (
            <Button onClick={() => act(() => api.resumeTrading(actor), 'Transaksi dibuka kembali.')}>Buka kembali transaksi</Button>
          ) : (
            <Button variant="danger" onClick={() => setHalting(true)}>
              Hentikan transaksi
            </Button>
          ))
        }
      />
      {trading?.halted && (
        <div className="alert">
          <Badge tone="critical">Transaksi dihentikan</Badge>
          <span>
            {trading.reason} · oleh {trading.haltedBy}. Harus dibuka kembali oleh orang lain.
          </span>
        </div>
      )}
      <Card flush>
        <Table head={['SKU', 'Berat', 'Harga beli', 'Harga buyback', 'Selisih']}>
          {skus?.map((s) => {
            const spread = ((s.buyPrice - s.buybackPrice) / s.buyPrice) * 100
            return (
              <tr key={s.id}>
                <td className="mono">{s.code}</td>
                <td>{gram(s.weightMg)}</td>
                <td>{rupiah(s.buyPrice)}</td>
                <td>{rupiah(s.buybackPrice)}</td>
                <td>{spread.toLocaleString('id-ID', { maximumFractionDigits: 1 })}%</td>
              </tr>
            )
          })}
        </Table>
      </Card>
      <p className="note">Harga buyback dihitung dari berat × harga buyback per gram.</p>
      {halting && (
        <Modal title="Hentikan transaksi" onClose={() => setHalting(false)}>
          <form
            className="stack"
            onSubmit={async (e) => {
              e.preventDefault()
              const reason = String(new FormData(e.currentTarget).get('reason'))
              const done = await act(() => api.haltTrading(actor, reason), 'Transaksi dihentikan.')
              if (done) setHalting(false)
            }}
          >
            <Field label="Alasan">
              <input name="reason" required />
            </Field>
            <Button type="submit" variant="danger">
              Hentikan sekarang
            </Button>
          </form>
        </Modal>
      )}
    </>
  )
}

export function FinancePage() {
  const { actor, act } = useApp()
  const totals = useData(() => api.getFinance())
  const withdrawals = useData(() => api.listWithdrawals())
  const invariants = useData(() => api.checkInvariants())

  return (
    <>
      <PageHeader title="Keuangan" />
      {totals && (
        <div className="stat-row">
          <StatTile icon={Wallet} value={rupiah(totals.walletTotal)} label="Total saldo pengguna" />
          <StatTile icon={Landmark} tint="sky" value={rupiah(totals.customerBankBalance)} label="Rekening dana pengguna" />
          <StatTile icon={ShieldCheck} tint="pink" value={rupiah(totals.ledgerLiability)} label="Kewajiban di ledger" />
        </div>
      )}
      <Card title="Penarikan dana" flush>
        <Table head={['Pengguna', 'Rekening', 'Nominal', 'Waktu', 'Status', '']}>
          {withdrawals?.map((w) => (
            <tr key={w.id}>
              <td>{w.owner}</td>
              <td>{w.bank}</td>
              <td>{rupiah(w.amount)}</td>
              <td>{dateTime(w.createdAt)}</td>
              <td>
                <Badge tone={WITHDRAWAL_STATUS[w.status][1]}>{WITHDRAWAL_STATUS[w.status][0]}</Badge>
              </td>
              <td>
                {actor.role === 'FINANCE' && w.status === 'FAILED' && (
                  <Button variant="soft" onClick={() => act(() => api.retryWithdrawal(actor, w.id), 'Penarikan dikirim ulang.')}>
                    Coba ulang
                  </Button>
                )}
                {w.status === 'UNKNOWN' && <span className="small muted">Cek status ke bank dulu</span>}
              </td>
            </tr>
          ))}
        </Table>
      </Card>
      <Card title="Rekonsiliasi" flush>
        <Table head={['Pemeriksaan', 'Hasil', 'Keterangan']}>
          {invariants?.map((r) => (
            <tr key={r.id}>
              <td>{r.label}</td>
              <td>{r.ok ? <Badge tone="good">Cocok</Badge> : <Badge tone="critical">Tidak cocok</Badge>}</td>
              <td className="muted">{r.detail}</td>
            </tr>
          ))}
        </Table>
      </Card>
    </>
  )
}

export function KycPage() {
  const { actor, act } = useApp()
  const cases = useData(() => api.listKyc())
  const [rejecting, setRejecting] = useState<number>()
  const canDecide = actor.role === 'COMPLIANCE'

  return (
    <>
      <PageHeader title="Tinjauan KYC" />
      <Card flush>
        <Table head={['Pemohon', 'NIK', 'Alasan tinjauan', 'Status', '']}>
          {cases?.map((k) => (
            <tr key={k.id}>
              <td>{k.applicant}</td>
              <td className="mono">{k.nikMasked}</td>
              <td>{k.reason}</td>
              <td>
                {k.status === 'PENDING' && <Badge tone="warning">Menunggu</Badge>}
                {k.status === 'APPROVED' && <Badge tone="good">Disetujui</Badge>}
                {k.status === 'REJECTED' && <Badge tone="critical">Ditolak</Badge>}
                {k.decisionNote && <div className="small muted">{k.decisionNote}</div>}
              </td>
              <td className="row-actions">
                {canDecide && k.status === 'PENDING' && (
                  <>
                    <Button variant="soft" onClick={() => act(() => api.decideKyc(actor, k.id, true, ''), 'KYC disetujui.')}>
                      Setujui
                    </Button>
                    <Button variant="ghost" onClick={() => setRejecting(k.id)}>
                      Tolak
                    </Button>
                  </>
                )}
              </td>
            </tr>
          ))}
        </Table>
      </Card>
      {rejecting !== undefined && (
        <Modal title="Tolak KYC" onClose={() => setRejecting(undefined)}>
          <form
            className="stack"
            onSubmit={async (e) => {
              e.preventDefault()
              const note = String(new FormData(e.currentTarget).get('note'))
              const done = await act(() => api.decideKyc(actor, rejecting, false, note), 'KYC ditolak.')
              if (done) setRejecting(undefined)
            }}
          >
            <Field label="Alasan penolakan">
              <input name="note" required />
            </Field>
            <Button type="submit" variant="danger">
              Tolak
            </Button>
          </form>
        </Modal>
      )}
    </>
  )
}

export function FinancingPage() {
  const contracts = useData(() => api.listContracts())
  return (
    <>
      <PageHeader title="Cicilan Mitra" />
      <Card flush>
        <Table head={['Kontrak', 'Pengguna', 'Token', 'Harga', 'Angsuran', 'Status']}>
          {contracts?.map((c) => (
            <tr key={c.id}>
              <td className="mono">{c.id}</td>
              <td>{c.owner}</td>
              <td className="mono small">{c.tokenCode}</td>
              <td>{rupiah(c.price)}</td>
              <td>
                {c.paidMonths} dari {c.tenorMonths} bulan
              </td>
              <td>
                {c.status === 'ON_TIME' && <Badge tone="good">Lancar</Badge>}
                {c.status === 'OVERDUE' && <Badge tone="critical">Menunggak</Badge>}
                {c.status === 'PAID_OFF' && <Badge tone="neutral">Lunas</Badge>}
              </td>
            </tr>
          ))}
        </Table>
      </Card>
    </>
  )
}

export function AuditPage() {
  const audit = useData(() => api.listAudit())
  return (
    <>
      <PageHeader title="Jejak Audit" />
      <Card flush>
        <Table head={['Waktu', 'Pelaku', 'Aksi', 'Objek', 'Keterangan']}>
          {audit?.map((a) => (
            <tr key={a.id}>
              <td>{dateTime(a.at)}</td>
              <td>{a.actor}</td>
              <td className="mono small">{a.action}</td>
              <td>{a.entity}</td>
              <td className="muted">{a.detail}</td>
            </tr>
          ))}
        </Table>
        {audit?.length === 0 && <Empty>Belum ada aktivitas.</Empty>}
      </Card>
    </>
  )
}
