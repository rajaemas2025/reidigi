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
        title="Stock Opname"
        actions={actor.role === 'WAREHOUSE_SUPERVISOR' && <Button onClick={() => setCreating(true)}>Mulai opname</Button>}
      />
      <Card flush>
        <Table head={['Sesi', 'Lokasi', 'Dibuat', 'Pindaian', 'Selisih', 'Status', '']}>
          {counts?.map((c) => (
            <tr key={c.id}>
              <td className="mono">#{c.id}</td>
              <td>{c.locationName}</td>
              <td>
                {dateTime(c.createdAt)}
                <div className="small muted">{c.createdBy}</div>
              </td>
              <td>{c.scans.length}</td>
              <td>{c.status === 'COUNTING' ? '–' : c.variances.length}</td>
              <td>
                <Badge tone={COUNT_STATUS[c.status][1]}>{COUNT_STATUS[c.status][0]}</Badge>
              </td>
              <td>
                <Link className="link" to={`/opname/${c.id}`}>
                  Buka
                </Link>
              </td>
            </tr>
          ))}
        </Table>
        {counts?.length === 0 && <Empty>Belum ada sesi opname.</Empty>}
      </Card>
      {creating && (
        <Modal title="Mulai opname" onClose={() => setCreating(false)}>
          <form
            className="stack"
            onSubmit={async (e) => {
              e.preventDefault()
              const location = Number(new FormData(e.currentTarget).get('location'))
              const count = await act(() => api.createCount(actor, location), 'Sesi opname dimulai.')
              if (count) navigate(`/opname/${count.id}`)
            }}
          >
            <Field label="Lokasi">
              <select name="location">
                {locations?.map((l) => (
                  <option key={l.id} value={l.id}>
                    {l.name}
                  </option>
                ))}
              </select>
            </Field>
            <Button type="submit">Mulai</Button>
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
        title={`Opname #${count.id} · ${count.locationName}`}
        subtitle={`Dibuat ${count.createdBy}, ${dateTime(count.createdAt)}`}
        actions={<Badge tone={COUNT_STATUS[count.status][1]}>{COUNT_STATUS[count.status][0]}</Badge>}
      />

      {count.status === 'COUNTING' && (
        <Card title="Pindai keping">
          <p className="note">Pindai semua keping yang ada di lokasi ini.</p>
          {canScan ? (
            <ScanInput
              onScan={async (serial) => {
                const result = await act(() => api.scan(actor, count.id, serial))
                if (result) setLastScan(result.duplicate ? `${serial.toUpperCase()} sudah dipindai` : `${serial.toUpperCase()} tercatat`)
              }}
            />
          ) : (
            <p className="muted">Hanya tim gudang yang bisa memindai.</p>
          )}
          <div className="scan-total">
            <strong>{count.scans.length}</strong> keping dipindai{lastScan && <span className="muted"> · {lastScan}</span>}
          </div>
          {canScan && (
            <Button variant="dark" onClick={() => act(() => api.submitCount(actor, count.id), 'Hitungan dikirim.')}>
              Selesai dan kirim hitungan
            </Button>
          )}
        </Card>
      )}

      {count.status !== 'COUNTING' && (
        <Card title={`Selisih (${count.variances.length})`} flush>
          {count.variances.length === 0 ? (
            <Empty>Tidak ada selisih.</Empty>
          ) : (
            <Table head={['Nomor seri', 'Jenis', 'Hitung ulang', '']}>
              {count.variances.map((v) => (
                <tr key={v.id}>
                  <td className="mono">{v.serial}</td>
                  <td>
                    <Badge tone="warning">{VARIANCE[v.type]}</Badge>
                  </td>
                  <td>
                    {v.recountFound === null ? (
                      <span className="muted">Belum</span>
                    ) : (
                      <>
                        {v.recountFound ? 'Ditemukan' : 'Tidak ditemukan'}
                        <div className="small muted">{v.recountBy}</div>
                      </>
                    )}
                  </td>
                  <td className="row-actions">
                    {count.status === 'RECOUNT' && canScan && (
                      <>
                        <Button variant="soft" onClick={() => act(() => api.recordRecount(actor, count.id, v.id, true))}>
                          Ditemukan
                        </Button>
                        <Button variant="ghost" onClick={() => act(() => api.recordRecount(actor, count.id, v.id, false))}>
                          Tidak ada
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

      {count.status === 'RECOUNT' && <p className="note">Hitung ulang harus oleh orang lain.</p>}

      {canApprove && (
        <Button onClick={() => act(() => api.approveCount(actor, count.id), 'Keputusan opname tersimpan.')}>
          {count.status === 'AWAITING_FINANCE' ? 'Setujui sebagai Finance' : 'Setujui hasil opname'}
        </Button>
      )}
      {count.approvedBy && <p className="note">Disetujui oleh {count.approvedBy}.</p>}
    </>
  )
}
