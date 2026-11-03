import { useEffect, useRef, useState } from 'react'
import type { ButtonHTMLAttributes, ComponentType, FormEvent, ReactNode } from 'react'
import { AlertOctagon, AlertTriangle, CheckCircle2, Circle, Clock, ScanLine, X } from 'lucide-react'

export type Tone = 'good' | 'warning' | 'critical' | 'info' | 'neutral'

const TONE_ICONS: Record<Tone, ComponentType<{ size?: number }>> = {
  good: CheckCircle2,
  warning: AlertTriangle,
  critical: AlertOctagon,
  info: Clock,
  neutral: Circle,
}

export function Badge({ tone, children }: { tone: Tone; children: ReactNode }) {
  const Icon = TONE_ICONS[tone]
  return (
    <span className={`badge badge-${tone}`}>
      <Icon size={13} />
      {children}
    </span>
  )
}

export function PageHeader({ title, subtitle, actions }: { title: string; subtitle?: string; actions?: ReactNode }) {
  return (
    <header className="page-header">
      <div>
        <h1>{title}</h1>
        {subtitle && <p>{subtitle}</p>}
      </div>
      {actions && <div className="row">{actions}</div>}
    </header>
  )
}

export function Card({ title, actions, children, flush }: { title?: string; actions?: ReactNode; children: ReactNode; flush?: boolean }) {
  return (
    <section className={`card ${flush ? 'card-flush' : ''}`}>
      {(title || actions) && (
        <div className="card-head">
          {title && <h2>{title}</h2>}
          {actions}
        </div>
      )}
      {children}
    </section>
  )
}

type IconTint = 'violet' | 'pink' | 'sky' | 'gold'

export function StatTile({
  icon: Icon,
  tint = 'violet',
  value,
  label,
}: {
  icon: ComponentType<{ size?: number }>
  tint?: IconTint
  value: ReactNode
  label: string
}) {
  return (
    <div className="stat">
      <span className={`stat-icon tint-${tint}`}>
        <Icon size={22} />
      </span>
      <div>
        <div className="stat-label">{label}</div>
        <div className="stat-value">{value}</div>
      </div>
    </div>
  )
}

export function Button({
  variant = 'primary',
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: 'primary' | 'soft' | 'ghost' | 'danger' | 'dark' }) {
  return <button type="button" {...props} className={`btn btn-${variant} ${props.className ?? ''}`} />
}

export function Field({ label, hint, children }: { label: string; hint?: string; children: ReactNode }) {
  return (
    <label className="field">
      <span className="field-label">{label}</span>
      {children}
      {hint && <span className="field-hint">{hint}</span>}
    </label>
  )
}

export function Modal({ title, onClose, children }: { title: string; onClose: () => void; children: ReactNode }) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose()
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])
  return (
    <div className="modal-backdrop" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div className="modal" role="dialog" aria-modal="true" aria-label={title}>
        <div className="card-head">
          <h2>{title}</h2>
          <button type="button" className="icon-btn" aria-label="Tutup" onClick={onClose}>
            <X size={18} />
          </button>
        </div>
        {children}
      </div>
    </div>
  )
}

/** Input untuk scanner USB/Bluetooth: scanner mengetik seri lalu menekan Enter. */
export function ScanInput({
  onScan,
  placeholder = 'Pindai barcode keping atau ketik nomor seri...',
  autoFocus = true,
}: {
  onScan: (serial: string) => void | Promise<unknown>
  placeholder?: string
  autoFocus?: boolean
}) {
  const [value, setValue] = useState('')
  const ref = useRef<HTMLInputElement>(null)
  const submit = async (e: FormEvent) => {
    e.preventDefault()
    if (!value.trim()) return
    await onScan(value)
    setValue('')
    ref.current?.focus()
  }
  return (
    <form className="scan" onSubmit={submit}>
      <ScanLine size={20} />
      <input
        ref={ref}
        autoFocus={autoFocus}
        value={value}
        onChange={(e) => setValue(e.target.value)}
        placeholder={placeholder}
        aria-label={placeholder}
        autoComplete="off"
        spellCheck={false}
      />
      <Button type="submit" variant="dark">
        Catat Keping
      </Button>
    </form>
  )
}

export function Empty({ children }: { children: ReactNode }) {
  return <div className="empty">{children}</div>
}

export function Table({ head, children }: { head: string[]; children: ReactNode }) {
  return (
    <div className="table-wrap">
      <table>
        <thead>
          <tr>
            {head.map((h, i) => (
              <th key={i}>{h}</th>
            ))}
          </tr>
        </thead>
        <tbody>{children}</tbody>
      </table>
    </div>
  )
}
