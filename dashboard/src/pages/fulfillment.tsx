import { useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api'
import type { PrintOrder } from '../api/types'
import { Badge, Button, Card, Empty, Field, Modal, PageHeader, ScanInput, Table } from '../components/ui'
import { dateTime, gram, useApp, useData } from '../lib/app'
import { PRINT_STATUS } from '../lib/labels'

type Dialog = { kind: 'pick' | 'ship' | 'receive' | 'pickup'; id: number }

export function FulfillmentPage() {
  const { actor, act } = useApp()
  const orders = useData(() => api.listPrintOrders())
  const [dialog, setDialog] = useState<Dialog>()
  const canAct = actor.role === 'WAREHOUSE_STAFF' || actor.role === 'WAREHOUSE_SUPERVISOR'
  const current = orders?.find((o) => o.id === dialog?.id)
  const close = () => setDialog(undefined)

  return (
    <>
      <PageHeader
        title="Pemenuhan Cetak"
        subtitle="Kirim JNE atau ambil di cabang (minimal 2 gr)"
      />
      <Card flush>
        <Table head={['Pesanan', 'Pengguna', 'Keping', 'Tujuan', 'Status', '']}>
          {orders?.map((o) => (
            <tr key={o.id}>
              <td className="mono">#{o.id}</td>
              <td>{o.owner}</td>
              <td>
                {o.items.map((item) => (
                  <div key={item.serial} className="item-line">
                    <Link className="link mono small" to={`/stok?q=${item.serial}`}>
                      {item.serial}
                    </Link>
                    <span className="small muted">{gram(item.weightMg)}</span>
                  </div>
                ))}
              </td>
              <td>
                {o.destination}
                {o.trackingNumber && <div className="small muted">No. kirim {o.trackingNumber}</div>}
                {o.pickupDeadline && o.status === 'READY_PICKUP' && (
                  <div className="small muted">Batas ambil {dateTime(o.pickupDeadline)}</div>
                )}
              </td>
              <td>
                <Badge tone={PRINT_STATUS[o.status][1]}>{PRINT_STATUS[o.status][0]}</Badge>
                {progress(o) && <div className="small muted">{progress(o)}</div>}
              </td>
              <td>
                {canAct && o.status === 'WAITING' && (
                  <Button variant="soft" onClick={() => setDialog({ kind: 'pick', id: o.id })}>
                    Pindai picking
                  </Button>
                )}
                {canAct && o.status === 'PICKED' && (
                  <Button variant="soft" onClick={() => setDialog({ kind: 'ship', id: o.id })}>
                    {o.method === 'PICKUP' ? 'Kirim ke cabang' : 'Kirim JNE'}
                  </Button>
                )}
                {canAct && o.status === 'IN_TRANSIT' && (
                  <Button variant="soft" onClick={() => setDialog({ kind: 'receive', id: o.id })}>
                    Pindai tiba
                  </Button>
                )}
                {canAct && o.status === 'SHIPPED' && (
                  <Button variant="soft" onClick={() => act(() => api.completePrintOrder(actor, o.id), 'Pesanan selesai.')}>
                    Sudah diterima
                  </Button>
                )}
                {canAct && o.status === 'READY_PICKUP' && (
                  <Button variant="soft" onClick={() => setDialog({ kind: 'pickup', id: o.id })}>
                    Serahkan
                  </Button>
                )}
              </td>
            </tr>
          ))}
        </Table>
        {orders?.length === 0 && <Empty>Belum ada pesanan cetak.</Empty>}
      </Card>

      {current && dialog?.kind === 'pick' && (
        <ScanDialog
          key={current.status}
          title={`Picking pesanan #${current.id}`}
          done={current.items.filter((i) => i.picked).length}
          total={current.items.length}
          onClose={close}
          onScan={async (serial) => {
            const order = await act(() => api.pickPrintOrder(actor, current.id, serial), 'Seri cocok.')
            if (order && order.status !== 'WAITING') close()
          }}
        />
      )}

      {current && dialog?.kind === 'receive' && (
        <ScanDialog
          key={current.status}
          title={`Terima di ${current.destination.replace('Ambil di cabang ', '')}`}
          done={current.items.filter((i) => i.arrived).length}
          total={current.items.length}
          onClose={close}
          onScan={async (serial) => {
            const order = await act(() => api.receiveAtBranch(actor, current.id, serial), 'Keping tercatat tiba.')
            if (order && order.status !== 'IN_TRANSIT') close()
          }}
        />
      )}

      {current && dialog?.kind === 'ship' && (
        <Modal title={current.method === 'PICKUP' ? 'Kirim ke cabang' : 'Kirim dengan JNE'} onClose={close}>
          <form
            className="stack"
            onSubmit={async (e) => {
              e.preventDefault()
              const tracking = String(new FormData(e.currentTarget).get('tracking'))
              const done = await act(() => api.shipPrintOrder(actor, current.id, tracking), 'Pesanan dikirim.')
              if (done) close()
            }}
          >
            <Field label={current.method === 'PICKUP' ? 'Nomor pengiriman ke cabang' : 'Nomor resi JNE'}>
              <input name="tracking" required autoFocus />
            </Field>
            <Button type="submit">Catat pengiriman</Button>
          </form>
        </Modal>
      )}

      {current && dialog?.kind === 'pickup' && (
        <Modal title={`Serahkan pesanan #${current.id}`} onClose={close}>
          <form
            className="stack"
            onSubmit={async (e) => {
              e.preventDefault()
              const form = new FormData(e.currentTarget)
              const done = await act(
                () => api.completePickup(actor, current.id, String(form.get('code')), form.get('identity') === 'on'),
                'Keping diserahkan ke pengguna.',
              )
              if (done) close()
            }}
          >
            <p className="note">Pengguna: {current.owner}</p>
            <Field label="Kode pengambilan">
              <input name="code" required inputMode="numeric" autoComplete="off" autoFocus />
            </Field>
            <label className="check">
              <input name="identity" type="checkbox" /> KTP sudah diperiksa dan sesuai nama pengguna
            </label>
            <Button type="submit">Serahkan keping</Button>
          </form>
        </Modal>
      )}
    </>
  )
}

function progress(order: PrintOrder) {
  const total = order.items.length
  if (total < 2) return ''
  if (order.status === 'WAITING') return `${order.items.filter((i) => i.picked).length} dari ${total} dipindai`
  if (order.status === 'IN_TRANSIT') return `${order.items.filter((i) => i.arrived).length} dari ${total} tiba`
  return ''
}

function ScanDialog({
  title,
  done,
  total,
  onClose,
  onScan,
}: {
  title: string
  done: number
  total: number
  onClose: () => void
  onScan: (serial: string) => Promise<void>
}) {
  return (
    <Modal title={title} onClose={onClose}>
      <ScanInput onScan={onScan} />
      <div className="scan-total">
        <strong>{done}</strong> dari {total} keping
      </div>
    </Modal>
  )
}
