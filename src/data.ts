// Domain types, seeds and formatting ported from the web master
// (mottandmittdesing/src/data.ts) and aligned with the mitt PC hub domain.

export type Product = { id: string; name: string; price: number; category: string }
export type Line = { productId: string; qty: number }
export type Table = {
  id: string
  name: string
  open: boolean
  synced: boolean
  lines: Line[]
  hubTabId: string | null
}
export type Order = {
  id: string
  table: string
  hour: number
  items: { name: string; qty: number; price: number }[]
  total: number
}
export type Expense = { id: string; concept: string; amount: number; date: string }
export type Supplier = {
  id: string
  name: string
  phone: string
  note: string
}

export const uid = () => Math.random().toString(36).slice(2, 8)

// "$ 8.900,00": thousands with dots, decimals with a comma, like the web's
// toLocaleString('es-AR').
export function money(n: number): string {
  const negative = n < 0
  const abs = Math.abs(n)
  const [int, dec] = abs.toFixed(2).split('.')
  const grouped = int.replace(/\B(?=(\d{3})+(?!\d))/g, '.')
  return `${negative ? '-' : ''}$ ${grouped},${dec}`
}

function sameLocalDay(a: Date, b: Date): boolean {
  return (
    a.getFullYear() === b.getFullYear() &&
    a.getMonth() === b.getMonth() &&
    a.getDate() === b.getDate()
  )
}

export function isToday(iso: string): boolean {
  if (!iso || typeof iso !== 'string') return false
  const trimmed = iso.trim()
  if (!trimmed) return false
  if (trimmed === 'Hoy') return true
  if (trimmed === 'Ayer') return false
  const parsed = new Date(trimmed)
  if (Number.isNaN(parsed.getTime())) return false
  return sameLocalDay(parsed, new Date())
}

export function formatDay(iso: string): string {
  if (!iso || typeof iso !== 'string') return ''
  const trimmed = iso.trim()
  if (!trimmed) return ''
  if (trimmed === 'Hoy' || trimmed === 'Ayer') return trimmed
  const parsed = new Date(trimmed)
  if (Number.isNaN(parsed.getTime())) return ''
  const now = new Date()
  if (sameLocalDay(parsed, now)) return 'Hoy'
  const yesterday = new Date(now.getFullYear(), now.getMonth(), now.getDate() - 1)
  if (sameLocalDay(parsed, yesterday)) return 'Ayer'
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${pad(parsed.getDate())}/${pad(parsed.getMonth() + 1)}`
}

export const seedProducts: Product[] = [
  { id: 'p1', name: 'Milanesa napolitana', price: 8900, category: 'Platos' },
  { id: 'p2', name: 'Pizza muzzarella', price: 7400, category: 'Platos' },
  { id: 'p3', name: 'Hamburguesa doble', price: 9200, category: 'Platos' },
  { id: 'p4', name: 'Ensalada César', price: 6100, category: 'Entradas' },
  { id: 'p5', name: 'Cerveza artesanal', price: 3200, category: 'Bebidas' },
  { id: 'p6', name: 'Limonada menta', price: 2400, category: 'Bebidas' },
  { id: 'p7', name: 'Flan con dulce', price: 3500, category: 'Postres' },
]

export const seedTables: Table[] = [
  { id: 't1', name: 'Mesa 1', open: true, synced: true, lines: [{ productId: 'p1', qty: 2 }, { productId: 'p5', qty: 3 }], hubTabId: null },
  { id: 't2', name: 'Mesa 2', open: false, synced: true, lines: [], hubTabId: null },
  { id: 't3', name: 'Mesa 3', open: true, synced: true, lines: [{ productId: 'p2', qty: 1 }, { productId: 'p6', qty: 2 }], hubTabId: null },
  { id: 't4', name: 'Mesa 4', open: true, synced: false, lines: [{ productId: 'p3', qty: 2 }], hubTabId: null },
  { id: 't5', name: 'Terraza 1', open: false, synced: false, lines: [], hubTabId: null },
  { id: 't6', name: 'Barra', open: false, synced: true, lines: [], hubTabId: null },
]

const mk = (table: string, hour: number, items: [string, number, number][]): Order => {
  const its = items.map(([name, qty, price]) => ({ name, qty, price }))
  return { id: uid(), table, hour, items: its, total: its.reduce((s, i) => s + i.qty * i.price, 0) }
}

export const seedOrders: Order[] = [
  mk('Mesa 1', 12, [['Milanesa napolitana', 2, 8900], ['Cerveza artesanal', 2, 3200]]),
  mk('Mesa 2', 12, [['Pizza muzzarella', 2, 7400], ['Limonada menta', 2, 2400]]),
  mk('Barra', 13, [['Hamburguesa doble', 1, 9200], ['Cerveza artesanal', 1, 3200]]),
  mk('Mesa 3', 13, [['Ensalada César', 2, 6100], ['Flan con dulce', 2, 3500]]),
  mk('Mesa 1', 14, [['Pizza muzzarella', 3, 7400], ['Cerveza artesanal', 4, 3200]]),
  mk('Terraza 1', 14, [['Hamburguesa doble', 3, 9200], ['Limonada menta', 3, 2400]]),
  mk('Mesa 4', 15, [['Milanesa napolitana', 1, 8900], ['Flan con dulce', 1, 3500]]),
  mk('Mesa 2', 16, [['Cerveza artesanal', 6, 3200]]),
  mk('Mesa 3', 17, [['Pizza muzzarella', 2, 7400], ['Ensalada César', 1, 6100]]),
  mk('Barra', 18, [['Cerveza artesanal', 3, 3200], ['Hamburguesa doble', 1, 9200]]),
]

export const seedExpenses: Expense[] = [
  { id: 'e1', concept: 'Proveedor de carnes', amount: 42000, date: 'Hoy' },
  { id: 'e2', concept: 'Gas y servicios', amount: 15800, date: 'Hoy' },
  { id: 'e3', concept: 'Bebidas y hielo', amount: 21300, date: 'Ayer' },
]

export const seedSuppliers: Supplier[] = [
  { id: 's1', name: 'Frigorífico Don Aldo', phone: '11 4455-2301', note: 'Carnes' },
  { id: 's2', name: 'Distribuidora La Fuente', phone: '11 5120-8890', note: 'Bebidas' },
  { id: 's3', name: 'Verdulería El Huerto', phone: '11 3398-7712', note: 'Verduras' },
]
