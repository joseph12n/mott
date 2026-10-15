import AsyncStorage from '@react-native-async-storage/async-storage'
import React, { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react'

import {
  Expense,
  Order,
  Product,
  Supplier,
  Table,
  seedExpenses,
  seedOrders,
  seedProducts,
  seedSuppliers,
  seedTables,
} from './data'

// The phone owns its data (no PC required): the store mirrors the web
// master's shape exactly — same updater signatures the views already use —
// and persists the whole object to AsyncStorage instead of localStorage.
// Every field is validated on load so a truncated or older payload degrades
// to the seeds instead of crashing the app.

export type Store = {
  products: Product[]
  setProducts: (updater: (current: Product[]) => Product[]) => void
  tables: Table[]
  setTables: (updater: (current: Table[]) => Table[]) => void
  orders: Order[]
  setOrders: (updater: (current: Order[]) => Order[]) => void
  expenses: Expense[]
  setExpenses: (updater: (current: Expense[]) => Expense[]) => void
  suppliers: Supplier[]
  setSuppliers: (updater: (current: Supplier[]) => Supplier[]) => void
  name: string
  setName: (value: string) => void
  accent: string
  setAccent: (value: string) => void
  accent2: string
  setAccent2: (value: string) => void
  logo: string
  setLogo: (value: string) => void
  dark: boolean
  setDark: (value: boolean) => void
  ready: boolean
}

type Persisted = {
  products: Product[]
  tables: Table[]
  orders: Order[]
  expenses: Expense[]
  suppliers: Supplier[]
  name: string
  accent: string
  accent2: string
  logo: string
  dark: boolean
}

const STORAGE_KEY = 'mitt:app-state:v1'

const DEFAULTS = (): Persisted => ({
  products: seedProducts,
  tables: seedTables,
  orders: seedOrders,
  expenses: seedExpenses,
  suppliers: seedSuppliers,
  name: 'mitt',
  accent: '#0e7a5a',
  accent2: '#f2a33a',
  logo: '',
  dark: false,
})

function isArray(value: unknown): value is unknown[] {
  return Array.isArray(value)
}

// Defensive load: anything unexpected falls back to the seed for that field,
// so one bad field never blanks the whole app.
function hydrate(raw: unknown): Persisted {
  const base = DEFAULTS()
  if (!raw || typeof raw !== 'object') return base
  const parsed = raw as Partial<Persisted>
  return {
    products: isArray(parsed.products) ? (parsed.products as Product[]) : base.products,
    tables: isArray(parsed.tables) ? (parsed.tables as Table[]) : base.tables,
    orders: isArray(parsed.orders) ? (parsed.orders as Order[]) : base.orders,
    expenses: isArray(parsed.expenses) ? (parsed.expenses as Expense[]) : base.expenses,
    suppliers: isArray(parsed.suppliers) ? (parsed.suppliers as Supplier[]) : base.suppliers,
    name: typeof parsed.name === 'string' ? parsed.name : base.name,
    accent: typeof parsed.accent === 'string' ? parsed.accent : base.accent,
    accent2: typeof parsed.accent2 === 'string' ? parsed.accent2 : base.accent2,
    logo: typeof parsed.logo === 'string' ? parsed.logo : base.logo,
    dark: typeof parsed.dark === 'boolean' ? parsed.dark : base.dark,
  }
}

const StoreContext = createContext<Store | null>(null)

export function StoreProvider({ children }: { children: React.ReactNode }) {
  const [state, setState] = useState<Persisted>(DEFAULTS)
  const [ready, setReady] = useState(false)

  useEffect(() => {
    let alive = true
    AsyncStorage.getItem(STORAGE_KEY)
      .then((stored) => {
        if (!alive || !stored) return
        setState(hydrate(JSON.parse(stored)))
      })
      .catch(() => {
        // Unreadable storage keeps the seeds; the app still runs.
      })
      .finally(() => {
        if (alive) setReady(true)
      })
    return () => {
      alive = false
    }
  }, [])

  // Writes are coalesced (one pending write at a time) so rapid taps do not
  // queue dozens of whole-state writes.
  const pending = useRef<Persisted | null>(null)
  const writing = useRef(false)
  const flush = useCallback(async () => {
    if (writing.current) return
    writing.current = true
    try {
      while (pending.current) {
        const next = pending.current
        pending.current = null
        await AsyncStorage.setItem(STORAGE_KEY, JSON.stringify(next))
      }
    } catch {
      // A failed write must not break the UI; the in-memory state stands.
    } finally {
      writing.current = false
    }
  }, [])

  const persist = useCallback(
    (next: Persisted) => {
      pending.current = next
      void flush()
    },
    [flush],
  )

  const update = useCallback(
    (patch: Partial<Persisted>) => {
      setState((current) => {
        const next = { ...current, ...patch }
        if (ready) persist(next)
        return next
      })
    },
    [persist, ready],
  )

  const store = useMemo<Store>(
    () => ({
      ...state,
      ready,
      setProducts: (updater) => update({ products: updater(state.products) }),
      setTables: (updater) => update({ tables: updater(state.tables) }),
      setOrders: (updater) => update({ orders: updater(state.orders) }),
      setExpenses: (updater) => update({ expenses: updater(state.expenses) }),
      setSuppliers: (updater) => update({ suppliers: updater(state.suppliers) }),
      setName: (value) => update({ name: value }),
      setAccent: (value) => update({ accent: value }),
      setAccent2: (value) => update({ accent2: value }),
      setLogo: (value) => update({ logo: value }),
      setDark: (value) => update({ dark: value }),
    }),
    [state, ready, update],
  )

  return <StoreContext.Provider value={store}>{children}</StoreContext.Provider>
}

export function useStore(): Store {
  const store = useContext(StoreContext)
  if (!store) throw new Error('useStore must be used inside StoreProvider')
  return store
}
