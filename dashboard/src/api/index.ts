import { MockDb } from './core'
import { createSeed } from './seed'
import type { DbState } from './types'

const STORAGE_KEY = 'reidig-dashboard-db-v4'

function load(): DbState {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (raw) return JSON.parse(raw) as DbState
  } catch {
    // Penyimpanan tidak tersedia: pakai data awal.
  }
  return createSeed()
}

const db = new MockDb(load())
const listeners = new Set<() => void>()

export function onDataChange(listener: () => void) {
  listeners.add(listener)
  return () => {
    listeners.delete(listener)
  }
}

function changed() {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(db.state))
  } catch {
    // Data tetap berjalan di memori.
  }
  listeners.forEach((listener) => listener())
}

type AsyncApi = {
  [K in keyof MockDb as MockDb[K] extends (...args: never[]) => unknown ? K : never]: MockDb[K] extends (
    ...args: infer A
  ) => infer R
    ? (...args: A) => Promise<R>
    : never
}

/** Satu-satunya pintu ke data. Saat backend siap, ganti isi objek ini dengan panggilan HTTP. */
export const api = new Proxy({} as AsyncApi, {
  get(_target, name: string) {
    return async (...args: unknown[]) => {
      const method = (db as unknown as Record<string, (...a: unknown[]) => unknown>)[name]
      const result = method.apply(db, args)
      const snapshot = result === undefined ? undefined : structuredClone(result)
      if (!/^(list|get|check)/.test(name)) changed()
      return snapshot
    }
  },
})

export function resetDemoData() {
  db.state = createSeed()
  changed()
}
