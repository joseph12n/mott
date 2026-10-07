import { useState } from 'react'
import { Plus, Trash2 } from 'lucide-react'
import type { Store } from '../App'
import { money, uid } from '../data'
import { Header } from './ui'

export default function Gastos({ s }: { s: Store }) {
  const [c, setC] = useState(''); const [a, setA] = useState('')
  const total = s.expenses.reduce((x, e) => x + e.amount, 0)
  return (
    <>
      <Header title="Gastos" sub="Egresos registrados del servicio" right={<div className="num text-3xl font-medium">{money(total)}</div>} />
      <div className="card p-4 flex flex-wrap gap-3 mb-6">
        <input className="field flex-1 min-w-48" placeholder="Concepto" value={c} onChange={(e) => setC(e.target.value)} />
        <input className="field w-40 num" type="number" placeholder="Monto" value={a} onChange={(e) => setA(e.target.value)} />
        <button className="btn btn-primary" disabled={!c.trim() || !+a} onClick={() => { s.setExpenses((l) => [{ id: uid(), concept: c.trim(), amount: +a, date: 'Hoy' }, ...l]); setC(''); setA('') }}><Plus size={16} />Registrar gasto</button>
      </div>
      <div className="card divide-y divide-line">
        {s.expenses.map((e) => (
          <div key={e.id} className="flex items-center gap-4 px-5 py-4">
            <div className="flex-1"><div className="font-semibold">{e.concept}</div><div className="text-xs text-mute">{e.date}</div></div>
            <div className="num">{money(e.amount)}</div>
            <button onClick={() => s.setExpenses((l) => l.filter((x) => x.id !== e.id))} className="size-8 rounded-lg grid place-items-center text-mute hover:bg-sunk hover:text-berry"><Trash2 size={15} /></button>
          </div>
        ))}
      </div>
    </>
  )
}
