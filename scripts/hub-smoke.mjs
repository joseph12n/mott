import assert from 'node:assert/strict'
import { spawn } from 'node:child_process'
import { existsSync, mkdtempSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

import { createHubClient, HubError } from '../src/hub/client.ts'
import {
  buildSnapshot,
  fromCents,
  mapBranding,
  mapExpenses,
  mapProducts,
  mapSales,
  mapSuppliers,
  mapTables,
  mapToday,
  toCents,
} from '../src/hub/mappers.ts'
import { normalizeBaseUrl, pairingUrl, parsePairingCode } from '../src/hub/pairing.ts'
import { MAX_PENDING_ATTEMPTS } from '../src/hub/queue.ts'
import { computeTabDelta, drainQueue } from '../src/hub/sync.ts'

function resolveMittBinary() {
  if (process.env.MITT_BIN) {
    return process.env.MITT_BIN
  }
  const candidates = [
    '/home/joseph/Documentos/mitt/bin/mitt-linux',
    '/usr/bin/mitt',
    '/home/joseph/Documentos/mitt/mitt',
  ]
  for (const candidate of candidates) {
    if (existsSync(candidate)) {
      return candidate
    }
  }
  return '/home/joseph/Documentos/mitt/mitt'
}

const HOST = '127.0.0.1:8099'
const BASE_URL = `http://${HOST}`
const TOKEN = 'test-token-0123456789abcdef'

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

async function waitForHealth(baseUrl, timeoutMs, getStderr) {
  const deadline = Date.now() + timeoutMs
  while (Date.now() < deadline) {
    try {
      const res = await fetch(`${baseUrl}/api/health`)
      if (res.ok) {
        const body = await res.json()
        if (body && body.status === 'ok') {
          return
        }
      }
    } catch {
      // Hub not listening yet
    }
    await sleep(100)
  }
  throw new Error(`Hub did not become healthy within ${timeoutMs}ms.\nStderr:\n${getStderr()}`)
}

async function stopProcess(child) {
  if (!child || child.exitCode !== null || child.killed) {
    return
  }
  child.kill('SIGTERM')
  const exited = await Promise.race([
    new Promise((resolve) => child.once('exit', () => resolve(true))),
    sleep(2000).then(() => false),
  ])
  if (!exited && child.exitCode === null) {
    child.kill('SIGKILL')
    await new Promise((resolve) => child.once('exit', resolve))
  }
}

async function main() {
  const mittBin = resolveMittBinary()
  const tmpDir = mkdtempSync(join(tmpdir(), `mott-hub-smoke-${process.pid}-`))
  const dbPath = join(tmpDir, 'mitt.db')
  let stderr = ''

  const child = spawn(
    mittBin,
    ['-db', dbPath, '-addr', HOST, '-advertise', BASE_URL, '-token', TOKEN],
    { stdio: ['ignore', 'pipe', 'pipe'] },
  )

  child.stderr.on('data', (chunk) => {
    stderr += chunk.toString()
  })

  try {
    await waitForHealth(BASE_URL, 10_000, () => stderr)

    const client = createHubClient({ baseUrl: BASE_URL, token: TOKEN })

    // 1. health()
    const health = await client.health()
    assert.equal(health.status, 'ok')
    console.log('PASS 1. health()')

    // 2. pairing() returns a URL and the token
    const pairing = await client.pairing()
    assert.ok(pairing.url.includes('127.0.0.1:8099') || pairing.url.startsWith('http://'))
    assert.ok(pairing.pairing_code.includes(TOKEN))
    console.log('PASS 2. pairing()')

    // 3. listProducts() (may be empty)
    const initialProducts = await client.listProducts()
    assert.ok(Array.isArray(initialProducts))
    console.log('PASS 3. listProducts()')

    // 4. createProduct({ name: "Smoke product", price_cents: 1234 }), verify id/price
    const createdProduct = await client.createProduct({
      name: 'Smoke product',
      price_cents: 1234,
    })
    assert.equal(typeof createdProduct.id, 'string')
    assert.equal(createdProduct.id.length, 32)
    assert.equal(createdProduct.name, 'Smoke product')
    assert.equal(createdProduct.price_cents, 1234)
    assert.equal(createdProduct.available, true)
    console.log('PASS 4. createProduct()')

    // 5. patchProduct(id, { available: false }), verify (then restore available: true for ordering)
    const patchedProduct = await client.patchProduct(createdProduct.id, {
      available: false,
    })
    assert.equal(patchedProduct.id, createdProduct.id)
    assert.equal(patchedProduct.available, false)
    const restoredProduct = await client.patchProduct(createdProduct.id, {
      available: true,
    })
    assert.equal(restoredProduct.available, true)
    console.log('PASS 5. patchProduct()')

    // 6. createTable({ label: "Smoke table" }), verify
    const createdTable = await client.createTable({ label: 'Smoke table' })
    assert.equal(typeof createdTable.id, 'string')
    assert.equal(createdTable.id.length, 32)
    assert.equal(createdTable.label, 'Smoke table')
    assert.equal(createdTable.occupied, false)
    console.log('PASS 6. createTable()')

    // 7. openTab({ table_id }) twice: second call returns the same tab id (200)
    const tab1 = await client.openTab({ table_id: createdTable.id })
    const tab2 = await client.openTab({ table_id: createdTable.id })
    assert.equal(typeof tab1.id, 'string')
    assert.equal(tab1.id, tab2.id)
    assert.equal(tab1.table_id, createdTable.id)
    console.log('PASS 7. openTab() idempotent')

    // 8. addItem(tabId, { product_id, qty: 2 }) twice: returned item qty is 4
    const afterAdd1 = await client.addItem(tab1.id, {
      product_id: createdProduct.id,
      qty: 2,
    })
    assert.equal(afterAdd1.items.length, 1)
    assert.equal(afterAdd1.items[0].qty, 2)
    const afterAdd2 = await client.addItem(tab1.id, {
      product_id: createdProduct.id,
      qty: 2,
    })
    assert.equal(afterAdd2.items.length, 1)
    assert.equal(afterAdd2.items[0].qty, 4)
    assert.equal(afterAdd2.total_cents, 4 * 1234)
    console.log('PASS 8. addItem() coalesces qty to 4')

    // 9. closeTab(tabId) returns a sale with total_cents = 4 * 1234
    const sale = await client.closeTab(tab1.id)
    assert.equal(sale.id, tab1.id)
    assert.equal(sale.table_id, createdTable.id)
    assert.equal(sale.total_cents, 4 * 1234)
    assert.equal(sale.items.length, 1)
    assert.equal(sale.items[0].qty, 4)
    console.log('PASS 9. closeTab()')

    // 10. salesToday() count >= 1
    const today = await client.salesToday()
    assert.ok(today.count >= 1)
    assert.equal(today.total_cents, 4 * 1234)
    console.log('PASS 10. salesToday()')

    // 11. listSales(10) includes the sale
    const sales = await client.listSales(10)
    assert.ok(sales.some((s) => s.id === sale.id && s.total_cents === 4 * 1234))
    console.log('PASS 11. listSales(10)')

    // 12. createExpense({ description: "Smoke expense", qty: 1.5, cost_cents: 200 }); listExpenses() includes it
    const expense = await client.createExpense({
      description: 'Smoke expense',
      qty: 1.5,
      cost_cents: 200,
    })
    assert.equal(expense.description, 'Smoke expense')
    assert.equal(expense.qty, 1.5)
    assert.equal(expense.cost_cents, 200)
    const expenses = await client.listExpenses()
    assert.ok(expenses.some((e) => e.id === expense.id))
    console.log('PASS 12. createExpense() and listExpenses()')

    // 13. createSupplier, patchSupplier, listSuppliers, deleteSupplier
    const supplier = await client.createSupplier({
      name: 'Smoke supplier',
      phone: '11-2222-3333',
      note: 'Initial note',
    })
    assert.equal(supplier.name, 'Smoke supplier')
    const patchedSupplier = await client.patchSupplier(supplier.id, {
      note: 'Updated note',
    })
    assert.equal(patchedSupplier.note, 'Updated note')
    assert.equal(patchedSupplier.phone, '11-2222-3333')
    const suppliersAfterPatch = await client.listSuppliers()
    assert.ok(suppliersAfterPatch.some((s) => s.id === supplier.id && s.note === 'Updated note'))
    await client.deleteSupplier(supplier.id)
    const suppliersAfterDelete = await client.listSuppliers()
    assert.ok(!suppliersAfterDelete.some((s) => s.id === supplier.id))
    console.log('PASS 13. supplier CRUD')

    // 14. patchBranding({ shop_name: "Smoke bar", primary: "#112233", accent: "#445566" }); getBranding() reflects it
    const patchedBranding = await client.patchBranding({
      shop_name: 'Smoke bar',
      primary: '#112233',
      accent: '#445566',
    })
    assert.equal(patchedBranding.shop_name, 'Smoke bar')
    assert.equal(patchedBranding.primary, '#112233')
    assert.equal(patchedBranding.accent, '#445566')
    const branding = await client.getBranding()
    assert.equal(branding.shop_name, 'Smoke bar')
    assert.equal(branding.primary, '#112233')
    assert.equal(branding.accent, '#445566')
    console.log('PASS 14. patchBranding() and getBranding()')

    // 15. parsePairingCode round-trips a valid code; three invalid inputs throw
    const code = pairingUrl({ baseUrl: `${BASE_URL}/`, token: TOKEN })
    const parsedPairing = parsePairingCode(code)
    assert.equal(parsedPairing.baseUrl, BASE_URL)
    assert.equal(parsedPairing.token, TOKEN)
    assert.equal(normalizeBaseUrl('127.0.0.1:8099/'), BASE_URL)
    const fallbackPairing = parsePairingCode(`${BASE_URL}/|${TOKEN}`)
    assert.equal(fallbackPairing.baseUrl, BASE_URL)
    assert.equal(fallbackPairing.token, TOKEN)
    for (const invalidInput of [
      '',
      'mitt://pair?url=http%3A%2F%2F127.0.0.1%3A8099&token=short',
      'mitt://pair?url=http%3A%2F%2F127.0.0.1&token=test-token-0123456789abcdef',
      'not-a-valid-pairing-string',
    ]) {
      assert.throws(() => parsePairingCode(invalidInput))
    }
    console.log('PASS 15. parsePairingCode()')

    // 16. Mapper assertions: mapProducts, mapTables, mapSales, mapExpenses, mapSuppliers, mapBranding, mapToday
    assert.equal(fromCents(1234), 12.34)
    assert.equal(toCents(12.34), 1234)
    assert.equal(toCents(Number.NaN), 0)

    const mappedProducts = mapProducts([restoredProduct], { [restoredProduct.id]: 'Bebidas' })
    assert.equal(mappedProducts[0].price, 12.34)
    assert.equal(mappedProducts[0].category, 'Bebidas')

    const openTabForMap = await client.openTab({ table_id: createdTable.id })
    await client.addItem(openTabForMap.id, { product_id: createdProduct.id, qty: 1 })
    const [tablesList, openTabsList] = await Promise.all([
      client.listTables(),
      client.openTabs(),
    ])
    const mappedTables = mapTables(tablesList, openTabsList)
    assert.equal(mappedTables.length, 1)
    assert.equal(mappedTables[0].open, true)
    assert.equal(mappedTables[0].tabId, openTabForMap.id)
    assert.deepEqual(mappedTables[0].lines, [{ productId: createdProduct.id, qty: 1 }])

    const mappedSales = mapSales(sales, { [createdTable.id]: createdTable.label })
    assert.equal(mappedSales[0].table, 'Smoke table')
    assert.equal(mappedSales[0].total, (4 * 1234) / 100)
    assert.equal(mappedSales[0].items[0].price, 12.34)

    const mappedExpenses = mapExpenses(expenses)
    assert.equal(mappedExpenses[0].concept, 'Smoke expense')
    assert.equal(mappedExpenses[0].amount, 3)

    const mappedSuppliers = mapSuppliers([supplier])
    assert.equal(mappedSuppliers[0].name, 'Smoke supplier')

    const mappedBrandNoLogo = mapBranding(BASE_URL, branding)
    assert.equal(mappedBrandNoLogo.name, 'Smoke bar')
    assert.equal(mappedBrandNoLogo.accent, '#112233')
    assert.equal(mappedBrandNoLogo.accent2, '#445566')
    assert.equal(mappedBrandNoLogo.logoUrl, null)

    const mappedBrandWithLogo = mapBranding(BASE_URL, {
      ...branding,
      has_logo: true,
      updated_at: '2026-10-20T00:00:00Z',
    })
    assert.equal(
      mappedBrandWithLogo.logoUrl,
      `${BASE_URL}/api/branding/logo?v=2026-10-20T00%3A00%3A00Z`,
    )

    const mappedToday = mapToday(today)
    assert.equal(mappedToday.count, today.count)
    assert.equal(mappedToday.totalCents, today.total_cents)

    const snapshot = buildSnapshot({
      products: [restoredProduct],
      tables: tablesList,
      tabs: openTabsList,
      sales,
      expenses,
      suppliers: [supplier],
      branding,
      today,
      baseUrl: BASE_URL,
    })
    assert.equal(snapshot.products.length, 1)
    assert.equal(snapshot.tables.length, 1)
    assert.equal(snapshot.orders.length, 1)
    assert.equal(snapshot.expenses.length, 1)
    assert.equal(snapshot.suppliers.length, 1)
    console.log('PASS 16. mappers')

    // 17. HubError on a bad token: create a second client with token: "bad" and expect listProducts() to throw with status === 401
    const badClient = createHubClient({ baseUrl: BASE_URL, token: 'bad' })
    await assert.rejects(
      () => badClient.listProducts(),
      (err) => err instanceof HubError && err.status === 401,
    )
    console.log('PASS 17. HubError on 401')

    // 18. Offline outbox: delta, idempotent SAVE_TAB, poison drop, retry, exhaustion
    assert.deepEqual(
      computeTabDelta(
        [{ productId: 'p1', qty: 4 }],
        [{ product_id: 'p1', qty: 1 }],
      ),
      [{ product_id: 'p1', qty: 3 }],
    )
    assert.deepEqual(
      computeTabDelta([{ productId: 'p1', qty: 2 }], [{ product_id: 'p1', qty: 4 }]),
      [],
    )

    const existingTab = {
      id: 'tab-existing',
      table_id: 'table-1',
      status: 'open',
      opened_at: '2026-10-20T00:00:00Z',
      items: [
        { product_id: 'p1', name: 'Product', unit_price_cents: 100, qty: 1, line_total_cents: 100 },
      ],
      total_cents: 100,
    }
    const addedItems = []
    const idempotentClient = {
      baseUrl: BASE_URL,
      openTabs: async () => [existingTab],
      openTab: async () => {
        throw new Error('openTab should not be called')
      },
      addItem: async (tabId, body) => {
        addedItems.push({ tabId, body })
        return existingTab
      },
    }
    const saved = await drainQueue(idempotentClient, [
      {
        id: 'op-save',
        type: 'SAVE_TAB',
        payload: { tableId: 'table-1', lines: [{ productId: 'p1', qty: 4 }] },
        attempts: 0,
        createdAt: 0,
      },
    ])
    assert.deepEqual(addedItems, [
      { tabId: 'tab-existing', body: { product_id: 'p1', qty: 3 } },
    ])
    assert.equal(saved.ops.length, 0)
    assert.equal(saved.report.synced, 1)

    const poisonClient = {
      baseUrl: BASE_URL,
      openTabs: async () => [],
      openTab: async () => ({ ...existingTab, id: 'tab-new', items: [] }),
      addItem: async () => {
        throw new HubError(400, 'bad_request', 'bad')
      },
    }
    const poisoned = await drainQueue(poisonClient, [
      {
        id: 'op-poison',
        type: 'SAVE_TAB',
        payload: { tableId: 'table-1', lines: [{ productId: 'p1', qty: 1 }] },
        attempts: 0,
        createdAt: 0,
      },
    ])
    assert.equal(poisoned.ops.length, 0)
    assert.equal(poisoned.report.dropped, 1)

    const failingClient = {
      baseUrl: BASE_URL,
      openTabs: async () => [],
      openTab: async () => ({ ...existingTab, id: 'tab-new', items: [] }),
      addItem: async () => {
        throw new HubError(500, 'hub_error', 'boom')
      },
    }
    const retried = await drainQueue(failingClient, [
      {
        id: 'op-retry',
        type: 'SAVE_TAB',
        payload: { tableId: 'table-1', lines: [{ productId: 'p1', qty: 1 }] },
        attempts: 0,
        createdAt: 0,
      },
    ])
    assert.equal(retried.ops.length, 1)
    assert.equal(retried.ops[0].attempts, 1)
    assert.equal(retried.report.deferred, 1)

    const exhausted = await drainQueue(failingClient, [
      {
        id: 'op-exhausted',
        type: 'SAVE_TAB',
        payload: { tableId: 'table-1', lines: [{ productId: 'p1', qty: 1 }] },
        attempts: MAX_PENDING_ATTEMPTS - 1,
        createdAt: 0,
      },
    ])
    assert.equal(exhausted.ops.length, 0)
    assert.equal(exhausted.report.exhausted, 1)

    const closed = await drainQueue(
      {
        baseUrl: BASE_URL,
        openTabs: async () => [],
        closeTab: async () => {
          throw new Error('closeTab should not be called')
        },
      },
      [
        {
          id: 'op-close',
          type: 'CLOSE_TAB',
          payload: { tableId: 'table-1' },
          attempts: 0,
          createdAt: 0,
        },
      ],
    )
    assert.equal(closed.ops.length, 0)
    assert.equal(closed.report.synced, 1)
    console.log('PASS 18. outbox delta, idempotent save, poison, retry, exhaustion')
  } finally {
    await stopProcess(child)
    rmSync(tmpDir, { recursive: true, force: true })
  }
}

main().catch((err) => {
  console.error(err)
  process.exit(1)
})
