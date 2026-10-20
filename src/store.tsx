import AsyncStorage from '@react-native-async-storage/async-storage'
import React, { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react'

import type { Expense, Order, Product, Supplier, Table } from './data'
import {
  seedExpenses,
  seedOrders,
  seedProducts,
  seedSuppliers,
  seedTables,
} from './data'
import { createHubClient, HubError } from './hub/client.ts'
import type { HubSnapshot, HubSupplier, HubToday } from './hub/model.ts'
import { normalizeBaseUrl, parsePairingCode } from './hub/pairing.ts'
import type { Pairing } from './hub/pairing.ts'
import { loadQueue, saveQueue } from './hub/queue.ts'
import { drainQueue, pullSnapshot } from './hub/sync.ts'

export type ConnectionState = 'unpaired' | 'connecting' | 'online' | 'offline' | 'error'

export type Store = {
  products: Product[]
  setProducts: (updater: (current: Product[]) => Product[]) => void
  productAvailable: Record<string, boolean>
  tables: Table[]
  setTables: (updater: (current: Table[]) => Table[]) => void
  orders: Order[]
  setOrders: (updater: (current: Order[]) => Order[]) => void
  expenses: Expense[]
  setExpenses: (updater: (current: Expense[]) => Expense[]) => void
  suppliers: Supplier[]
  setSuppliers: (updater: (current: Supplier[]) => Supplier[]) => void
  hubSuppliers: HubSupplier[]
  name: string
  setName: (value: string) => void
  accent: string
  setAccent: (value: string) => void
  accent2: string
  setAccent2: (value: string) => void
  logo: string
  setLogo: (value: string) => void
  logoUrl: string | null
  dark: boolean
  setDark: (value: boolean) => void
  pairing: Pairing | null
  connection: ConnectionState
  lastSyncAt: string | null
  hubError: string | null
  pending: number
  hubToday: HubToday | null
  connect: (input: string | { baseUrl: string; token: string }) => Promise<void>
  disconnect: () => Promise<void>
  syncNow: () => Promise<void>
  ready: boolean
}

type Persisted = {
  products: Product[]
  productAvailable: Record<string, boolean>
  tables: Table[]
  orders: Order[]
  expenses: Expense[]
  suppliers: Supplier[]
  hubSuppliers: HubSupplier[]
  name: string
  accent: string
  accent2: string
  logo: string
  logoUrl: string | null
  dark: boolean
  lastSyncAt: string | null
  hubToday: HubToday | null
}

const STORAGE_KEY = 'mott:app-state:v1'
const LEGACY_STORAGE_KEY = 'mitt:app-state:v1'
const PAIRING_KEY = 'mott:pairing:v1'

const DEFAULT_AVAILABLE = (): Record<string, boolean> => {
  const map: Record<string, boolean> = {}
  for (const p of seedProducts) {
    map[p.id] = true
  }
  return map
}

const DEFAULTS = (): Persisted => ({
  products: seedProducts,
  productAvailable: DEFAULT_AVAILABLE(),
  tables: seedTables,
  orders: seedOrders,
  expenses: seedExpenses,
  suppliers: seedSuppliers,
  hubSuppliers: [],
  name: 'mitt',
  accent: '#0e7a5a',
  accent2: '#f2a33a',
  logo: '',
  logoUrl: null,
  dark: false,
  lastSyncAt: null,
  hubToday: null,
})

function isArray(value: unknown): value is unknown[] {
  return Array.isArray(value)
}

function isBooleanRecord(value: unknown): value is Record<string, boolean> {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return false
  for (const v of Object.values(value as Record<string, unknown>)) {
    if (typeof v !== 'boolean') return false
  }
  return true
}

function isHubToday(value: unknown): value is HubToday {
  if (!value || typeof value !== 'object') return false
  const candidate = value as Partial<HubToday>
  return (
    typeof candidate.date === 'string' &&
    typeof candidate.count === 'number' &&
    Number.isFinite(candidate.count) &&
    typeof candidate.totalCents === 'number' &&
    Number.isFinite(candidate.totalCents)
  )
}

function hydrate(raw: unknown): Persisted {
  const base = DEFAULTS()
  if (!raw || typeof raw !== 'object') return base
  const parsed = raw as Partial<Persisted>
  return {
    products: isArray(parsed.products) ? (parsed.products as Product[]) : base.products,
    productAvailable: isBooleanRecord(parsed.productAvailable)
      ? parsed.productAvailable
      : base.productAvailable,
    tables: isArray(parsed.tables)
      ? (parsed.tables as Table[]).map((t) => ({ ...t, hubTabId: t.hubTabId ?? null }))
      : base.tables,
    orders: isArray(parsed.orders) ? (parsed.orders as Order[]) : base.orders,
    expenses: isArray(parsed.expenses) ? (parsed.expenses as Expense[]) : base.expenses,
    suppliers: isArray(parsed.suppliers) ? (parsed.suppliers as Supplier[]) : base.suppliers,
    hubSuppliers: isArray(parsed.hubSuppliers)
      ? (parsed.hubSuppliers as HubSupplier[])
      : base.hubSuppliers,
    name: typeof parsed.name === 'string' ? parsed.name : base.name,
    accent: typeof parsed.accent === 'string' ? parsed.accent : base.accent,
    accent2: typeof parsed.accent2 === 'string' ? parsed.accent2 : base.accent2,
    logo: typeof parsed.logo === 'string' ? parsed.logo : base.logo,
    logoUrl: typeof parsed.logoUrl === 'string' ? parsed.logoUrl : null,
    dark: typeof parsed.dark === 'boolean' ? parsed.dark : base.dark,
    lastSyncAt: typeof parsed.lastSyncAt === 'string' ? parsed.lastSyncAt : null,
    hubToday: isHubToday(parsed.hubToday) ? parsed.hubToday : null,
  }
}

function hydratePairing(raw: string | null): Pairing | null {
  if (!raw) return null
  try {
    const parsed = JSON.parse(raw) as Partial<Pairing>
    if (!parsed || typeof parsed !== 'object') return null
    if (typeof parsed.baseUrl !== 'string' || typeof parsed.token !== 'string') return null
    const baseUrl = normalizeBaseUrl(parsed.baseUrl)
    const token = parsed.token.trim()
    if (token.length < 16) return null
    return { baseUrl, token }
  } catch {
    return null
  }
}

function buildCategoryMap(products: Product[]): Record<string, string> {
  const categories: Record<string, string> = {}
  for (const p of products) {
    if (p.id && p.category) {
      categories[p.id] = p.category
    }
  }
  return categories
}

function applySnapshotToState(current: Persisted, snapshot: HubSnapshot): Persisted {
  const productAvailable: Record<string, boolean> = {}
  const products: Product[] = snapshot.products.map((p) => {
    productAvailable[p.id] = p.available
    return {
      id: p.id,
      name: p.name,
      price: p.price,
      category: p.category || 'Carta',
    }
  })

  const tables: Table[] = snapshot.tables.map((t) => ({
    id: t.id,
    name: t.name,
    open: t.open,
    synced: true,
    lines: t.lines,
    hubTabId: t.tabId,
  }))

  const orders: Order[] = snapshot.orders.map((o) => ({
    id: o.id,
    table: o.table,
    hour: o.hour,
    items: o.items,
    total: o.total,
  }))

  const expenses: Expense[] = snapshot.expenses.map((e) => ({
    id: e.id,
    concept: e.concept,
    amount: e.amount,
    date: e.date,
  }))

  return {
    ...current,
    products,
    productAvailable,
    tables,
    orders,
    expenses,
    hubSuppliers: snapshot.suppliers,
    name: snapshot.branding.name || current.name,
    accent: snapshot.branding.accent || current.accent,
    accent2: snapshot.branding.accent2 || current.accent2,
    logoUrl: snapshot.branding.logoUrl,
    hubToday: snapshot.today,
    lastSyncAt: new Date().toISOString(),
  }
}

const StoreContext = createContext<Store | null>(null)

export function StoreProvider({ children }: { children: React.ReactNode }) {
  const [state, setState] = useState<Persisted>(DEFAULTS)
  const [pairing, setPairing] = useState<Pairing | null>(null)
  const [connection, setConnection] = useState<ConnectionState>('unpaired')
  const [hubError, setHubError] = useState<string | null>(null)
  const [pendingCount, setPendingCount] = useState<number>(0)
  const [ready, setReady] = useState(false)

  const stateRef = useRef<Persisted>(state)
  stateRef.current = state

  const pairingRef = useRef<Pairing | null>(pairing)
  pairingRef.current = pairing

  // Writes are coalesced (one pending write at a time) so rapid taps do not
  // queue dozens of whole-state writes.
  const pendingWrite = useRef<Persisted | null>(null)
  const writing = useRef(false)
  const flush = useCallback(async () => {
    if (writing.current) return
    writing.current = true
    try {
      while (pendingWrite.current) {
        const next = pendingWrite.current
        pendingWrite.current = null
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
      pendingWrite.current = next
      void flush()
    },
    [flush],
  )

  const commitState = useCallback(
    (updater: (current: Persisted) => Persisted) => {
      setState((current) => {
        const next = updater(current)
        stateRef.current = next
        persist(next)
        return next
      })
    },
    [persist],
  )

  const update = useCallback(
    (patch: Partial<Persisted>) => {
      setState((current) => {
        const next = { ...current, ...patch }
        stateRef.current = next
        if (ready) persist(next)
        return next
      })
    },
    [persist, ready],
  )

  const syncWithPairing = useCallback(
    async (activePairing: Pairing) => {
      setConnection('connecting')
      setHubError(null)
      const client = createHubClient(activePairing)
      try {
        const categories = buildCategoryMap(stateRef.current.products)
        let snapshot = await pullSnapshot(client, categories)

        const queued = await loadQueue()
        let remaining = queued
        if (queued.length > 0) {
          const { report, ops } = await drainQueue(client, queued)
          remaining = ops
          await saveQueue(remaining)
          setPendingCount(remaining.length)

          if (report.synced > 0) {
            const refreshedCategories = buildCategoryMap(stateRef.current.products)
            snapshot = await pullSnapshot(client, refreshedCategories)
          }

          if (report.deferred > 0) {
            commitState((current) => applySnapshotToState(current, snapshot))
            setConnection('offline')
            setHubError('No se pudo conectar')
            return
          }
        } else {
          setPendingCount(0)
        }

        commitState((current) => applySnapshotToState(current, snapshot))
        setConnection('online')
        setHubError(null)
      } catch (err) {
        const queued = await loadQueue()
        setPendingCount(queued.length)
        if (err instanceof HubError && (err.status === 401 || err.status === 403)) {
          setConnection('error')
          setHubError('Token inválido')
        } else if (err instanceof HubError) {
          setConnection('error')
          setHubError(err.message || `Error HTTP ${err.status}`)
        } else {
          setConnection('offline')
          setHubError('No se pudo conectar')
        }
      }
    },
    [commitState],
  )

  useEffect(() => {
    let alive = true
    Promise.all([
      AsyncStorage.getItem(STORAGE_KEY),
      AsyncStorage.getItem(LEGACY_STORAGE_KEY),
      AsyncStorage.getItem(PAIRING_KEY),
      loadQueue(),
    ])
      .then(([storedState, legacyState, storedPairing, queuedOps]) => {
        if (!alive) return
        const rawState = storedState ?? legacyState
        if (rawState) {
          try {
            const hydrated = hydrate(JSON.parse(rawState))
            stateRef.current = hydrated
            setState(hydrated)
          } catch {
            // Unreadable state keeps seeds.
          }
        }

        setPendingCount(queuedOps.length)

        const validPairing = hydratePairing(storedPairing)
        if (validPairing) {
          pairingRef.current = validPairing
          setPairing(validPairing)
          setConnection('connecting')
          void syncWithPairing(validPairing)
        } else {
          pairingRef.current = null
          setPairing(null)
          setConnection('unpaired')
        }
      })
      .catch(() => {
        // Unreadable storage keeps seeds and unpaired state.
      })
      .finally(() => {
        if (alive) setReady(true)
      })
    return () => {
      alive = false
    }
  }, [syncWithPairing])

  const connect = useCallback(
    async (input: string | { baseUrl: string; token: string }) => {
      let parsed: Pairing
      try {
        if (typeof input === 'string') {
          parsed = parsePairingCode(input)
        } else {
          const baseUrl = normalizeBaseUrl(input.baseUrl)
          const token = input.token.trim()
          if (token.length < 16) {
            throw new Error('Pairing token must be at least 16 characters')
          }
          parsed = { baseUrl, token }
        }
      } catch (err) {
        setConnection('error')
        const message = err instanceof Error ? err.message : ''
        if (message.includes('16')) {
          setHubError('El token debe tener al menos 16 caracteres')
        } else {
          setHubError('Código o dirección inválida')
        }
        return
      }

      setConnection('connecting')
      setHubError(null)

      const client = createHubClient(parsed)
      try {
        await client.pairing()
      } catch (err) {
        if (err instanceof HubError && (err.status === 401 || err.status === 403)) {
          setConnection('error')
          setHubError('Token inválido')
        } else if (err instanceof HubError) {
          setConnection('error')
          setHubError(err.message || `Error HTTP ${err.status}`)
        } else {
          setConnection('offline')
          setHubError('No se pudo conectar')
        }
        return
      }

      try {
        await AsyncStorage.setItem(PAIRING_KEY, JSON.stringify(parsed))
      } catch {
        // Keep in-memory pairing even if storage fails
      }
      pairingRef.current = parsed
      setPairing(parsed)

      await syncWithPairing(parsed)
    },
    [syncWithPairing],
  )

  const disconnect = useCallback(async () => {
    try {
      await AsyncStorage.removeItem(PAIRING_KEY)
    } catch {
      // Ignore storage error
    }
    pairingRef.current = null
    setPairing(null)
    setConnection('unpaired')
    setHubError(null)
    commitState((current) => ({
      ...current,
      lastSyncAt: null,
      hubToday: null,
      logoUrl: null,
    }))
  }, [commitState])

  const syncNow = useCallback(async () => {
    const activePairing = pairingRef.current
    if (!activePairing) return
    await syncWithPairing(activePairing)
  }, [syncWithPairing])

  const store = useMemo<Store>(
    () => ({
      ...state,
      pairing,
      connection,
      hubError,
      pending: pendingCount,
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
      connect,
      disconnect,
      syncNow,
    }),
    [state, pairing, connection, hubError, pendingCount, ready, update, connect, disconnect, syncNow],
  )

  return <StoreContext.Provider value={store}>{children}</StoreContext.Provider>
}

export function useStore(): Store {
  const store = useContext(StoreContext)
  if (!store) throw new Error('useStore must be used inside StoreProvider')
  return store
}
