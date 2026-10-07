import { useState } from 'react'
import { Plus, Trash2, Phone, CheckCircle2, Truck } from 'lucide-react'
import type { Store } from '../App'
import { Supplier, money, uid } from '../data'
import { Header, Pill } from './ui'

export default function Proveedores({ s }: { s: Store }) {
  const [sel, setSel] = useState(s.suppliers[0]?.id ?? '')
  const [f, setF] = useState({ name: '', phone: '', category: '' })
  const [np, setNp] = useState({ name: '', price: '', unit: 'u.' })
  const [buy, setBuy] = useState({ pid: '', qty: 1 })
  const sup = s.suppliers.find((x) => x.id === sel)
  const upd = (id: string, fn: (x: Supplier) => Supplier) => s.setSuppliers((l) => l.map((x) => (x.id === id ? fn(x) : x)))
  const owed = (x: Supplier) => x.purchases.filter((c) => !c.paid).reduce((a, c) => a + c.qty * c.price, 0)
  const totalOwed = s.suppliers.reduce((a, x) => a + owed(x), 0)

  const addSup = () => {
    const id = uid()
    s.setSuppliers((l) => [...l, { id, name: f.name.trim(), phone: f.phone, category: f.category || 'General', products: [], purchases: [] }])
    setSel(id); setF({ name: '', phone: '', category: '' })
  }

  return (
    <>
      <Header title="Proveedores" sub={`${s.suppliers.length} proveedores · lo que ofrecen, a qué precio y cuánto se les debe`}
        right={<div className="text-right"><div className="text-xs text-mute">Pendiente de pago</div><div className="num text-3xl text-berry">{money(totalOwed)}</div></div>} />

      <div className="card p-4 flex flex-wrap gap-3 mb-6">
        <input className="field flex-1 min-w-44" placeholder="Nombre del proveedor" value={f.name} onChange={(e) => setF({ ...f, name: e.target.value })} />
        <input className="field w-44" placeholder="Teléfono" value={f.phone} onChange={(e) => setF({ ...f, phone: e.target.value })} />
        <input className="field w-40" placeholder="Rubro (Carnes…)" value={f.category} onChange={(e) => setF({ ...f, category: e.target.value })} />
        <button className="btn btn-primary" disabled={!f.name.trim()} onClick={addSup}><Plus size={16} />Agregar proveedor</button>
      </div>

      <div className="grid gap-4 xl:grid-cols-[340px_1fr]">
        <div className="space-y-3">
          {s.suppliers.map((x) => (
            <button key={x.id} onClick={() => setSel(x.id)} className={`card w-full text-left p-4 transition ${sel === x.id ? 'ring-2 ring-brand' : 'hover:bg-sunk'}`}>
              <div className="flex items-center gap-3">
                <div className="size-10 rounded-xl bg-brand-soft text-brand grid place-items-center"><Truck size={18} /></div>
                <div className="flex-1 min-w-0"><div className="font-semibold truncate">{x.name}</div><div className="text-xs text-mute">{x.category} · {x.products.length} productos</div></div>
              </div>
              <div className="flex justify-between items-center mt-3 text-sm">
                <span className="text-mute">Saldo</span>
                {owed(x) > 0 ? <span className="num text-berry">{money(owed(x))}</span> : <Pill>Al día</Pill>}
              </div>
            </button>
          ))}
          {s.suppliers.length === 0 && <div className="card p-8 text-center text-mute">Agrega tu primer proveedor.</div>}
        </div>

        {sup ? (
          <div className="space-y-4">
            <div className="card p-6">
              <div className="flex flex-wrap items-start gap-3">
                <div>
                  <h2 className="font-display font-bold text-2xl">{sup.name}</h2>
                  <div className="flex items-center gap-3 text-sm text-mute mt-1"><Pill tone="mute">{sup.category}</Pill>{sup.phone && <span className="flex items-center gap-1"><Phone size={13} />{sup.phone}</span>}</div>
                </div>
                <button className="btn btn-danger ml-auto" onClick={() => { s.setSuppliers((l) => l.filter((x) => x.id !== sup.id)); setSel(s.suppliers.find((x) => x.id !== sup.id)?.id ?? '') }}><Trash2 size={15} />Eliminar</button>
              </div>

              <h3 className="text-xs font-bold uppercase tracking-wide text-mute mt-6 mb-3">Productos que vende</h3>
              <ul className="divide-y divide-line">
                {sup.products.map((p) => (
                  <li key={p.id} className="flex items-center gap-3 py-2.5 text-sm">
                    <span className="flex-1 font-semibold">{p.name}</span>
                    <span className="num">{money(p.price)} <span className="text-mute">/ {p.unit}</span></span>
                    <button onClick={() => upd(sup.id, (x) => ({ ...x, products: x.products.filter((y) => y.id !== p.id) }))} className="size-8 rounded-lg grid place-items-center text-mute hover:bg-sunk hover:text-berry"><Trash2 size={14} /></button>
                  </li>
                ))}
                {sup.products.length === 0 && <li className="py-3 text-sm text-mute">Sin productos todavía.</li>}
              </ul>
              <div className="flex flex-wrap gap-2 mt-3">
                <input className="field flex-1 min-w-40" placeholder="Producto" value={np.name} onChange={(e) => setNp({ ...np, name: e.target.value })} />
                <input className="field w-32 num" type="number" placeholder="Precio" value={np.price} onChange={(e) => setNp({ ...np, price: e.target.value })} />
                <input className="field w-24" placeholder="Unidad" value={np.unit} onChange={(e) => setNp({ ...np, unit: e.target.value })} />
                <button className="btn btn-ghost" disabled={!np.name.trim() || !+np.price} onClick={() => { upd(sup.id, (x) => ({ ...x, products: [...x.products, { id: uid(), name: np.name.trim(), price: +np.price, unit: np.unit || 'u.' }] })); setNp({ name: '', price: '', unit: 'u.' }) }}><Plus size={16} />Agregar</button>
              </div>
            </div>

            <div className="card p-6">
              <div className="flex flex-wrap items-center justify-between gap-3 mb-4">
                <h3 className="font-display font-bold text-xl">Compras y cuenta</h3>
                {owed(sup) > 0 && <button className="btn btn-primary !h-9" onClick={() => upd(sup.id, (x) => ({ ...x, purchases: x.purchases.map((c) => ({ ...c, paid: true })) }))}><CheckCircle2 size={15} />Saldar {money(owed(sup))}</button>}
              </div>
              <div className="flex flex-wrap gap-2 mb-4">
                <select className="field flex-1 min-w-44" value={buy.pid} onChange={(e) => setBuy({ ...buy, pid: e.target.value })}>
                  <option value="">Producto comprado…</option>
                  {sup.products.map((p) => <option key={p.id} value={p.id}>{p.name} · {money(p.price)}</option>)}
                </select>
                <input className="field w-24 num" type="number" min={1} value={buy.qty} onChange={(e) => setBuy({ ...buy, qty: Math.max(1, +e.target.value || 1) })} />
                <button className="btn btn-primary" disabled={!buy.pid} onClick={() => {
                  const p = sup.products.find((y) => y.id === buy.pid)!
                  upd(sup.id, (x) => ({ ...x, purchases: [{ id: uid(), productName: p.name, qty: buy.qty, price: p.price, paid: false, date: 'Hoy' }, ...x.purchases] })); setBuy({ pid: '', qty: 1 })
                }}>Registrar compra</button>
              </div>
              <ul className="divide-y divide-line">
                {sup.purchases.map((c) => (
                  <li key={c.id} className="flex items-center gap-3 py-3 text-sm">
                    <div className="flex-1"><div className="font-semibold">{c.productName}</div><div className="text-xs text-mute">{c.date} · <span className="num">{c.qty} × {money(c.price)}</span></div></div>
                    <span className="num">{money(c.qty * c.price)}</span>
                    <button onClick={() => upd(sup.id, (x) => ({ ...x, purchases: x.purchases.map((y) => (y.id === c.id ? { ...y, paid: !y.paid } : y)) }))}>
                      {c.paid ? <Pill>Pagado</Pill> : <Pill tone="sun">Pendiente</Pill>}
                    </button>
                  </li>
                ))}
                {sup.purchases.length === 0 && <li className="py-3 text-sm text-mute">Aún no registras compras.</li>}
              </ul>
            </div>
          </div>
        ) : <div className="card p-10 text-center text-mute">Selecciona o agrega un proveedor.</div>}
      </div>
    </>
  )
}
