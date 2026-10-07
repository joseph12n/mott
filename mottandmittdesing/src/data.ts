export type Product = { id: string; name: string; price: number; category: string }
export type Line = { productId: string; qty: number }
export type Table = { id: string; name: string; open: boolean; synced: boolean; lines: Line[] }
export type Order = { id: string; table: string; hour: number; items: { name: string; qty: number; price: number }[]; total: number; method: string }
export type Expense = { id: string; concept: string; amount: number; date: string }

export const uid = () => Math.random().toString(36).slice(2, 8)
export const money = (n: number) =>
  '$ ' + n.toLocaleString('es-AR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })

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
  { id: 't1', name: 'Mesa 1', open: true, synced: true, lines: [{ productId: 'p1', qty: 2 }, { productId: 'p5', qty: 3 }] },
  { id: 't2', name: 'Mesa 2', open: false, synced: true, lines: [] },
  { id: 't3', name: 'Mesa 3', open: true, synced: true, lines: [{ productId: 'p2', qty: 1 }, { productId: 'p6', qty: 2 }] },
  { id: 't4', name: 'Mesa 4', open: true, synced: false, lines: [{ productId: 'p3', qty: 2 }] },
  { id: 't5', name: 'Terraza 1', open: false, synced: false, lines: [] },
  { id: 't6', name: 'Barra', open: false, synced: true, lines: [] },
]

const mk = (table: string, hour: number, method: string, items: [string, number, number][]): Order => {
  const its = items.map(([name, qty, price]) => ({ name, qty, price }))
  return { id: uid(), table, hour, method, items: its, total: its.reduce((s, i) => s + i.qty * i.price, 0) }
}
export const seedOrders: Order[] = [
  mk('Mesa 1', 12, 'Tarjeta', [['Milanesa napolitana', 2, 8900], ['Cerveza artesanal', 2, 3200]]),
  mk('Mesa 2', 12, 'Efectivo', [['Pizza muzzarella', 2, 7400], ['Limonada menta', 2, 2400]]),
  mk('Barra', 13, 'QR', [['Hamburguesa doble', 1, 9200], ['Cerveza artesanal', 1, 3200]]),
  mk('Mesa 3', 13, 'Tarjeta', [['Ensalada César', 2, 6100], ['Flan con dulce', 2, 3500]]),
  mk('Mesa 1', 14, 'Efectivo', [['Pizza muzzarella', 3, 7400], ['Cerveza artesanal', 4, 3200]]),
  mk('Terraza 1', 14, 'QR', [['Hamburguesa doble', 3, 9200], ['Limonada menta', 3, 2400]]),
  mk('Mesa 4', 15, 'Tarjeta', [['Milanesa napolitana', 1, 8900], ['Flan con dulce', 1, 3500]]),
  mk('Mesa 2', 16, 'Tarjeta', [['Cerveza artesanal', 6, 3200]]),
  mk('Mesa 3', 17, 'Efectivo', [['Pizza muzzarella', 2, 7400], ['Ensalada César', 1, 6100]]),
  mk('Barra', 18, 'QR', [['Cerveza artesanal', 3, 3200], ['Hamburguesa doble', 1, 9200]]),
]
export const seedExpenses: Expense[] = [
  { id: 'e1', concept: 'Proveedor de carnes', amount: 42000, date: 'Hoy' },
  { id: 'e2', concept: 'Gas y servicios', amount: 15800, date: 'Hoy' },
  { id: 'e3', concept: 'Bebidas y hielo', amount: 21300, date: 'Ayer' },
]

export type SupProduct = { id: string; name: string; price: number; unit: string }
export type Purchase = { id: string; productName: string; qty: number; price: number; paid: boolean; date: string }
export type Supplier = { id: string; name: string; phone: string; category: string; products: SupProduct[]; purchases: Purchase[] }

export const seedSuppliers: Supplier[] = [
  {
    id: 's1', name: 'Frigorífico Don Aldo', phone: '11 4455-2301', category: 'Carnes',
    products: [{ id: 'sp1', name: 'Carne picada', price: 6800, unit: 'kg' }, { id: 'sp2', name: 'Bife de chorizo', price: 11200, unit: 'kg' }],
    purchases: [
      { id: 'c1', productName: 'Carne picada', qty: 10, price: 6800, paid: true, date: 'Hoy' },
      { id: 'c2', productName: 'Bife de chorizo', qty: 6, price: 11200, paid: false, date: 'Hoy' },
    ],
  },
  {
    id: 's2', name: 'Distribuidora La Fuente', phone: '11 5120-8890', category: 'Bebidas',
    products: [{ id: 'sp3', name: 'Cerveza barril 30L', price: 68000, unit: 'barril' }, { id: 'sp4', name: 'Gaseosa 2.25L', price: 2100, unit: 'u.' }],
    purchases: [{ id: 'c3', productName: 'Cerveza barril 30L', qty: 1, price: 68000, paid: false, date: 'Ayer' }],
  },
  {
    id: 's3', name: 'Verdulería El Huerto', phone: '11 3398-7712', category: 'Verduras',
    products: [{ id: 'sp5', name: 'Tomate', price: 1900, unit: 'kg' }, { id: 'sp6', name: 'Lechuga', price: 1200, unit: 'u.' }],
    purchases: [{ id: 'c4', productName: 'Tomate', qty: 12, price: 1900, paid: true, date: 'Hoy' }],
  },
]
