import { useState } from 'react'
import { Plus, Trash2, Wifi, WifiOff, X } from 'lucide-react'
import type { Store } from '../App'
import { money, uid } from '../data'
import { Header, Pill } from './ui'

export default function Mesas({ s }: { s: Store }) {
  const [name, setName] = useState('')
  const [free, setFree] = useState('')
  const [pick, setPick] = useState<Record<string, { p: string; q: number }>>({})
  const price = (id: string) => s.products.find((p) => p.id === id)?.price ?? 0
  const total = (t: (typeof s.tables)[0]) => t.lines.reduce((a, l) => a + l.qty * price(l.productId), 0)
  const upd = (id: string, f: (t: (typeof s.tables)[0]) => any) => s.setTables((ts) => ts.map((t) => (t.id === id ? f(t) : t)))
  const freeTables = s.tables.filter((t) => !t.open)
  const openTables = s.tables.filter((t) => t.open)

  const add = (id: string) => {
    const c = pick[id]; if (!c?.p) return
    upd(id, (t) => {
      const ex = t.lines.find((l) => l.productId === c.p)
      return { ...t, lines: ex ? t.lines.map((l) => (l === ex ? { ...l, qty: l.qty + c.q } : l)) : [...t.lines, { productId: c.p, qty: c.q }] }
    })
  }
  const close = (t: (typeof s.tables)[0]) => {
    if (t.lines.length) s.setOrders((o) => [...o, {
      id: uid(), table: t.name, hour: new Date().getHours(), method: 'Efectivo', total: total(t),
      items: t.lines.map((l) => ({ name: s.products.find((p) => p.id === l.productId)?.name ?? '', qty: l.qty, price: price(l.productId) })),
    }])
    upd(t.id, (x) => ({ ...x, open: false, lines: [] }))
  }

  return (
    <>
      <Header title="Mesas" sub={`${openTables.length} abiertas · ${money(openTables.reduce((a, t) => a + total(t), 0))} en curso`} />

      <div className="card p-4 flex flex-wrap gap-3 items-center mb-4">
        <select className="field w-48" value={free} onChange={(e) => setFree(e.target.value)}>
          <option value="">Mesa libre…</option>
          {freeTables.map((t) => <option key={t.id} value={t.id}>{t.name}</option>)}
        </select>
        <button className="btn btn-primary" disabled={!free} onClick={() => { upd(free, (t) => ({ ...t, open: true })); setFree('') }}>Abrir mesa</button>
        <div className="hidden sm:block w-px h-8 bg-line mx-2" />
        <input className="field w-48" placeholder="Nombre de nueva mesa" value={name} onChange={(e) => setName(e.target.value)} />
        <button className="btn btn-ghost" disabled={!name.trim()} onClick={() => { s.setTables((t) => [...t, { id: uid(), name: name.trim(), open: false, synced: false, lines: [] }]); setName('') }}><Plus size={16} />Agregar mesa</button>
        <span className="text-xs text-mute">{freeTables.length ? `${freeTables.length} libres` : 'Sin mesas libres.'}</span>
      </div>

      <div className="card p-4 mb-6">
        <div className="text-xs font-bold uppercase tracking-wide text-mute mb-3">Todas las mesas</div>
        <div className="flex flex-wrap gap-2">
          {s.tables.map((t) => (
            <div key={t.id} className="flex items-center gap-2 rounded-xl bg-sunk pl-3 pr-1.5 h-10 text-sm">
              <span className={`size-2 rounded-full ${t.open ? 'bg-berry' : 'bg-brand'}`} />
              <b>{t.name}</b>
              <span className="text-mute text-xs">{t.open ? 'Ocupada' : 'Libre'}</span>
              <button disabled={t.open} title={t.open ? 'Con cuenta abierta' : 'Eliminar'} onClick={() => s.setTables((ts) => ts.filter((x) => x.id !== t.id))}
                className="size-7 rounded-lg grid place-items-center text-mute hover:bg-surface hover:text-berry disabled:opacity-30 disabled:hover:bg-transparent disabled:hover:text-mute"><X size={14} /></button>
            </div>
          ))}
        </div>
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        {openTables.map((t) => {
          const c = pick[t.id] ?? { p: '', q: 1 }
          return (
            <article key={t.id} className="card p-5 flex flex-col">
              <div className="flex items-center gap-3 flex-wrap">
                <h3 className="font-display font-bold text-xl">{t.name}</h3>
                <Pill tone="sun">Ocupada</Pill>
                <div className="ml-auto">
                  {t.synced
                    ? <Pill><Wifi size={12} />En la app</Pill>
                    : <button className="btn btn-ghost !h-8 !px-3 !text-xs" onClick={() => upd(t.id, (x) => ({ ...x, synced: true }))}><WifiOff size={12} />Registrar mesa</button>}
                </div>
              </div>
              <ul className="my-4 text-sm flex-1 min-h-12">
                {t.lines.length === 0 && <li className="text-mute">Sin consumos.</li>}
                {t.lines.map((l) => {
                  const p = s.products.find((x) => x.id === l.productId)
                  return (
                    <li key={l.productId} className="flex justify-between py-2 border-b border-line last:border-0">
                      <span><span className="num text-mute mr-2">{l.qty}×</span>{p?.name}</span>
                      <span className="num">{money(l.qty * (p?.price ?? 0))}</span>
                    </li>
                  )
                })}
              </ul>
              <div className="num text-4xl font-medium tracking-tight mb-4">{money(total(t))}</div>
              <div className="flex flex-wrap gap-2">
                <select className="field flex-1 min-w-36" value={c.p} onChange={(e) => setPick({ ...pick, [t.id]: { ...c, p: e.target.value } })}>
                  <option value="">Producto…</option>
                  {s.products.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
                </select>
                <input type="number" min={1} className="field w-20 num" value={c.q} onChange={(e) => setPick({ ...pick, [t.id]: { ...c, q: Math.max(1, +e.target.value || 1) } })} />
                <button className="btn btn-primary" disabled={!c.p} onClick={() => add(t.id)}>Agregar</button>
                <button className="btn btn-danger" onClick={() => close(t)}>Cerrar</button>
              </div>
            </article>
          )
        })}
        {openTables.length === 0 && <div className="card p-10 text-center text-mute lg:col-span-2">No hay mesas abiertas. Abre una desde el selector de arriba.</div>}
      </div>
    </>
  )
}
