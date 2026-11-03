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
        title="Katalog Produk SKU"
        subtitle="Daftar spesifikasi pecahan emas, acuan harga transaksi, serta ketersediaan stok fisik."
        actions={canEdit && <Button onClick={() => setAdding(true)}>Tambah Produk SKU</Button>}
      />
      <Card flush>
        <Table head={['Kode SKU', 'Nama Produk', 'Pecahan Berat', 'Harga Beli Pengguna', 'Harga Buyback', 'Siap Jual', 'Terikat Token', 'Status Penjualan', 'Aksi']}>
          {skus?.map((s) => (
            <tr key={s.id}>
              <td className="mono">{s.code}</td>
              <td>{s.product}</td>
              <td>{gram(s.weightMg)}</td>
              <td>{rupiah(s.buyPrice)}</td>
              <td>{rupiah(s.buybackPrice)}</td>
              <td>{s.available} keping</td>
              <td>{s.locked} keping</td>
              <td>
                {!s.active ? (
                  <Badge tone="neutral">Nonaktif</Badge>
                ) : s.available === 0 ? (
                  <Badge tone="warning">Stok habis</Badge>
                ) : (
                  <Badge tone="good">Aktif dijual</Badge>
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
        <Modal title={`Pengaturan SKU ${editing.code}`} onClose={() => setEditing(undefined)}>
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
                'Pengaturan produk SKU berhasil diperbarui.',
              )
              if (done) setEditing(undefined)
            }}
          >
            <label className="check">
              <input name="active" type="checkbox" defaultChecked={editing.active} /> Aktifkan penjualan produk ini di aplikasi nasabah
            </label>
            <Button type="submit">Simpan Pengaturan</Button>
          </form>
        </Modal>
      )}
    </>
  )
}

