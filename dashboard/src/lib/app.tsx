import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import type { DependencyList, ReactNode } from 'react'
import { onDataChange } from '../api'
import { USERS } from '../api/seed'
import type { ApiError, User } from '../api/types'

const USER_KEY = 'emdig-dashboard-user'

function initialUser() {
  try {
    const saved = localStorage.getItem(USER_KEY)
    return USERS.find((u) => u.id === saved) ?? USERS[2]
  } catch {
    return USERS[2]
  }
}

interface Toast {
  id: number
  tone: 'good' | 'critical'
  text: string
}

interface AppContextValue {
  actor: User
  setActor: (id: string) => void
  /** Menjalankan aksi dan menampilkan hasilnya. Mengembalikan undefined kalau gagal. */
  act: <T>(fn: () => Promise<T>, success?: string) => Promise<T | undefined>
}

const AppContext = createContext<AppContextValue | null>(null)

export function AppProvider({ children }: { children: ReactNode }) {
  const [actor, setActorState] = useState<User>(initialUser)
  const [toasts, setToasts] = useState<Toast[]>([])

  const push = useCallback((tone: Toast['tone'], text: string) => {
    const id = Date.now() + Math.random()
    setToasts((list) => [...list, { id, tone, text }])
    setTimeout(() => setToasts((list) => list.filter((t) => t.id !== id)), 4500)
  }, [])

  const value = useMemo<AppContextValue>(
    () => ({
      actor,
      setActor: (id) => {
        setActorState(USERS.find((u) => u.id === id)!)
        try {
          localStorage.setItem(USER_KEY, id)
        } catch {
          // Pilihan peran tidak tersimpan, tetap berjalan.
        }
      },
      act: async (fn, success) => {
        try {
          const result = await fn()
          if (success) push('good', success)
          return (result ?? (true as unknown)) as never
        } catch (e) {
          push('critical', (e as ApiError).message)
          return undefined
        }
      },
    }),
    [actor, push],
  )

  return (
    <AppContext.Provider value={value}>
      {children}
      <div className="toasts" role="status" aria-live="polite">
        {toasts.map((t) => (
          <div key={t.id} className={`toast toast-${t.tone}`}>
            {t.text}
          </div>
        ))}
      </div>
    </AppContext.Provider>
  )
}

export function useApp() {
  const value = useContext(AppContext)
  if (!value) throw new Error('useApp harus di dalam AppProvider')
  return value
}

/** Memuat data dan memuat ulang setiap kali data berubah. */
export function useData<T>(fn: () => Promise<T>, deps: DependencyList = []) {
  const [data, setData] = useState<T>()
  useEffect(() => {
    let alive = true
    const load = () => {
      fn().then((d) => {
        if (alive) setData(d)
      })
    }
    load()
    const off = onDataChange(load)
    return () => {
      alive = false
      off()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps)
  return data
}

export const rupiah = (value: number) => 'Rp ' + value.toLocaleString('id-ID')
export const gram = (mg: number) => `${(mg / 1000).toLocaleString('id-ID')} gr`
export const dateTime = (iso: string) =>
  new Date(iso).toLocaleString('id-ID', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit', timeZone: 'Asia/Jakarta' }) + ' WIB'
