import { useEffect, useMemo, useState } from 'react'
import { LayoutDashboard, Armchair, BookOpen, Receipt, QrCode, Palette, RefreshCw, Truck, Moon, Sun } from 'lucide-react'
import { Expense, Order, Product, Supplier, Table, seedExpenses, seedOrders, seedProducts, seedSuppliers, seedTables } from './data'
import Proveedores from './views/Proveedores'
import Panel from './views/Panel'
import Mesas from './views/Mesas'
import Catalogo from './views/Catalogo'
import Gastos from './views/Gastos'
import Conexion from './views/Conexion'
import Personalizar from './views/Personalizar'

export type Store = {
  products: Product[]; setProducts: (f: (p: Product[]) => Product[]) => void
  tables: Table[]; setTables: (f: (t: Table[]) => Table[]) => void
  orders: Order[]; setOrders: (f: (o: Order[]) => Order[]) => void
  expenses: Expense[]; setExpenses: (f: (e: Expense[]) => Expense[]) => void
  suppliers: Supplier[]; setSuppliers: (f: (s: Supplier[]) => Supplier[]) => void
  name: string; setName: (n: string) => void
  accent: string; setAccent: (c: string) => void
  accent2: string; setAccent2: (c: string) => void
  logo: string; setLogo: (l: string) => void
  dark: boolean; setDark: (d: boolean) => void
}

function usePersist<T>(key: string, init: T): [T, (v: T) => void] {
  const [v, set] = useState<T>(() => {
    try { const r = localStorage.getItem('mitt:' + key); return r ? (JSON.parse(r) as T) : init } catch { return init }
  })
  return [v, (n: T) => { set(n); try { localStorage.setItem('mitt:' + key, JSON.stringify(n)) } catch {} }]
}

const nav = [
  { id: 'panel', label: 'Panel', icon: LayoutDashboard },
  { id: 'mesas', label: 'Mesas', icon: Armchair },
  { id: 'catalogo', label: 'Catálogo', icon: BookOpen },
  { id: 'proveedores', label: 'Proveedores', icon: Truck },
  { id: 'gastos', label: 'Gastos', icon: Receipt },
  { id: 'conexion', label: 'Conexión', icon: QrCode },
  { id: 'personalizar', label: 'Personalizar', icon: Palette },
] as const
type View = (typeof nav)[number]['id']

export default function App() {
  const [view, setView] = useState<View>('panel')
  const [products, setProducts] = useState(seedProducts)
  const [tables, setTables] = useState(seedTables)
  const [orders, setOrders] = useState(seedOrders)
  const [expenses, setExpenses] = useState(seedExpenses)
  const [suppliers, setSuppliers] = useState(seedSuppliers)
  const [name, setName] = usePersist('name', 'mitt')
  const [accent, setAccent] = usePersist('accent', '#0e7a5a')
  const [accent2, setAccent2] = usePersist('accent2', '#f2a33a')
  const [logo, setLogo] = usePersist('logo', '')
  const [dark, setDark] = usePersist('dark', false)

  useEffect(() => {
    const r = document.documentElement
    r.style.setProperty('--brand-base', accent)
    r.style.setProperty('--sun-base', accent2)
    r.classList.toggle('dark', dark)
  }, [accent, accent2, dark])

  const store: Store = useMemo(
    () => ({ products, setProducts, tables, setTables, orders, setOrders, expenses, setExpenses, suppliers, setSuppliers, name, setName, accent, setAccent, accent2, setAccent2, logo, setLogo, dark, setDark }),
    [products, tables, orders, expenses, suppliers, name, accent, accent2, logo, dark],
  )
  const openCount = tables.filter((t) => t.open).length

  return (
    <div className="min-h-screen lg:flex">
      <aside className="lg:sticky lg:top-0 lg:h-screen lg:w-64 shrink-0 flex lg:flex-col gap-2 p-3 lg:p-5 border-b lg:border-b-0 lg:border-r border-line bg-paper overflow-x-auto">
        <div className="hidden lg:flex items-center gap-3 mb-8 px-2">
          <div className="size-10 rounded-xl bg-brand text-on-brand grid place-items-center font-display font-extrabold text-lg overflow-hidden shrink-0">
            {logo ? <img src={logo} alt={`Logo de ${name}`} className="size-full object-cover" /> : name.slice(0, 1).toUpperCase()}
          </div>
          <div>
            <div className="font-display font-bold text-lg leading-none">{name}</div>
            <div className="text-xs text-mute mt-1">Servidor local</div>
          </div>
        </div>
        <nav className="flex lg:flex-col gap-1 flex-1">
          {nav.map(({ id, label, icon: Icon }) => (
            <button key={id} onClick={() => setView(id)}
              className={`flex items-center gap-3 px-3 h-11 rounded-xl text-sm font-semibold transition whitespace-nowrap ${view === id ? 'bg-ink text-paper' : 'text-mute hover:bg-sunk hover:text-ink'}`}>
              <Icon size={18} />{label}
              {id === 'mesas' && openCount > 0 && (
                <span className={`ml-auto num text-xs rounded-full px-2 py-0.5 ${view === id ? 'bg-paper/20' : 'bg-brand-soft text-brand'}`}>{openCount}</span>
              )}
            </button>
          ))}
        </nav>
        <div className="hidden lg:block space-y-3">
          <div className="flex items-center gap-2 px-2 text-xs font-bold tracking-wide text-brand">
            <span className="size-2 rounded-full bg-brand live" />CONECTADO
          </div>
          <button className="btn btn-ghost w-full" onClick={() => setOrders((o) => [...o])}><RefreshCw size={16} />Refrescar</button>
          <button className="btn btn-ghost w-full" onClick={() => setDark(!dark)}>{dark ? <Sun size={16} /> : <Moon size={16} />}{dark ? 'Modo claro' : 'Modo oscuro'}</button>
        </div>
      </aside>
      <main className="flex-1 min-w-0 p-5 lg:p-10 max-w-[1400px]">
        {view === 'panel' && <Panel s={store} go={setView} />}
        {view === 'mesas' && <Mesas s={store} />}
        {view === 'catalogo' && <Catalogo s={store} />}
        {view === 'proveedores' && <Proveedores s={store} />}
        {view === 'gastos' && <Gastos s={store} />}
        {view === 'conexion' && <Conexion s={store} />}
        {view === 'personalizar' && <Personalizar s={store} />}
      </main>
    </div>
  )
}