function SkuForm({ onClose }: { onClose: () => void }) {
  const { actor, act } = useApp()
  return (
    <Modal title="Tambah Produk SKU Baru" onClose={onClose}>
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
            'Produk SKU baru berhasil ditambahkan.',
          )
          if (done) onClose()
        }}
      >
        <Field label="Kode SKU" hint="Kode unik produk, misal: LMRE-3G">
          <input name="code" required placeholder="LMRE-3G" />
        </Field>
        <Field label="Nama produk" hint="Nama resmi yang tampil di aplikasi nasabah">
          <input name="product" required placeholder="LM Logam Mulia 3 gr" />
        </Field>
        <Field label="Pecahan berat (gram)" hint="Berat bersih keping emas">
          <input name="gram" required type="number" min="0.001" step="0.001" placeholder="3" />
        </Field>
        <div className="grid-2">
          <Field label="Harga beli pengguna (Rp)" hint="Harga saat nasabah membeli">
            <input name="buy" required type="number" min="1" placeholder="3900000" />
          </Field>
          <Field label="Harga buyback (Rp)" hint="Harga saat nasabah menjual">
            <input name="buyback" required type="number" min="1" placeholder="3720000" />
          </Field>
        </div>
        <Button type="submit">Simpan Produk SKU</Button>
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
        title="Stok Keping Fisik"
        subtitle="Pelacakan nomor seri fisik, brankas penyimpanan, proses QC, dan keterikatan token digital."
        actions={canReceive && <Button onClick={() => setReceiving(true)}>Catat Penerimaan Keping</Button>}
      />
      <div className="filters">
        <input
          aria-label="Cari seri atau token"
          placeholder="Cari nomor seri keping atau kode token..."
          value={q}
          onChange={(e) => setParams(e.target.value ? { q: e.target.value } : {})}
        />
        <select aria-label="Filter SKU" value={skuId} onChange={(e) => setSkuId(Number(e.target.value))}>
          <option value={0}>Semua jenis SKU</option>
          {skus?.map((s) => (
            <option key={s.id} value={s.id}>
              {s.code} ({s.product})
            </option>
          ))}
        </select>
        <select aria-label="Filter Lokasi" value={locationId} onChange={(e) => setLocationId(Number(e.target.value))}>
          <option value={0}>Semua brankas / lokasi</option>
          {locations?.map((l) => (
            <option key={l.id} value={l.id}>
              {l.name}
            </option>
          ))}
        </select>
        <select aria-label="Filter Status" value={status} onChange={(e) => setStatus(e.target.value as UnitStatus | '')}>
          <option value="">Semua status keping</option>
          {Object.entries(UNIT_STATUS).map(([value, [label]]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </select>
      </div>
      {blindLocations.size > 0 && (
        <p className="note">
          Lokasi brankas yang sedang dalam proses stock opname disembunyikan sementara demi menjaga objektivitas audit (blind audit).
        </p>
      )}
      <Card flush>
        <Table head={['Nomor Seri Keping', 'Kode SKU', 'Lokasi Brankas', 'Status Fisik', 'Token Kepemilikan', 'Aksi Petugas']}>
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
                    <Button variant="soft" onClick={() => act(() => api.decideQc(actor, u.id, true), 'Keping lolos QC dan siap dialokasikan.')}>
                      Lolos QC
                    </Button>
                    <Button variant="ghost" onClick={() => act(() => api.decideQc(actor, u.id, false), 'Keping tidak memenuhi standar QC dan masuk karantina.')}>
                      Karantina (QC)
                    </Button>
                  </>
                )}
                {isSupervisor && (u.status === 'AVAILABLE' || u.status === 'ALLOCATED') && (
                  <Button variant="ghost" onClick={() => setQuarantining(u)}>
                    Karantina
                  </Button>
                )}
                {isSupervisor && u.status === 'QUARANTINE' && (
                  <Button variant="soft" onClick={() => act(() => api.releaseQuarantine(actor, u.id), 'Keping telah diverifikasi ulang dan status karantina dicabut.')}>
                    Bebaskan Karantina
                  </Button>
                )}
              </td>
            </tr>
          ))}
        </Table>
        {visible?.length === 0 && <Empty>Tidak ditemukan keping emas yang sesuai dengan kriteria pencarian.</Empty>}
      </Card>

      {receiving && (
        <Modal title="Catat Penerimaan Keping Emas Baru" onClose={() => setReceiving(false)}>
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
                'Penerimaan keping berhasil dicatat dan masuk antrean QC.',
              )
              if (done) setReceiving(false)
            }}
          >
            <Field label="Pilih jenis SKU produk">
              <select name="sku">
                {skus?.map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.code} · {s.product}
                  </option>
                ))}
              </select>
            </Field>
            <Field label="Pilih lokasi brankas tujuan">
              <select name="location">
                {locations?.map((l) => (
                  <option key={l.id} value={l.id}>
                    {l.name}
                  </option>
                ))}
              </select>
            </Field>
            <Field label="Daftar nomor seri fisik" hint="Ketik atau scan barcode nomor seri keping, satu per baris atau pisahkan dengan spasi/koma">
              <textarea name="serials" rows={5} required placeholder="misal:&#10;REI-03-9001&#10;REI-03-9002" />
            </Field>
            <Button type="submit">Simpan & Masukkan Antrean QC</Button>
          </form>
        </Modal>
      )}

      {quarantining && (
        <Modal title={`Karantina Keping #${quarantining.serial}`} onClose={() => setQuarantining(undefined)}>
          <form
            className="stack"
            onSubmit={async (e) => {
              e.preventDefault()
              const reason = String(new FormData(e.currentTarget).get('reason'))
              const done = await act(() => api.quarantine(actor, quarantining.id, reason), 'Keping berhasil dikarantina dan token nasabah telah diamankan.')
              if (done) setQuarantining(undefined)
            }}
          >
            {quarantining.tokenCode && (
              <p className="note">Perhatian: Hak kepemilikan emas digital nasabah akan otomatis dialihkan ke keping fisik lain yang sehat dengan SKU yang sama.</p>
            )}
            <Field label="Alasan karantina keping" hint="Jelaskan kondisi fisik atau kendala teknis keping ini">
              <input name="reason" required placeholder="misal: kemasan rusak, goresan fisik, segel terbuka" />
            </Field>
            <Button type="submit" variant="danger">
              Konfirmasi Karantina
            </Button>
          </form>
        </Modal>
      )}
    </>
  )
}
