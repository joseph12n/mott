// Persisted offline outbox queue for hub write operations.

export type PendingOpType = 'UPSERT_PRODUCT' | 'SAVE_TAB' | 'CLOSE_TAB' | 'ADD_EXPENSE'

export type PendingOp = {
  id: string
  type: PendingOpType
  payload: unknown
  attempts: number
  createdAt: number
}

export type UpsertProductPayload = {
  name: string
  priceCents?: number
  price_cents?: number
  available?: boolean
  category?: string
}

export type SaveTabPayload = {
  tableId: string
  lines: { productId: string; qty: number }[]
}

export type CloseTabPayload = {
  tableId: string
}

export type AddExpensePayload = {
  description: string
  qty: number
  costCents?: number
  cost_cents?: number
}

export interface QueueStorage {
  getItem(key: string): Promise<string | null>
  setItem(key: string, value: string): Promise<void>
}

export const PENDING_QUEUE_KEY = 'mott:pending:v1'
export const MAX_PENDING_ATTEMPTS = 5

const VALID_TYPES = new Set<PendingOpType>([
  'UPSERT_PRODUCT',
  'SAVE_TAB',
  'CLOSE_TAB',
  'ADD_EXPENSE',
])

function makeOpId(): string {
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`
}

async function resolveStorage(storage?: QueueStorage): Promise<QueueStorage> {
  if (storage) return storage
  const mod = await import('@react-native-async-storage/async-storage')
  return mod.default
}

function isValidPendingOp(raw: unknown): raw is PendingOp {
  if (!raw || typeof raw !== 'object') return false
  const candidate = raw as Partial<PendingOp>
  return (
    typeof candidate.id === 'string' &&
    candidate.id.length > 0 &&
    typeof candidate.type === 'string' &&
    VALID_TYPES.has(candidate.type as PendingOpType) &&
    typeof candidate.attempts === 'number' &&
    Number.isFinite(candidate.attempts) &&
    candidate.attempts >= 0 &&
    typeof candidate.createdAt === 'number' &&
    Number.isFinite(candidate.createdAt)
  )
}

export async function loadQueue(storage?: QueueStorage): Promise<PendingOp[]> {
  try {
    const store = await resolveStorage(storage)
    const raw = await store.getItem(PENDING_QUEUE_KEY)
    if (!raw) return []
    const parsed = JSON.parse(raw)
    if (!Array.isArray(parsed)) return []
    return parsed.filter(isValidPendingOp)
  } catch {
    return []
  }
}

export async function saveQueue(ops: PendingOp[], storage?: QueueStorage): Promise<void> {
  try {
    const store = await resolveStorage(storage)
    await store.setItem(PENDING_QUEUE_KEY, JSON.stringify(ops))
  } catch {
    // Storage write failures should not crash the UI; in-memory queue stands.
  }
}

export function enqueue(
  ops: PendingOp[],
  op: {
    id?: string
    type: PendingOpType
    payload: unknown
    attempts?: number
    createdAt?: number
  },
): PendingOp[] {
  const next: PendingOp = {
    id: op.id && op.id.trim() ? op.id : makeOpId(),
    type: op.type,
    payload: op.payload,
    attempts: op.attempts ?? 0,
    createdAt: op.createdAt ?? Date.now(),
  }
  return [...ops, next]
}

export function removeOp(ops: PendingOp[], id: string): PendingOp[] {
  return ops.filter((op) => op.id !== id)
}

export function bumpAttempt(ops: PendingOp[], id: string): PendingOp[] {
  return ops.map((op) => (op.id === id ? { ...op, attempts: op.attempts + 1 } : op))
}
