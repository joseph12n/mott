// Hub snapshot pull and idempotent FIFO queue drain.

import { HubError } from './client.ts'
import type { HubClient } from './client.ts'
import { buildSnapshot } from './mappers.ts'
import type { HubSnapshot } from './model.ts'
import { bumpAttempt, MAX_PENDING_ATTEMPTS, removeOp } from './queue.ts'
import type {
  AddExpensePayload,
  CloseTabPayload,
  PendingOp,
  SaveTabPayload,
  UpsertProductPayload,
} from './queue.ts'

export type SyncReport = {
  synced: number
  dropped: number
  deferred: number
  exhausted: number
}

export async function pullSnapshot(
  client: HubClient,
  existingCategories?: Record<string, string>,
): Promise<HubSnapshot> {
  const [products, tables, tabs, sales, expenses, suppliers, branding, today] = await Promise.all([
    client.listProducts(),
    client.listTables(),
    client.openTabs(),
    client.listSales(500),
    client.listExpenses(),
    client.listSuppliers(),
    client.getBranding(),
    client.salesToday(),
  ])

  return buildSnapshot({
    products,
    tables,
    tabs,
    sales,
    expenses,
    suppliers,
    branding,
    today,
    baseUrl: client.baseUrl,
    categories: existingCategories,
  })
}

export function computeTabDelta(
  desiredLines: { productId: string; qty: number }[],
  existingItems: { product_id: string; qty: number }[],
): { product_id: string; qty: number }[] {
  const currentByProduct = new Map<string, number>()
  for (const item of existingItems) {
    currentByProduct.set(item.product_id, (currentByProduct.get(item.product_id) ?? 0) + item.qty)
  }

  const desiredByProduct = new Map<string, number>()
  for (const line of desiredLines) {
    if (!line.productId || !Number.isFinite(line.qty) || line.qty <= 0) continue
    desiredByProduct.set(line.productId, (desiredByProduct.get(line.productId) ?? 0) + line.qty)
  }

  const deltas: { product_id: string; qty: number }[] = []
  for (const [productId, desiredQty] of desiredByProduct.entries()) {
    const currentQty = currentByProduct.get(productId) ?? 0
    const missingQty = desiredQty - currentQty
    if (missingQty > 0) {
      deltas.push({ product_id: productId, qty: missingQty })
    }
  }
  return deltas
}

async function executeOp(client: HubClient, op: PendingOp): Promise<void> {
  switch (op.type) {
    case 'SAVE_TAB': {
      const payload = op.payload as Partial<SaveTabPayload> | null
      if (!payload || typeof payload.tableId !== 'string' || !payload.tableId.trim() || !Array.isArray(payload.lines)) {
        throw new HubError(400, 'invalid_payload', 'Invalid SAVE_TAB payload')
      }
      const openTabs = await client.openTabs()
      let tab = openTabs.find((t) => t.table_id === payload.tableId)
      if (!tab) {
        tab = await client.openTab({ table_id: payload.tableId })
      }
      const deltas = computeTabDelta(payload.lines, tab.items ?? [])
      for (const delta of deltas) {
        await client.addItem(tab.id, delta)
      }
      return
    }

    case 'CLOSE_TAB': {
      const payload = op.payload as Partial<CloseTabPayload> | null
      if (!payload || typeof payload.tableId !== 'string' || !payload.tableId.trim()) {
        throw new HubError(400, 'invalid_payload', 'Invalid CLOSE_TAB payload')
      }
      const openTabs = await client.openTabs()
      let tab = openTabs.find((t) => t.table_id === payload.tableId)
      const hasLines = Array.isArray(payload.lines) && payload.lines.length > 0
      if (!tab) {
        if (!hasLines) {
          // Already closed on the hub; treat as done.
          return
        }
        tab = await client.openTab({ table_id: payload.tableId })
      }
      if (hasLines && payload.lines) {
        const deltas = computeTabDelta(payload.lines, tab.items ?? [])
        for (const delta of deltas) {
          await client.addItem(tab.id, delta)
        }
      }
      await client.closeTab(tab.id)
      return
    }

    case 'ADD_EXPENSE': {
      const payload = op.payload as Partial<AddExpensePayload> | null
      const costCents = payload?.costCents ?? payload?.cost_cents
      if (
        !payload ||
        typeof payload.description !== 'string' ||
        !payload.description.trim() ||
        typeof payload.qty !== 'number' ||
        !Number.isFinite(payload.qty) ||
        typeof costCents !== 'number' ||
        !Number.isFinite(costCents)
      ) {
        throw new HubError(400, 'invalid_payload', 'Invalid ADD_EXPENSE payload')
      }
      await client.createExpense({
        description: payload.description.trim(),
        qty: payload.qty,
        cost_cents: Math.round(costCents),
      })
      return
    }

    case 'UPSERT_PRODUCT': {
      const payload = op.payload as Partial<UpsertProductPayload> | null
      const priceCents = payload?.priceCents ?? payload?.price_cents
      if (
        !payload ||
        typeof payload.name !== 'string' ||
        !payload.name.trim() ||
        typeof priceCents !== 'number' ||
        !Number.isFinite(priceCents)
      ) {
        throw new HubError(400, 'invalid_payload', 'Invalid UPSERT_PRODUCT payload')
      }
      const cleanName = payload.name.trim()
      const roundedPrice = Math.round(priceCents)
      const existing = await client.listProducts()
      const duplicate = existing.some(
        (p) => p.name.trim() === cleanName && p.price_cents === roundedPrice,
      )
      if (duplicate) {
        return
      }
      await client.createProduct({
        name: cleanName,
        price_cents: roundedPrice,
        available: payload.available ?? true,
      })
      return
    }

    default:
      throw new HubError(400, 'unknown_op_type', 'Unsupported pending operation')
  }
}

export async function drainQueue(
  client: HubClient,
  ops: PendingOp[],
): Promise<{ report: SyncReport; ops: PendingOp[] }> {
  const report: SyncReport = {
    synced: 0,
    dropped: 0,
    deferred: 0,
    exhausted: 0,
  }
  let nextOps = [...ops]

  for (let i = 0; i < ops.length; i += 1) {
    const op = ops[i]
    if (op.attempts >= MAX_PENDING_ATTEMPTS) {
      // Dead-letter: stop retrying and drop it so the queue can drain.
      nextOps = removeOp(nextOps, op.id)
      report.exhausted += 1
      continue
    }

    try {
      await executeOp(client, op)
      nextOps = removeOp(nextOps, op.id)
      report.synced += 1
    } catch (err) {
      if (err instanceof HubError && err.status >= 400 && err.status < 500) {
        nextOps = removeOp(nextOps, op.id)
        report.dropped += 1
        continue
      }

      if (op.attempts + 1 >= MAX_PENDING_ATTEMPTS) {
        nextOps = removeOp(nextOps, op.id)
        report.exhausted += 1
      } else {
        nextOps = bumpAttempt(nextOps, op.id)
        report.deferred += 1
      }

      for (let j = i + 1; j < ops.length; j += 1) {
        if (ops[j].attempts >= MAX_PENDING_ATTEMPTS) {
          report.exhausted += 1
        } else {
          report.deferred += 1
        }
      }
      break
    }
  }

  return { report, ops: nextOps }
}
