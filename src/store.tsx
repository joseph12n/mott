import AsyncStorage from '@react-native-async-storage/async-storage'
import React, { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react'

import {
  Expense,
  Line,
  Order,
  Product,
  Supplier,
  Table,
  seedExpenses,
  seedOrders,
  seedProducts,
  seedSuppliers,
  seedTables,
  uid,
} from './data'
import { createHubClient, HubError } from './hub/client.ts'
import { fromCents, mapBranding, mapExpenses, mapSales, mapSuppliers, toCents } from './hub/mappers.ts'
import type { HubSnapshot, HubSupplier, HubToday } from './hub/model.ts'
import { normalizeBaseUrl, parsePairingCode } from './hub/pairing.ts'
import type { Pairing } from './hub/pairing.ts'
import { enqueue, enqueueCloseTab, enqueueSaveTab, loadQueue, saveQueue } from './hub/queue.ts'
import { drainQueue, pullSnapshot } from './hub/sync.ts'
import type { BrandingPatchRequest, SupplierPatchRequest } from './hub/types.ts'

export type ConnectionState = 'unpaired' | 'connecting' | 'online' | 'offline' | 'error'

export type Store = {
  products: Product[]
  setProducts: (updater: (current: Product[]) => Product[]) => void
  productAvailable: Record<string, boolean>
  productCategories: Record<string, string>
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
  createTable: (label: string) => Promise<boolean>
  deleteTable: (tableId: string) => Promise<boolean>
  openTable: (tableId: string) => Promise<boolean>
  addItem: (tableId: string, productId: string, qty: number) => Promise<boolean>
  closeTable: (tableId: string) => Promise<boolean>
  createProduct: (name: string, price: number, category: string) => Promise<boolean>
  setProductAvailable: (id: string, available: boolean) => Promise<boolean>
  addExpense: (concept: string, amount: number) => Promise<boolean>
  createSupplier: (name: string, phone: string, note: string) => Promise<boolean>
  updateSupplier: (
    id: string,
    patch: { name?: string; phone?: string; note?: string },
  ) => Promise<boolean>
  deleteSupplier: (id: string) => Promise<boolean>
  saveBranding: (patch: { name?: string; accent?: string; accent2?: string }) => Promise<boolean>
  ready: boolean
}

type Persisted = {
  products: Product[]
  productAvailable: Record<string, boolean>
  productCategories: Record<string, string>
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

const DEFAULT_CATEGORIES = (): Record<string, string> => {
  const map: Record<string, string> = {}
  for (const p of seedProducts) {
    map[p.id] = p.category
  }
  return map
}

const DEFAULTS = (): Persisted => ({
  products: seedProducts,
  productAvailable: DEFAULT_AVAILABLE(),
  productCategories: DEFAULT_CATEGORIES(),
  tables: seedTables,
  orders: seedOrders,
  expenses: seedExpenses,
  suppliers: seedSuppliers,
  hubSuppliers: seedSuppliers,
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

function isStringRecord(value: unknown): value is Record<string, string> {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return false
  for (const v of Object.values(value as Record<string, unknown>)) {
    if (typeof v !== 'string') return false
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

function normalizeSuppliers(raw: unknown, fallback: Supplier[]): Supplier[] {
  if (!Array.isArray(raw)) return fallback
  const out: Supplier[] = []
  for (const item of raw) {
    if (!item || typeof item !== 'object') continue
    const rec = item as Record<string, unknown>
    if (typeof rec.id !== 'string' || typeof rec.name !== 'string') continue
    const phone = typeof rec.phone === 'string' ? rec.phone : ''
    const note =
      typeof rec.note === 'string'
        ? rec.note
        : typeof rec.category === 'string'
          ? rec.category
          : ''
    out.push({ id: rec.id, name: rec.name, phone, note })
  }
  return out
}

function hydrate(raw: unknown): Persisted {
  const base = DEFAULTS()
  if (!raw || typeof raw !== 'object') return base
  const parsed = raw as Partial<Persisted>
  const products = isArray(parsed.products) ? (parsed.products as Product[]) : base.products
  const productCategories = isStringRecord(parsed.productCategories)
    ? { ...buildCategoryMap(products), ...parsed.productCategories }
    : buildCategoryMap(products)
  const suppliers = normalizeSuppliers(parsed.suppliers, base.suppliers)
  return {
    products,
    productAvailable: isBooleanRecord(parsed.productAvailable)
      ? parsed.productAvailable
      : base.productAvailable,
    productCategories,
    tables: isArray(parsed.tables)
      ? (parsed.tables as Table[]).map((t) => ({ ...t, hubTabId: t.hubTabId ?? null }))
      : base.tables,
    orders: isArray(parsed.orders) ? (parsed.orders as Order[]) : base.orders,
    expenses: isArray(parsed.expenses) ? (parsed.expenses as Expense[]) : base.expenses,
    suppliers,
    hubSuppliers: isArray(parsed.hubSuppliers)
      ? normalizeSuppliers(parsed.hubSuppliers, suppliers)
      : suppliers,
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

function buildCategoryMap(
  products: Product[],
  existing?: Record<string, string>,
): Record<string, string> {
  const categories: Record<string, string> = { ...(existing ?? {}) }
  for (const p of products) {
    if (p.id && p.category) {
      categories[p.id] = p.category
    }
  }
  return categories
}

function mergeLines(lines: Line[], productId: string, qty: number): Line[] {
  const existing = lines.find((l) => l.productId === productId)
  if (existing) {
    return lines.map((l) => (l.productId === productId ? { ...l, qty: l.qty + qty } : l))
  }
  return [...lines, { productId, qty }]
}

function buildLocalOrder(table: Table, products: Product[]): Order {
  const priceOf = (id: string) => products.find((p) => p.id === id)?.price ?? 0
  const nameOf = (id: string) => products.find((p) => p.id === id)?.name ?? 'Producto'
  const items = table.lines.map((l) => ({
    name: nameOf(l.productId),
    qty: l.qty,
    price: priceOf(l.productId),
  }))
  const total = items.reduce((sum, it) => sum + it.qty * it.price, 0)
  return {
    id: uid(),
    table: table.name,
    hour: new Date().getHours(),
    items,
    total,
  }
}

function applySnapshotToState(current: Persisted, snapshot: HubSnapshot): Persisted {
  const byNameCategory = new Map<string, string>()
  for (const p of current.products) {
    if (p.name && p.category) {
      byNameCategory.set(p.name.trim().toLowerCase(), p.category)
    }
  }

  const productAvailable: Record<string, boolean> = {}
  const productCategories: Record<string, string> = { ...current.productCategories }

  const products: Product[] = snapshot.products.map((p) => {
    productAvailable[p.id] = p.available
    const resolvedCategory =
      current.productCategories[p.id] ||
      (p.category && p.category !== 'Carta' ? p.category : '') ||
      byNameCategory.get(p.name.trim().toLowerCase()) ||
      'Carta'
    productCategories[p.id] = resolvedCategory
    return {
      id: p.id,
      name: p.name,
      price: p.price,
      category: resolvedCategory,
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

  const suppliers: Supplier[] = snapshot.suppliers.map((s) => ({
    id: s.id,
    name: s.name,
    phone: s.phone,
    note: s.note,
  }))

  return {
    ...current,
    products,
    productAvailable,
    productCategories,
    tables,
    orders,
    expenses,
    suppliers,
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

  const connectionRef = useRef<ConnectionState>(connection)
  connectionRef.current = connection

  const updateConnection = useCallback((next: ConnectionState) => {
    connectionRef.current = next
    setConnection(next)
  }, [])

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
      updateConnection('connecting')
      setHubError(null)
      const client = createHubClient(activePairing)
      try {
        const categories = buildCategoryMap(
          stateRef.current.products,
          stateRef.current.productCategories,
        )
        let snapshot = await pullSnapshot(client, categories)

        const queued = await loadQueue()
        let remaining = queued
        if (queued.length > 0) {
          const { report, ops } = await drainQueue(client, queued)
          remaining = ops
          await saveQueue(remaining)
          setPendingCount(remaining.length)

          if (report.synced > 0) {
            const refreshedCategories = buildCategoryMap(
              stateRef.current.products,
              stateRef.current.productCategories,
            )
            snapshot = await pullSnapshot(client, refreshedCategories)
          }

          if (report.deferred > 0) {
            commitState((current) => applySnapshotToState(current, snapshot))
            updateConnection('offline')
            setHubError('No se pudo conectar')
            return
          }
        } else {
          setPendingCount(0)
        }

        commitState((current) => applySnapshotToState(current, snapshot))
        updateConnection('online')
        setHubError(null)
      } catch (err) {
        const queued = await loadQueue()
        setPendingCount(queued.length)
        if (err instanceof HubError && (err.status === 401 || err.status === 403)) {
          updateConnection('error')
          setHubError('Token inválido')
        } else if (err instanceof HubError) {
          updateConnection('error')
          setHubError(err.message || `Error HTTP ${err.status}`)
        } else {
          updateConnection('offline')
          setHubError('No se pudo conectar')
        }
      }
    },
    [commitState, updateConnection],
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
          updateConnection('connecting')
          void syncWithPairing(validPairing)
        } else {
          pairingRef.current = null
          setPairing(null)
          updateConnection('unpaired')
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
  }, [syncWithPairing, updateConnection])

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
        updateConnection('error')
        const message = err instanceof Error ? err.message : ''
        if (message.includes('16')) {
          setHubError('El token debe tener al menos 16 caracteres')
        } else {
          setHubError('Código o dirección inválida')
        }
        return
      }

      updateConnection('connecting')
      setHubError(null)

      const client = createHubClient(parsed)
      try {
        await client.pairing()
      } catch (err) {
        if (err instanceof HubError && (err.status === 401 || err.status === 403)) {
          updateConnection('error')
          setHubError('Token inválido')
        } else if (err instanceof HubError) {
          updateConnection('error')
          setHubError(err.message || `Error HTTP ${err.status}`)
        } else {
          updateConnection('offline')
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
    [syncWithPairing, updateConnection],
  )

  const disconnect = useCallback(async () => {
    try {
      await AsyncStorage.removeItem(PAIRING_KEY)
    } catch {
      // Ignore storage error
    }
    pairingRef.current = null
    setPairing(null)
    updateConnection('unpaired')
    setHubError(null)
    commitState((current) => ({
      ...current,
      lastSyncAt: null,
      hubToday: null,
      logoUrl: null,
    }))
  }, [commitState, updateConnection])

  const syncNow = useCallback(async () => {
    const activePairing = pairingRef.current
    if (!activePairing) return
    await syncWithPairing(activePairing)
  }, [syncWithPairing])

  const queueSaveTabOffline = useCallback(async (tableId: string, lines: Line[]) => {
    const queued = await loadQueue()
    const nextQueue = enqueueSaveTab(queued, { tableId, lines })
    await saveQueue(nextQueue)
    setPendingCount(nextQueue.length)
    setHubError(null)
  }, [])

  const queueCloseTabOffline = useCallback(async (tableId: string, lines: Line[]) => {
    const queued = await loadQueue()
    const nextQueue = enqueueCloseTab(queued, { tableId, lines })
    await saveQueue(nextQueue)
    setPendingCount(nextQueue.length)
    setHubError(null)
  }, [])

  const createTable = useCallback(
    async (label: string): Promise<boolean> => {
      const clean = label.trim()
      if (!clean) return false

      const activePairing = pairingRef.current
      const mode = connectionRef.current

      if (mode === 'unpaired' || !activePairing) {
        const localTable: Table = {
          id: uid(),
          name: clean,
          open: false,
          synced: false,
          lines: [],
          hubTabId: null,
        }
        commitState((current) => ({
          ...current,
          tables: [...current.tables, localTable],
        }))
        setHubError(null)
        return true
      }

      if (mode !== 'online') {
        setHubError('Necesitás conexión con la PC')
        return false
      }

      const client = createHubClient(activePairing)
      try {
        const dto = await client.createTable({ label: clean })
        const hubTable: Table = {
          id: dto.id,
          name: dto.label,
          open: Boolean(dto.occupied),
          synced: true,
          lines: [],
          hubTabId: null,
        }
        commitState((current) => ({
          ...current,
          tables: [...current.tables, hubTable],
          lastSyncAt: new Date().toISOString(),
        }))
        setHubError(null)
        return true
      } catch (err) {
        if (err instanceof HubError && err.status >= 400 && err.status < 500) {
          setHubError(err.message || 'No se pudo crear la mesa')
          return false
        }
        updateConnection('offline')
        setHubError('Necesitás conexión con la PC')
        return false
      }
    },
    [commitState, updateConnection],
  )

  const deleteTable = useCallback(
    async (tableId: string): Promise<boolean> => {
      const activePairing = pairingRef.current
      const mode = connectionRef.current

      if (mode === 'unpaired' || !activePairing) {
        const target = stateRef.current.tables.find((t) => t.id === tableId)
        if (target?.open) {
          setHubError('La mesa tiene una cuenta abierta')
          return false
        }
        commitState((current) => ({
          ...current,
          tables: current.tables.filter((t) => t.id !== tableId),
        }))
        setHubError(null)
        return true
      }

      if (mode !== 'online') {
        setHubError('Necesitás conexión con la PC')
        return false
      }

      const client = createHubClient(activePairing)
      try {
        await client.deleteTable(tableId)
        commitState((current) => ({
          ...current,
          tables: current.tables.filter((t) => t.id !== tableId),
          lastSyncAt: new Date().toISOString(),
        }))
        setHubError(null)
        return true
      } catch (err) {
        if (err instanceof HubError && err.status === 409) {
          setHubError('La mesa tiene una cuenta abierta')
          return false
        }
        if (err instanceof HubError && err.status >= 400 && err.status < 500) {
          setHubError(err.message || 'No se pudo eliminar la mesa')
          return false
        }
        updateConnection('offline')
        setHubError('Necesitás conexión con la PC')
        return false
      }
    },
    [commitState, updateConnection],
  )

  const openTable = useCallback(
    async (tableId: string): Promise<boolean> => {
      const activePairing = pairingRef.current
      const mode = connectionRef.current

      if (mode === 'unpaired' || !activePairing) {
        commitState((current) => ({
          ...current,
          tables: current.tables.map((t) => (t.id === tableId ? { ...t, open: true } : t)),
        }))
        setHubError(null)
        return true
      }

      const existing = stateRef.current.tables.find((t) => t.id === tableId)
      const currentLines = existing?.lines ?? []

      if (mode !== 'online') {
        commitState((current) => ({
          ...current,
          tables: current.tables.map((t) =>
            t.id === tableId ? { ...t, open: true, synced: false } : t,
          ),
        }))
        await queueSaveTabOffline(tableId, currentLines)
        return false
      }

      const client = createHubClient(activePairing)
      try {
        const tab = await client.openTab({ table_id: tableId })
        const hubLines: Line[] = (tab.items ?? []).map((it) => ({
          productId: it.product_id,
          qty: it.qty,
        }))
        commitState((current) => ({
          ...current,
          tables: current.tables.map((t) =>
            t.id === tableId
              ? { ...t, open: true, synced: true, hubTabId: tab.id, lines: hubLines }
              : t,
          ),
          lastSyncAt: new Date().toISOString(),
        }))
        setHubError(null)
        return true
      } catch (err) {
        if (err instanceof HubError && err.status >= 400 && err.status < 500) {
          setHubError(err.message || 'No se pudo abrir la mesa')
          return false
        }
        updateConnection('offline')
        commitState((current) => ({
          ...current,
          tables: current.tables.map((t) =>
            t.id === tableId ? { ...t, open: true, synced: false } : t,
          ),
        }))
        await queueSaveTabOffline(tableId, currentLines)
        return false
      }
    },
    [commitState, queueSaveTabOffline, updateConnection],
  )

  const addItem = useCallback(
    async (tableId: string, productId: string, qty: number): Promise<boolean> => {
      if (!productId || !Number.isFinite(qty) || qty <= 0) return false

      const activePairing = pairingRef.current
      const mode = connectionRef.current
      const target = stateRef.current.tables.find((t) => t.id === tableId)
      if (!target) return false

      const nextLines = mergeLines(target.lines, productId, qty)

      if (mode === 'unpaired' || !activePairing) {
        commitState((current) => ({
          ...current,
          tables: current.tables.map((t) =>
            t.id === tableId ? { ...t, open: true, synced: false, lines: nextLines } : t,
          ),
        }))
        setHubError(null)
        return true
      }

      if (mode !== 'online') {
        commitState((current) => ({
          ...current,
          tables: current.tables.map((t) =>
            t.id === tableId ? { ...t, open: true, synced: false, lines: nextLines } : t,
          ),
        }))
        await queueSaveTabOffline(tableId, nextLines)
        return false
      }

      const client = createHubClient(activePairing)
      try {
        let tabId = target.hubTabId
        if (!tabId) {
          const opened = await client.openTab({ table_id: tableId })
          tabId = opened.id
        }
        const updatedTab = await client.addItem(tabId, { product_id: productId, qty })
        const hubLines: Line[] = (updatedTab.items ?? []).map((it) => ({
          productId: it.product_id,
          qty: it.qty,
        }))
        commitState((current) => ({
          ...current,
          tables: current.tables.map((t) =>
            t.id === tableId
              ? { ...t, open: true, synced: true, hubTabId: updatedTab.id, lines: hubLines }
              : t,
          ),
          lastSyncAt: new Date().toISOString(),
        }))
        setHubError(null)
        return true
      } catch (err) {
        if (err instanceof HubError && err.status >= 400 && err.status < 500) {
          setHubError(err.message || 'No se pudo agregar el producto')
          return false
        }
        updateConnection('offline')
        commitState((current) => ({
          ...current,
          tables: current.tables.map((t) =>
            t.id === tableId ? { ...t, open: true, synced: false, lines: nextLines } : t,
          ),
        }))
        await queueSaveTabOffline(tableId, nextLines)
        return false
      }
    },
    [commitState, queueSaveTabOffline, updateConnection],
  )

  const closeTable = useCallback(
    async (tableId: string): Promise<boolean> => {
      const activePairing = pairingRef.current
      const mode = connectionRef.current
      const target = stateRef.current.tables.find((t) => t.id === tableId)
      if (!target) return false

      const localOrder =
        target.lines.length > 0 ? buildLocalOrder(target, stateRef.current.products) : null

      if (mode === 'unpaired' || !activePairing) {
        commitState((current) => ({
          ...current,
          orders: localOrder ? [...current.orders, localOrder] : current.orders,
          tables: current.tables.map((t) =>
            t.id === tableId ? { ...t, open: false, synced: true, lines: [], hubTabId: null } : t,
          ),
        }))
        setHubError(null)
        return true
      }

      if (mode !== 'online') {
        const closingLines = [...target.lines]
        commitState((current) => ({
          ...current,
          orders: localOrder ? [...current.orders, localOrder] : current.orders,
          tables: current.tables.map((t) =>
            t.id === tableId
              ? { ...t, open: false, synced: false, lines: [], hubTabId: null }
              : t,
          ),
        }))
        await queueCloseTabOffline(tableId, closingLines)
        return false
      }

      const client = createHubClient(activePairing)
      try {
        let tabId = target.hubTabId
        if (!tabId) {
          const openTabs = await client.openTabs()
          tabId = openTabs.find((t) => t.table_id === tableId)?.id ?? null
        }

        if (tabId) {
          const sale = await client.closeTab(tabId)
          const mappedSale = mapSales([sale], { [target.id]: target.name })[0]
          const newOrder: Order = {
            id: mappedSale.id,
            table: mappedSale.table,
            hour: mappedSale.hour,
            items: mappedSale.items,
            total: mappedSale.total,
          }
          commitState((current) => ({
            ...current,
            orders: [newOrder, ...current.orders.filter((o) => o.id !== newOrder.id)],
            tables: current.tables.map((t) =>
              t.id === tableId
                ? { ...t, open: false, synced: true, lines: [], hubTabId: null }
                : t,
            ),
            hubToday: current.hubToday
              ? {
                  ...current.hubToday,
                  count: current.hubToday.count + 1,
                  totalCents: current.hubToday.totalCents + sale.total_cents,
                }
              : current.hubToday,
            lastSyncAt: new Date().toISOString(),
          }))
        } else {
          commitState((current) => ({
            ...current,
            tables: current.tables.map((t) =>
              t.id === tableId
                ? { ...t, open: false, synced: true, lines: [], hubTabId: null }
                : t,
            ),
            lastSyncAt: new Date().toISOString(),
          }))
        }
        setHubError(null)
        return true
      } catch (err) {
        if (err instanceof HubError && err.status >= 400 && err.status < 500) {
          setHubError(err.message || 'No se pudo cerrar la mesa')
          return false
        }
        updateConnection('offline')
        const closingLines = [...target.lines]
        commitState((current) => ({
          ...current,
          orders: localOrder ? [...current.orders, localOrder] : current.orders,
          tables: current.tables.map((t) =>
            t.id === tableId
              ? { ...t, open: false, synced: false, lines: [], hubTabId: null }
              : t,
          ),
        }))
        await queueCloseTabOffline(tableId, closingLines)
        return false
      }
    },
    [commitState, queueCloseTabOffline, updateConnection],
  )

  const createProduct = useCallback(
    async (name: string, price: number, category: string): Promise<boolean> => {
      const cleanName = name.trim()
      const cleanCategory = category.trim() || 'Carta'
      const priceCents = toCents(price)
      if (!cleanName || priceCents <= 0) return false

      const activePairing = pairingRef.current
      const mode = connectionRef.current

      const insertLocally = (id: string, resolvedPrice: number, available: boolean) => {
        const prod: Product = { id, name: cleanName, price: resolvedPrice, category: cleanCategory }
        commitState((current) => ({
          ...current,
          products: [...current.products, prod],
          productAvailable: { ...current.productAvailable, [id]: available },
          productCategories: { ...current.productCategories, [id]: cleanCategory },
        }))
      }

      if (mode === 'unpaired' || !activePairing) {
        insertLocally(uid(), fromCents(priceCents), true)
        setHubError(null)
        return true
      }

      const enqueueOffline = async () => {
        insertLocally(uid(), fromCents(priceCents), true)
        const queued = await loadQueue()
        const nextQueue = enqueue(queued, {
          type: 'UPSERT_PRODUCT',
          payload: {
            name: cleanName,
            priceCents,
            available: true,
            category: cleanCategory,
          },
        })
        await saveQueue(nextQueue)
        setPendingCount(nextQueue.length)
        setHubError(null)
      }

      if (mode !== 'online') {
        await enqueueOffline()
        return true
      }

      const client = createHubClient(activePairing)
      try {
        const dto = await client.createProduct({
          name: cleanName,
          price_cents: priceCents,
          available: true,
        })
        commitState((current) => ({
          ...current,
          products: [
            ...current.products,
            {
              id: dto.id,
              name: dto.name,
              price: fromCents(dto.price_cents),
              category: cleanCategory,
            },
          ],
          productAvailable: { ...current.productAvailable, [dto.id]: dto.available },
          productCategories: { ...current.productCategories, [dto.id]: cleanCategory },
          lastSyncAt: new Date().toISOString(),
        }))
        setHubError(null)
        return true
      } catch (err) {
        if (err instanceof HubError && err.status >= 400 && err.status < 500) {
          setHubError(err.message || 'No se pudo crear el producto')
          return false
        }
        updateConnection('offline')
        await enqueueOffline()
        return true
      }
    },
    [commitState, updateConnection],
  )

  const setProductAvailable = useCallback(
    async (id: string, available: boolean): Promise<boolean> => {
      const activePairing = pairingRef.current
      const mode = connectionRef.current

      if (mode === 'unpaired' || !activePairing) {
        commitState((current) => ({
          ...current,
          productAvailable: { ...current.productAvailable, [id]: available },
        }))
        setHubError(null)
        return true
      }

      if (mode !== 'online') {
        commitState((current) => ({
          ...current,
          productAvailable: { ...current.productAvailable, [id]: available },
        }))
        setHubError('Sin conexión: el cambio no se envió a la PC')
        return false
      }

      const client = createHubClient(activePairing)
      try {
        const dto = await client.patchProduct(id, { available })
        commitState((current) => ({
          ...current,
          productAvailable: { ...current.productAvailable, [id]: dto.available },
          lastSyncAt: new Date().toISOString(),
        }))
        setHubError(null)
        return true
      } catch (err) {
        if (err instanceof HubError && err.status >= 400 && err.status < 500) {
          setHubError(err.message || 'No se pudo actualizar el producto')
          return false
        }
        updateConnection('offline')
        commitState((current) => ({
          ...current,
          productAvailable: { ...current.productAvailable, [id]: available },
        }))
        return false
      }
    },
    [commitState, updateConnection],
  )

  const addExpense = useCallback(
    async (concept: string, amount: number): Promise<boolean> => {
      const cleanConcept = concept.trim()
      const costCents = toCents(amount)
      if (!cleanConcept || costCents <= 0) return false

      const activePairing = pairingRef.current
      const mode = connectionRef.current

      const insertLocally = (exp: Expense) => {
        commitState((current) => ({
          ...current,
          expenses: [exp, ...current.expenses],
        }))
      }

      if (mode === 'unpaired' || !activePairing) {
        insertLocally({
          id: uid(),
          concept: cleanConcept,
          amount: fromCents(costCents),
          date: new Date().toISOString(),
        })
        setHubError(null)
        return true
      }

      const enqueueOffline = async () => {
        insertLocally({
          id: uid(),
          concept: cleanConcept,
          amount: fromCents(costCents),
          date: new Date().toISOString(),
        })
        const queued = await loadQueue()
        const nextQueue = enqueue(queued, {
          type: 'ADD_EXPENSE',
          payload: {
            description: cleanConcept,
            qty: 1,
            costCents,
          },
        })
        await saveQueue(nextQueue)
        setPendingCount(nextQueue.length)
        setHubError(null)
      }

      if (mode !== 'online') {
        await enqueueOffline()
        return true
      }

      const client = createHubClient(activePairing)
      try {
        const dto = await client.createExpense({
          description: cleanConcept,
          qty: 1,
          cost_cents: costCents,
        })
        const mapped = mapExpenses([dto])[0]
        insertLocally({
          id: mapped.id,
          concept: mapped.concept,
          amount: mapped.amount,
          date: mapped.date,
        })
        commitState((current) => ({
          ...current,
          lastSyncAt: new Date().toISOString(),
        }))
        setHubError(null)
        return true
      } catch (err) {
        if (err instanceof HubError && err.status >= 400 && err.status < 500) {
          setHubError(err.message || 'No se pudo registrar el gasto')
          return false
        }
        updateConnection('offline')
        await enqueueOffline()
        return true
      }
    },
    [commitState, updateConnection],
  )

  const createSupplier = useCallback(
    async (name: string, phone: string, note: string): Promise<boolean> => {
      const cleanName = name.trim()
      const cleanPhone = phone.trim()
      const cleanNote = note.trim()
      if (!cleanName) return false

      const activePairing = pairingRef.current
      const mode = connectionRef.current

      if (mode === 'unpaired' || !activePairing) {
        const sup: Supplier = {
          id: uid(),
          name: cleanName,
          phone: cleanPhone,
          note: cleanNote,
        }
        commitState((current) => ({
          ...current,
          suppliers: [...current.suppliers, sup],
        }))
        setHubError(null)
        return true
      }

      if (mode !== 'online') {
        setHubError('Necesitás conexión con la PC')
        return false
      }

      const client = createHubClient(activePairing)
      try {
        const dto = await client.createSupplier({
          name: cleanName,
          phone: cleanPhone,
          note: cleanNote,
        })
        const mapped = mapSuppliers([dto])[0]
        commitState((current) => {
          const nextSuppliers = [...current.suppliers, mapped]
          return {
            ...current,
            suppliers: nextSuppliers,
            hubSuppliers: nextSuppliers,
            lastSyncAt: new Date().toISOString(),
          }
        })
        setHubError(null)
        return true
      } catch (err) {
        if (err instanceof HubError && err.status >= 400 && err.status < 500) {
          setHubError(err.message || 'No se pudo crear el proveedor')
          return false
        }
        updateConnection('offline')
        setHubError('Necesitás conexión con la PC')
        return false
      }
    },
    [commitState, updateConnection],
  )

  const updateSupplier = useCallback(
    async (
      id: string,
      patch: { name?: string; phone?: string; note?: string },
    ): Promise<boolean> => {
      const activePairing = pairingRef.current
      const mode = connectionRef.current

      if (mode === 'unpaired' || !activePairing) {
        commitState((current) => ({
          ...current,
          suppliers: current.suppliers.map((s) =>
            s.id === id
              ? {
                  ...s,
                  name: patch.name !== undefined ? patch.name.trim() || s.name : s.name,
                  phone: patch.phone !== undefined ? patch.phone.trim() : s.phone,
                  note: patch.note !== undefined ? patch.note.trim() : s.note,
                }
              : s,
          ),
        }))
        setHubError(null)
        return true
      }

      if (mode !== 'online') {
        setHubError('Necesitás conexión con la PC')
        return false
      }

      const client = createHubClient(activePairing)
      const body: SupplierPatchRequest = {}
      if (patch.name !== undefined) body.name = patch.name.trim()
      if (patch.phone !== undefined) body.phone = patch.phone.trim()
      if (patch.note !== undefined) body.note = patch.note.trim()

      try {
        const dto = await client.patchSupplier(id, body)
        const mapped = mapSuppliers([dto])[0]
        commitState((current) => {
          const nextSuppliers = current.suppliers.map((s) => (s.id === id ? mapped : s))
          return {
            ...current,
            suppliers: nextSuppliers,
            hubSuppliers: nextSuppliers,
            lastSyncAt: new Date().toISOString(),
          }
        })
        setHubError(null)
        return true
      } catch (err) {
        if (err instanceof HubError && err.status >= 400 && err.status < 500) {
          setHubError(err.message || 'No se pudo actualizar el proveedor')
          return false
        }
        updateConnection('offline')
        setHubError('Necesitás conexión con la PC')
        return false
      }
    },
    [commitState, updateConnection],
  )

  const deleteSupplier = useCallback(
    async (id: string): Promise<boolean> => {
      const activePairing = pairingRef.current
      const mode = connectionRef.current

      if (mode === 'unpaired' || !activePairing) {
        commitState((current) => ({
          ...current,
          suppliers: current.suppliers.filter((s) => s.id !== id),
        }))
        setHubError(null)
        return true
      }

      if (mode !== 'online') {
        setHubError('Necesitás conexión con la PC')
        return false
      }

      const client = createHubClient(activePairing)
      try {
        await client.deleteSupplier(id)
        commitState((current) => {
          const nextSuppliers = current.suppliers.filter((s) => s.id !== id)
          return {
            ...current,
            suppliers: nextSuppliers,
            hubSuppliers: nextSuppliers,
            lastSyncAt: new Date().toISOString(),
          }
        })
        setHubError(null)
        return true
      } catch (err) {
        if (err instanceof HubError && err.status >= 400 && err.status < 500) {
          setHubError(err.message || 'No se pudo eliminar el proveedor')
          return false
        }
        updateConnection('offline')
        setHubError('Necesitás conexión con la PC')
        return false
      }
    },
    [commitState, updateConnection],
  )

  const saveBranding = useCallback(
    async (patch: { name?: string; accent?: string; accent2?: string }): Promise<boolean> => {
      const applyLocally = () => {
        commitState((current) => ({
          ...current,
          name: patch.name !== undefined ? patch.name.trim() || current.name : current.name,
          accent: patch.accent !== undefined ? patch.accent : current.accent,
          accent2: patch.accent2 !== undefined ? patch.accent2 : current.accent2,
        }))
      }

      const activePairing = pairingRef.current
      const mode = connectionRef.current

      if (mode === 'unpaired' || !activePairing) {
        applyLocally()
        setHubError(null)
        return true
      }

      if (mode !== 'online') {
        applyLocally()
        setHubError('Necesitás conexión con la PC')
        return false
      }

      const client = createHubClient(activePairing)
      const body: BrandingPatchRequest = {}
      if (patch.name !== undefined) body.shop_name = patch.name.trim()
      if (patch.accent !== undefined) body.primary = patch.accent
      if (patch.accent2 !== undefined) body.accent = patch.accent2

      try {
        const dto = await client.patchBranding(body)
        const mapped = mapBranding(activePairing.baseUrl, dto)
        commitState((current) => ({
          ...current,
          name: mapped.name || current.name,
          accent: mapped.accent || current.accent,
          accent2: mapped.accent2 || current.accent2,
          logoUrl: mapped.logoUrl,
          lastSyncAt: new Date().toISOString(),
        }))
        setHubError(null)
        return true
      } catch (err) {
        applyLocally()
        if (err instanceof HubError && err.status >= 400 && err.status < 500) {
          setHubError(err.message || 'No se pudo guardar la identidad')
          return false
        }
        updateConnection('offline')
        setHubError('Necesitás conexión con la PC')
        return false
      }
    },
    [commitState, updateConnection],
  )

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
      createTable,
      deleteTable,
      openTable,
      addItem,
      closeTable,
      createProduct,
      setProductAvailable,
      addExpense,
      createSupplier,
      updateSupplier,
      deleteSupplier,
      saveBranding,
    }),
    [
      state,
      pairing,
      connection,
      hubError,
      pendingCount,
      ready,
      update,
      connect,
      disconnect,
      syncNow,
      createTable,
      deleteTable,
      openTable,
      addItem,
      closeTable,
      createProduct,
      setProductAvailable,
      addExpense,
      createSupplier,
      updateSupplier,
      deleteSupplier,
      saveBranding,
    ],
  )

  return <StoreContext.Provider value={store}>{children}</StoreContext.Provider>
}

export function useStore(): Store {
  const store = useContext(StoreContext)
  if (!store) throw new Error('useStore must be used inside StoreProvider')
  return store
}
