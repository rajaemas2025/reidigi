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
  const canControlTrading = actor.role === 'FINANCE'

  return (
    <>
      <PageHeader
        title="Harga & Pasar Emas"
        subtitle={trading && `Kuotasi harga acuan dari ${trading.source} · Terakhir diperbarui ${dateTime(trading.priceUpdatedAt)}`}
        actions={
          canControlTrading &&
          trading &&
          (trading.halted ? (
            <Button onClick={() => act(() => api.resumeTrading(actor), 'Transaksi jual-beli emas berhasil dibuka kembali.')}>
              Buka Kembali Transaksi
            </Button>
          ) : (
            <Button variant="danger" onClick={() => setHalting(true)}>
              Hentikan Transaksi Darurat
            </Button>
          ))
        }
      />
      {trading?.halted && (
        <div className="alert">
          <Badge tone="critical">Transaksi Sedang Dihentikan</Badge>
          <span>
            {trading.reason} · Oleh {trading.haltedBy}. Demi kepatuhan prinsip dual-control, pembukaan kembali harus dilakukan oleh personel Finance yang berbeda.
          </span>
        </div>
      )}
      <Card flush>
        <Table head={['Kode SKU', 'Pecahan Berat', 'Harga Beli Pengguna', 'Harga Buyback Pengguna', 'Selisih Spread']}>
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
      <p className="note">Harga buyback pengguna dihitung otomatis berdasarkan berat keping × harga buyback dasar per gram.</p>
      {halting && (
        <Modal title="Hentikan Transaksi Emas Darurat" onClose={() => setHalting(false)}>
          <form
            className="stack"
            onSubmit={async (e) => {
              e.preventDefault()
              const reason = String(new FormData(e.currentTarget).get('reason'))
              const done = await act(() => api.haltTrading(actor, reason), 'Transaksi jual-beli emas berhasil dihentikan sementara.')
              if (done) setHalting(false)
            }}
          >
            <Field label="Alasan penghentian darurat" hint="Jelaskan pemicu darurat, misal: lonjakan volatilitas pasar ekstrem atau gangguan feed harga">
              <input name="reason" required placeholder="misal: Lonjakan volatilitas pasar melebihi batas toleransi" />
            </Field>
            <Button type="submit" variant="danger">
              Hentikan Transaksi Sekarang
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
      <PageHeader
        title="Kas & Rekonsiliasi Saldo"
        subtitle="Pemantauan kecukupan kas nasabah, penarikan dana, serta integritas buku besar (ledger)."
      />
      {totals && (
        <div className="stat-row">
          <StatTile icon={Wallet} value={rupiah(totals.walletTotal)} label="Total saldo dompet nasabah" />
          <StatTile icon={Landmark} tint="sky" value={rupiah(totals.customerBankBalance)} label="Rekening kas penampung (escrow)" />
          <StatTile icon={ShieldCheck} tint="pink" value={rupiah(totals.ledgerLiability)} label="Total kewajiban di ledger" />
        </div>
      )}
      <Card title="Permintaan Penarikan Dana (Withdrawal)" flush>
        <Table head={['Nama Nasabah', 'Rekening Bank Tujuan', 'Nominal Penarikan', 'Waktu Pengajuan', 'Status Transfer', 'Tindakan']}>
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
                  <Button variant="soft" onClick={() => act(() => api.retryWithdrawal(actor, w.id), 'Instruksi penarikan dana berhasil dikirim ulang ke bank rekanan.')}>
                    Kirim Ulang Transfer
                  </Button>
                )}
                {w.status === 'UNKNOWN' && <span className="small muted">Menunggu konfirmasi bank rekanan</span>}
              </td>
            </tr>
          ))}
        </Table>
      </Card>
      <Card title="Uji Integritas & Keseimbangan Buku Besar" flush>
        <Table head={['Parameter Pemeriksaan', 'Hasil Pengujian', 'Keterangan Selisih']}>
          {invariants?.map((r) => (
            <tr key={r.id}>
              <td>{r.label}</td>
              <td>{r.ok ? <Badge tone="good">Sesuai</Badge> : <Badge tone="critical">Ada selisih</Badge>}</td>
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
      <PageHeader
        title="Verifikasi Nasabah (KYC)"
        subtitle="Tinjau dan verifikasi dokumen identitas nasabah yang memerlukan persetujuan manual tim Compliance."
      />
      <Card flush>
        <Table head={['Nama Pemohon', 'NIK Terdaftar', 'Pemicu Tinjauan Manual', 'Status Verifikasi', 'Tindakan Tim']}>
          {cases?.map((k) => (
            <tr key={k.id}>
              <td>{k.applicant}</td>
              <td className="mono">{k.nikMasked}</td>
              <td>{k.reason}</td>
              <td>
                {k.status === 'PENDING' && <Badge tone="warning">Menunggu verifikasi</Badge>}
                {k.status === 'APPROVED' && <Badge tone="good">Terverifikasi</Badge>}
                {k.status === 'REJECTED' && <Badge tone="critical">Ditolak</Badge>}
                {k.decisionNote && <div className="small muted">Catatan: {k.decisionNote}</div>}
              </td>
              <td className="row-actions">
                {canDecide && k.status === 'PENDING' && (
                  <>
                    <Button variant="soft" onClick={() => act(() => api.decideKyc(actor, k.id, true, ''), 'Verifikasi KYC nasabah disetujui. Akun kini aktif penuh!')}>
                      Setujui KYC
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
        <Modal title="Tolak Verifikasi KYC Nasabah" onClose={() => setRejecting(undefined)}>
          <form
            className="stack"
            onSubmit={async (e) => {
              e.preventDefault()
              const note = String(new FormData(e.currentTarget).get('note'))
              const done = await act(() => api.decideKyc(actor, rejecting, false, note), 'Pengajuan KYC ditolak dan alasan telah dicatat.')
              if (done) setRejecting(undefined)
            }}
          >
            <Field label="Alasan penolakan dokumen" hint="Tulis alasan secara jelas agar dapat ditindaklanjuti oleh nasabah">
              <input name="note" required placeholder="misal: Foto e-KTP buram dan NIK tidak terbaca jelas" />
            </Field>
            <Button type="submit" variant="danger">
              Konfirmasi Penolakan
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
      <PageHeader
        title="Cicilan Emas Mitra"
        subtitle="Pemantauan kontrak cicilan emas yang didanai lunas di awal oleh agregator/mitra syariah (BPRS/Koperasi) hingga lunas."
      />
      <Card flush>
        <Table head={['ID Kontrak', 'Nama Nasabah', 'Mitra Pembiayaan Akad', 'Keping Terkunci', 'Total Pembiayaan', 'Progres Angsuran', 'Status Kontrak']}>
          {contracts?.map((c) => (
            <tr key={c.id}>
              <td className="mono">{c.id}</td>
              <td>{c.owner}</td>
              <td>
                <span className="small">{c.partner}</span>
              </td>
              <td className="mono small">{c.tokenCode}</td>
              <td>{rupiah(c.price)}</td>
              <td>
                {c.paidMonths} dari {c.tenorMonths} bulan
              </td>
              <td>
                {c.status === 'ON_TIME' && <Badge tone="good">Angsuran lancar</Badge>}
                {c.status === 'OVERDUE' && <Badge tone="critical">Menunggak</Badge>}
                {c.status === 'PAID_OFF' && <Badge tone="neutral">Lunas (Hak milik aktif)</Badge>}
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
      <PageHeader
        title="Jejak Audit Sistem"
        subtitle="Rekam jejak seluruh aktivitas operasional sistem yang bersifat permanen dan tidak dapat diubah (immutable log)."
      />
      <Card flush>
        <Table head={['Waktu Kejadian', 'Petugas / Sistem', 'Jenis Aktivitas', 'Objek Sasaran', 'Rincian Keterangan']}>
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
        {audit?.length === 0 && <Empty>Belum ada catatan aktivitas operasional yang terekam.</Empty>}
      </Card>
    </>
  )
}
