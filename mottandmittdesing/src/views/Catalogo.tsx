import { useState } from 'react'
import { Plus, Trash2 } from 'lucide-react'
import type { Store } from '../App'
import { money, uid } from '../data'
import { Header, Pill } from './ui'

export default function Catalogo({ s }: { s: Store }) {
  const [n, setN] = useState(''); const [p, setP] = useState(''); const [c, setC] = useState('Platos')
  const cats = [...new Set(s.products.map((x) => x.category))]
  return (
    <>
      <Header title="Catálogo" sub={`${s.products.length} productos disponibles para la app móvil`} />
      <div className="card p-4 flex flex-wrap gap-3 mb-6">
        <input className="field flex-1 min-w-48" placeholder="Nombre del producto" value={n} onChange={(e) => setN(e.target.value)} />
        <input className="field w-32 num" type="number" placeholder="Precio" value={p} onChange={(e) => setP(e.target.value)} />
        <input className="field w-36" list="cats" value={c} onChange={(e) => setC(e.target.value)} />
        <datalist id="cats">{cats.map((x) => <option key={x} value={x} />)}</datalist>
        <button className="btn btn-primary" disabled={!n.trim() || !+p} onClick={() => { s.setProducts((l) => [...l, { id: uid(), name: n.trim(), price: +p, category: c || 'General' }]); setN(''); setP('') }}><Plus size={16} />Agregar producto</button>
      </div>
      {cats.map((cat) => (
        <section key={cat} className="mb-6">
          <h2 className="text-xs font-bold uppercase tracking-wide text-mute mb-3">{cat}</h2>
          <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
            {s.products.filter((x) => x.category === cat).map((x) => (
              <div key={x.id} className="card p-4 flex items-center gap-3 group">
                <div className="size-11 rounded-xl bg-brand-soft text-brand grid place-items-center font-display font-extrabold">{x.name[0]}</div>
                <div className="flex-1 min-w-0"><div className="font-semibold truncate">{x.name}</div><div className="num text-sm text-mute">{money(x.price)}</div></div>
                <Pill>En la app</Pill>
                <button title="Eliminar" onClick={() => s.setProducts((l) => l.filter((y) => y.id !== x.id))} className="size-8 rounded-lg grid place-items-center text-mute hover:bg-sunk hover:text-berry"><Trash2 size={15} /></button>
              </div>
            ))}
          </div>
        </section>
      ))}
    </>
  )
}
