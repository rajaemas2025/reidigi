import { useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { api } from '../api'
import type { SkuRow, UnitRow, UnitStatus } from '../api/types'
import { Badge, Button, Card, Empty, Field, Modal, PageHeader, Table } from '../components/ui'
import { gram, rupiah, useApp, useData } from '../lib/app'
import { UNIT_STATUS } from '../lib/labels'

export function SkuPage() {
  const { actor, act } = useApp()
  const skus = useData(() => api.listSkus())
  const [adding, setAdding] = useState(false)
  const [editing, setEditing] = useState<SkuRow>()
  const canEdit = actor.role === 'WAREHOUSE_SUPERVISOR'

  return (
    <>
      <PageHeader
        title="Katalog SKU"
        actions={canEdit && <Button onClick={() => setAdding(true)}>Tambah SKU</Button>}
      />
      <Card flush>
        <Table head={['Kode', 'Produk', 'Berat', 'Harga beli', 'Harga buyback', 'Tersedia', 'Terkunci', 'Status', '']}>
          {skus?.map((s) => (
            <tr key={s.id}>
              <td className="mono">{s.code}</td>
              <td>{s.product}</td>
              <td>{gram(s.weightMg)}</td>
              <td>{rupiah(s.buyPrice)}</td>
              <td>{rupiah(s.buybackPrice)}</td>
              <td>{s.available}</td>
              <td>{s.locked}</td>
              <td>
                {!s.active ? (
                  <Badge tone="neutral">Nonaktif</Badge>
                ) : s.available === 0 ? (
                  <Badge tone="warning">Stok habis</Badge>
                ) : (
                  <Badge tone="good">Aktif</Badge>
                )}
              </td>
              <td>
                {canEdit && (
                  <Button variant="ghost" onClick={() => setEditing(s)}>
                    Ubah
                  </Button>
                )}
              </td>
            </tr>
          ))}
        </Table>
      </Card>
      {adding && <SkuForm onClose={() => setAdding(false)} />}
      {editing && (
        <Modal title={`Ubah ${editing.code}`} onClose={() => setEditing(undefined)}>
          <form
            className="stack"
            onSubmit={async (e) => {
              e.preventDefault()
              const form = new FormData(e.currentTarget)
              const done = await act(
                () =>
                  api.updateSku(actor, editing.id, {
                    active: form.get('active') === 'on',
                  }),
                'SKU diperbarui.',
              )
              if (done) setEditing(undefined)
            }}
          >
            <label className="check">
              <input name="active" type="checkbox" defaultChecked={editing.active} /> Dijual di aplikasi
            </label>
            <Button type="submit">Simpan</Button>
          </form>
        </Modal>
      )}
    </>
  )
}

function SkuForm({ onClose }: { onClose: () => void }) {
  const { actor, act } = useApp()
  return (
    <Modal title="Tambah SKU" onClose={onClose}>
      <form
        className="stack"
        onSubmit={async (e) => {
          e.preventDefault()
          const f = new FormData(e.currentTarget)
          const done = await act(
            () =>
              api.createSku(actor, {
                code: String(f.get('code')),
                product: String(f.get('product')),
                weightMg: Math.round(Number(f.get('gram')) * 1000),
                buyPrice: Number(f.get('buy')),
                buybackPrice: Number(f.get('buyback')),
              }),
            'SKU ditambahkan.',
          )
          if (done) onClose()
        }}
      >
        <Field label="Kode SKU">
          <input name="code" required placeholder="LMRE-3G" />
        </Field>
        <Field label="Nama produk">
          <input name="product" required placeholder="LM Raja Emas" />
        </Field>
        <Field label="Berat (gram)">
          <input name="gram" required type="number" min="0.001" step="0.001" />
        </Field>
        <div className="grid-2">
          <Field label="Harga beli (Rp)">
            <input name="buy" required type="number" min="1" />
          </Field>
          <Field label="Harga buyback (Rp)">
            <input name="buyback" required type="number" min="1" />
          </Field>
        </div>
        <Button type="submit">Simpan SKU</Button>
      </form>
    </Modal>
  )
}

export function StockPage() {
  const { actor, act } = useApp()
  const [params, setParams] = useSearchParams()
  const q = params.get('q') ?? ''
  const [skuId, setSkuId] = useState(0)
  const [locationId, setLocationId] = useState(0)
  const [status, setStatus] = useState<UnitStatus | ''>('')
  const [receiving, setReceiving] = useState(false)
  const [quarantining, setQuarantining] = useState<UnitRow>()

  const skus = useData(() => api.listSkus())
  const locations = useData(() => api.listLocations())
  const counts = useData(() => api.listCounts())
  const rows = useData(
    () => api.listUnits({ skuId: skuId || undefined, locationId: locationId || undefined, status: status || undefined, q }),
    [skuId, locationId, status, q],
  )

  const isSupervisor = actor.role === 'WAREHOUSE_SUPERVISOR'
  const canReceive = isSupervisor || actor.role === 'WAREHOUSE_STAFF'
  // Hitung buta: petugas tidak boleh melihat isi lokasi yang sedang dihitung.
  const blindLocations = new Set(
    actor.role === 'WAREHOUSE_STAFF' ? counts?.filter((c) => c.status === 'COUNTING').map((c) => c.locationId) : [],
  )
  const visible = rows?.filter((u) => !blindLocations.has(u.locationId))

  return (
    <>
      <PageHeader
        title="Stok Keping"
        actions={canReceive && <Button onClick={() => setReceiving(true)}>Terima barang</Button>}
      />
      <div className="filters">
        <input
          aria-label="Cari seri atau token"
          placeholder="Cari seri atau token"
          value={q}
          onChange={(e) => setParams(e.target.value ? { q: e.target.value } : {})}
        />
        <select aria-label="SKU" value={skuId} onChange={(e) => setSkuId(Number(e.target.value))}>
          <option value={0}>Semua SKU</option>
          {skus?.map((s) => (
            <option key={s.id} value={s.id}>
              {s.code}
            </option>
          ))}
        </select>
        <select aria-label="Lokasi" value={locationId} onChange={(e) => setLocationId(Number(e.target.value))}>
          <option value={0}>Semua lokasi</option>
          {locations?.map((l) => (
            <option key={l.id} value={l.id}>
              {l.name}
            </option>
          ))}
        </select>
        <select aria-label="Status" value={status} onChange={(e) => setStatus(e.target.value as UnitStatus | '')}>
          <option value="">Semua status</option>
          {Object.entries(UNIT_STATUS).map(([value, [label]]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </select>
      </div>
      {blindLocations.size > 0 && <p className="note">Keping di lokasi yang sedang diopname tidak ditampilkan.</p>}
      <Card flush>
        <Table head={['Nomor seri', 'SKU', 'Lokasi', 'Status', 'Token', '']}>
          {visible?.map((u) => (
            <tr key={u.id}>
              <td className="mono">{u.serial}</td>
              <td>{u.skuCode}</td>
              <td>{u.locationName}</td>
              <td>
                <Badge tone={UNIT_STATUS[u.status][1]}>{UNIT_STATUS[u.status][0]}</Badge>
              </td>
              <td className="mono small">{u.tokenCode ?? '–'}</td>
              <td className="row-actions">
                {isSupervisor && u.status === 'INBOUND_QC' && (
                  <>
                    <Button variant="soft" onClick={() => act(() => api.decideQc(actor, u.id, true), 'Keping lolos QC.')}>
                      Lolos
                    </Button>
                    <Button variant="ghost" onClick={() => act(() => api.decideQc(actor, u.id, false), 'Keping masuk karantina.')}>
                      Gagal
                    </Button>
                  </>
                )}
                {isSupervisor && (u.status === 'AVAILABLE' || u.status === 'ALLOCATED') && (
                  <Button variant="ghost" onClick={() => setQuarantining(u)}>
                    Karantina
                  </Button>
                )}
                {isSupervisor && u.status === 'QUARANTINE' && (
                  <Button variant="soft" onClick={() => act(() => api.releaseQuarantine(actor, u.id), 'Karantina dilepas.')}>
                    Lepas
                  </Button>
                )}
              </td>
            </tr>
          ))}
        </Table>
        {visible?.length === 0 && <Empty>Tidak ada keping yang cocok.</Empty>}
      </Card>

      {receiving && (
        <Modal title="Terima barang" onClose={() => setReceiving(false)}>
          <form
            className="stack"
            onSubmit={async (e) => {
              e.preventDefault()
              const f = new FormData(e.currentTarget)
              const done = await act(
                () =>
                  api.receiveUnits(actor, {
                    skuId: Number(f.get('sku')),
                    locationId: Number(f.get('location')),
                    serials: String(f.get('serials')).split(/[\s,]+/),
                  }),
                'Keping masuk antrean QC.',
              )
              if (done) setReceiving(false)
            }}
          >
            <Field label="SKU">
              <select name="sku">
                {skus?.map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.code}
                  </option>
                ))}
              </select>
            </Field>
            <Field label="Lokasi">
              <select name="location">
                {locations?.map((l) => (
                  <option key={l.id} value={l.id}>
                    {l.name}
                  </option>
                ))}
              </select>
            </Field>
            <Field label="Nomor seri" hint="Satu seri per baris">
              <textarea name="serials" rows={5} required />
            </Field>
            <Button type="submit">Catat penerimaan</Button>
          </form>
        </Modal>
      )}

      {quarantining && (
        <Modal title={`Karantina ${quarantining.serial}`} onClose={() => setQuarantining(undefined)}>
          <form
            className="stack"
            onSubmit={async (e) => {
              e.preventDefault()
              const reason = String(new FormData(e.currentTarget).get('reason'))
              const done = await act(() => api.quarantine(actor, quarantining.id, reason), 'Keping dikarantina.')
              if (done) setQuarantining(undefined)
            }}
          >
            {quarantining.tokenCode && (
              <p className="note">Token keping ini akan dipindah ke keping lain dengan SKU sama.</p>
            )}
            <Field label="Alasan">
              <input name="reason" required />
            </Field>
            <Button type="submit" variant="danger">
              Karantina keping
            </Button>
          </form>
        </Modal>
      )}
    </>
  )
}
