import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { api } from '../api'
import { Badge, Button, Card, Empty, Field, Modal, PageHeader, ScanInput, Table } from '../components/ui'
import { dateTime, useApp, useData } from '../lib/app'
import { COUNT_STATUS, VARIANCE } from '../lib/labels'

export function CountsPage() {
  const { actor, act } = useApp()
  const navigate = useNavigate()
  const counts = useData(() => api.listCounts())
  const locations = useData(() => api.listLocations())
  const [creating, setCreating] = useState(false)

  return (
    <>
      <PageHeader
        title="Stock Opname Brankas"
        subtitle="Pemeriksaan fisik keping berkala dengan metode blind audit tanpa menampilkan ekspektasi stok."
        actions={actor.role === 'WAREHOUSE_SUPERVISOR' && <Button onClick={() => setCreating(true)}>Buka Sesi Opname Baru</Button>}
      />
      <Card flush>
        <Table head={['No. Sesi', 'Lokasi Brankas', 'Waktu Dibuat', 'Total Pindaian', 'Temuan Selisih', 'Status Sesi', 'Aksi']}>
          {counts?.map((c) => (
            <tr key={c.id}>
              <td className="mono">#{c.id}</td>
              <td>{c.locationName}</td>
              <td>
                {dateTime(c.createdAt)}
                <div className="small muted">Oleh {c.createdBy}</div>
              </td>
              <td>{c.scans.length} keping</td>
              <td>{c.status === 'COUNTING' ? '–' : `${c.variances.length} temuan`}</td>
              <td>
                <Badge tone={COUNT_STATUS[c.status][1]}>{COUNT_STATUS[c.status][0]}</Badge>
              </td>
              <td>
                <Link className="link" to={`/opname/${c.id}`}>
                  Buka Sesi
                </Link>
              </td>
            </tr>
          ))}
        </Table>
        {counts?.length === 0 && <Empty>Belum ada sesi stock opname yang tercatat.</Empty>}
      </Card>
      {creating && (
        <Modal title="Buka Sesi Stock Opname Baru" onClose={() => setCreating(false)}>
          <form
            className="stack"
            onSubmit={async (e) => {
              e.preventDefault()
              const location = Number(new FormData(e.currentTarget).get('location'))
              const count = await act(() => api.createCount(actor, location), 'Sesi stock opname berhasil dibuka! Petugas dapat mulai memindai.')
              if (count) navigate(`/opname/${count.id}`)
            }}
          >
            <Field label="Pilih lokasi brankas yang akan dihitung" hint="Satu lokasi brankas hanya boleh memiliki satu sesi aktif dalam satu waktu">
              <select name="location">
                {locations?.map((l) => (
                  <option key={l.id} value={l.id}>
                    {l.name}
                  </option>
                ))}
              </select>
            </Field>
            <Button type="submit">Mulai Penghitungan</Button>
          </form>
        </Modal>
      )}
    </>
  )
}

export function CountDetailPage() {
  const { actor, act } = useApp()
  const id = Number(useParams().id)
  const count = useData(() => api.getCount(id), [id])
  const [lastScan, setLastScan] = useState<string>()

  if (!count) return null

  const canScan = actor.role === 'WAREHOUSE_STAFF' || actor.role === 'WAREHOUSE_SUPERVISOR'
  const canApprove =
    (count.status === 'AWAITING_APPROVAL' && actor.role === 'WAREHOUSE_SUPERVISOR') ||
    (count.status === 'AWAITING_FINANCE' && actor.role === 'FINANCE')

  return (
    <>
      <PageHeader
        title={`Sesi Opname #${count.id} · ${count.locationName}`}
        subtitle={`Dibuka oleh ${count.createdBy} pada ${dateTime(count.createdAt)}`}
        actions={<Badge tone={COUNT_STATUS[count.status][1]}>{COUNT_STATUS[count.status][0]}</Badge>}
      />

      {count.status === 'COUNTING' && (
        <Card title="Pindai Keping Fisik">
          <p className="note">Pindai seluruh barcode nomor seri keping yang berada di lokasi brankas ini satu per satu.</p>
          {canScan ? (
            <ScanInput
              placeholder="Arahkan scanner atau ketik nomor seri keping..."
              onScan={async (serial) => {
                const result = await act(() => api.scan(actor, count.id, serial))
                if (result) setLastScan(result.duplicate ? `${serial.toUpperCase()} sudah pernah dipindai sebelumnya` : `${serial.toUpperCase()} berhasil dicatat`)
              }}
            />
          ) : (
            <p className="muted">Hanya petugas gudang yang berwenang melakukan pemindaian fisik keping.</p>
          )}
          <div className="scan-total">
            <strong>{count.scans.length}</strong> keping telah dipindai{lastScan && <span className="muted"> · {lastScan}</span>}
          </div>
          {canScan && (
            <Button variant="dark" onClick={() => act(() => api.submitCount(actor, count.id), 'Hasil penghitungan berhasil dikirim untuk verifikasi.')}>
              Selesai Memindai & Ajukan Hitungan
            </Button>
          )}
        </Card>
      )}

      {count.status !== 'COUNTING' && (
        <Card title={`Temuan Selisih Fisik (${count.variances.length})`} flush>
          {count.variances.length === 0 ? (
            <Empty>Luar biasa! Tidak ada selisih, seluruh fisik keping cocok dengan catatan sistem.</Empty>
          ) : (
            <Table head={['Nomor Seri Keping', 'Kategori Selisih', 'Hasil Hitung Ulang', 'Tindakan Petugas']}>
              {count.variances.map((v) => (
                <tr key={v.id}>
                  <td className="mono">{v.serial}</td>
                  <td>
                    <Badge tone="warning">{VARIANCE[v.type]}</Badge>
                  </td>
                  <td>
                    {v.recountFound === null ? (
                      <span className="muted">Menunggu hitung ulang</span>
                    ) : (
                      <>
                        {v.recountFound ? 'Ditemukan kembali' : 'Tetap tidak ditemukan'}
                        <div className="small muted">Oleh {v.recountBy}</div>
                      </>
                    )}
                  </td>
                  <td className="row-actions">
                    {count.status === 'RECOUNT' && canScan && (
                      <>
                        <Button variant="soft" onClick={() => act(() => api.recordRecount(actor, count.id, v.id, true), 'Keping dicatat ditemukan.')}>
                          Ditemukan
                        </Button>
                        <Button variant="ghost" onClick={() => act(() => api.recordRecount(actor, count.id, v.id, false), 'Keping dipastikan tidak ada.')}>
                          Tetap Tidak Ada
                        </Button>
                      </>
                    )}
                  </td>
                </tr>
              ))}
            </Table>
          )}
        </Card>
      )}

      {count.status === 'RECOUNT' && (
        <p className="note">Sesuai SOP blind audit, proses hitung ulang wajib dilakukan oleh petugas yang berbeda demi menjaga objektivitas.</p>
      )}

      {canApprove && (
        <Button onClick={() => act(() => api.approveCount(actor, count.id), 'Keputusan stock opname berhasil disimpan dan dibukukan.')}>
          {count.status === 'AWAITING_FINANCE' ? 'Setujui Hasil & Sesuaikan Ledger (Finance)' : 'Setujui & Tutup Sesi Opname (Supervisor)'}
        </Button>
      )}
      {count.approvedBy && <p className="note">Sesi stock opname ini telah disetujui resmi oleh {count.approvedBy}.</p>}
    </>
  )
}
