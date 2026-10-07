import { Area, AreaChart, Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis, Cell } from 'recharts'
import { ArrowUpRight, TrendingUp, ReceiptText, Armchair, Wallet } from 'lucide-react'
import type { Store } from '../App'
import { money } from '../data'
import { Header, Pill } from './ui'

const tip = { borderRadius: 12, border: '1px solid var(--color-line)', background: 'var(--color-surface)', color: 'var(--color-ink)' }

export default function Panel({ s, go }: { s: Store; go: (v: any) => void }) {
  const openTotal = s.tables.filter((t) => t.open).reduce((a, t) => a + t.lines.reduce((x, l) => x + l.qty * (s.products.find((p) => p.id === l.productId)?.price ?? 0), 0), 0)
  const sold = s.orders.reduce((a, o) => a + o.total, 0)
  const spent = s.expenses.filter((e) => e.date === 'Hoy').reduce((a, e) => a + e.amount, 0)
  const purch = s.suppliers.flatMap((x) => x.purchases.map((c) => ({ ...c, sup: x.name, amount: c.qty * c.price })))
  const bought = purch.reduce((a, c) => a + c.amount, 0)
  const pending = purch.filter((c) => !c.paid).reduce((a, c) => a + c.amount, 0)
  const paidToday = purch.filter((c) => c.paid && c.date === 'Hoy').reduce((a, c) => a + c.amount, 0)
  const bySup = s.suppliers.map((x) => {
    const ps = x.purchases.map((c) => ({ ...c, amount: c.qty * c.price }))
    return { id: x.id, name: x.name, category: x.category, total: ps.reduce((a, c) => a + c.amount, 0), owed: ps.filter((c) => !c.paid).reduce((a, c) => a + c.amount, 0) }
  }).sort((a, b) => b.owed - a.owed)
  const avg = s.orders.length ? sold / s.orders.length : 0

  const hours = Array.from({ length: 12 }, (_, i) => ({ h: `${i + 11}h`, v: s.orders.filter((o) => o.hour === i + 11).reduce((a, o) => a + o.total, 0) }))
  const byTable = Object.entries(s.orders.reduce<Record<string, { total: number; n: number }>>((m, o) => {
    m[o.table] = { total: (m[o.table]?.total ?? 0) + o.total, n: (m[o.table]?.n ?? 0) + 1 }; return m
  }, {})).map(([table, v]) => ({ table, ...v })).sort((a, b) => b.total - a.total)
  const prods: Record<string, number> = {}
  s.orders.forEach((o) => o.items.forEach((i) => (prods[i.name] = (prods[i.name] ?? 0) + i.qty)))
  const top = Object.entries(prods).sort((a, b) => b[1] - a[1]).slice(0, 5)
  const maxTop = top[0]?.[1] ?? 1

  const kpis = [
    { label: 'Ventas cobradas', value: money(sold), icon: TrendingUp, note: '+12% vs. ayer', hero: true },
    { label: 'Órdenes', value: String(s.orders.length), icon: ReceiptText, note: 'cerradas hoy' },
    { label: 'Ticket promedio', value: money(avg), icon: Wallet, note: 'por orden' },
    { label: 'En mesas abiertas', value: money(openTotal), icon: Armchair, note: `${s.tables.filter((t) => t.open).length} mesas en curso` },
  ]

  return (
    <>
      <Header title="Panel" sub={`Resumen del servicio de hoy · ${s.name}`}
        right={<button className="btn btn-primary" onClick={() => go('conexion')}>Conectar app móvil <ArrowUpRight size={16} /></button>} />

      <section className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {kpis.map((k) => (
          <div key={k.label} className={`rounded-[1.25rem] p-5 border ${k.hero ? 'bg-brand text-on-brand border-transparent' : 'card'}`}>
            <div className="flex items-center justify-between">
              <span className={`text-sm font-medium ${k.hero ? 'text-on-brand/80' : 'text-mute'}`}>{k.label}</span>
              <k.icon size={18} className={k.hero ? 'text-on-brand/80' : 'text-mute'} />
            </div>
            <div className="num text-2xl xl:text-[1.65rem] font-medium mt-4 tracking-tight">{k.value}</div>
            <div className={`text-xs mt-1 ${k.hero ? 'text-on-brand/70' : 'text-mute'}`}>{k.note}</div>
          </div>
        ))}
      </section>

      <section className="grid gap-4 mt-4 xl:grid-cols-[1.7fr_1fr]">
        <div className="card p-6">
          <div className="flex items-center justify-between mb-4">
            <h2 className="font-display font-bold text-xl">Ventas por hora</h2>
            <Pill tone="mute">Hoy</Pill>
          </div>
          <div className="h-64">
            <ResponsiveContainer>
              <AreaChart data={hours} margin={{ left: -10, right: 4, top: 8 }}>
                <defs><linearGradient id="g" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stopColor="var(--color-brand)" stopOpacity={0.25} /><stop offset="100%" stopColor="var(--color-brand)" stopOpacity={0} /></linearGradient></defs>
                <CartesianGrid vertical={false} stroke="var(--color-line)" />
                <XAxis dataKey="h" tickLine={false} axisLine={false} tick={{ fontSize: 12, fill: 'var(--color-mute)' }} />
                <YAxis tickLine={false} axisLine={false} tick={{ fontSize: 12, fill: 'var(--color-mute)' }} tickFormatter={(v) => `${v / 1000}k`} />
                <Tooltip formatter={(v) => money(Number(v))} contentStyle={tip} />
                <Area type="monotone" dataKey="v" name="Ventas" stroke="var(--color-brand)" strokeWidth={2.5} fill="url(#g)" />
              </AreaChart>
            </ResponsiveContainer>
          </div>
        </div>

        <div className="card p-6">
          <h2 className="font-display font-bold text-xl mb-4">Más pedidos</h2>
          <ul className="space-y-4">
            {top.map(([n, q], i) => (
              <li key={n}>
                <div className="flex justify-between text-sm mb-1.5"><span className="font-semibold">{i + 1}. {n}</span><span className="num text-mute">{q} u.</span></div>
                <div className="h-2 rounded-full bg-sunk"><div className="h-full rounded-full bg-brand" style={{ width: `${(q / maxTop) * 100}%` }} /></div>
              </li>
            ))}
          </ul>
        </div>
      </section>

      <section className="grid gap-4 mt-4 xl:grid-cols-[1fr_1.3fr]">
        <div className="card p-6">
          <div className="flex items-center justify-between mb-1">
            <h2 className="font-display font-bold text-xl">Compras por mesa</h2>
            <span className="text-xs text-mute">{byTable.length} mesas</span>
          </div>
          <p className="text-sm text-mute mb-3">Total cobrado de cada mesa hoy</p>
          <div className="h-64">
            <ResponsiveContainer>
              <BarChart data={byTable} layout="vertical" margin={{ left: 10, right: 10 }}>
                <XAxis type="number" hide />
                <YAxis type="category" dataKey="table" tickLine={false} axisLine={false} width={80} tick={{ fontSize: 12, fill: 'var(--color-ink)' }} />
                <Tooltip cursor={{ fill: 'transparent' }} formatter={(v) => money(Number(v))} contentStyle={tip} />
                <Bar dataKey="total" name="Total" radius={8} barSize={16}>
                  {byTable.map((_, i) => <Cell key={i} fill={i === 0 ? 'var(--color-sun)' : 'var(--color-brand)'} />)}
                </Bar>
              </BarChart>
            </ResponsiveContainer>
          </div>
        </div>

        <div className="card p-6 overflow-x-auto">
          <h2 className="font-display font-bold text-xl mb-4">Órdenes recientes</h2>
          <table className="w-full text-sm min-w-[480px]">
            <thead><tr className="text-left text-mute text-xs uppercase tracking-wide"><th className="pb-3 font-semibold">Mesa</th><th className="pb-3 font-semibold">Hora</th><th className="pb-3 font-semibold">Pago</th><th className="pb-3 font-semibold text-right">Total</th></tr></thead>
            <tbody>
              {[...s.orders].reverse().slice(0, 7).map((o) => (
                <tr key={o.id} className="border-t border-line">
                  <td className="py-3 font-semibold">{o.table}</td>
                  <td className="py-3 num text-mute">{o.hour}:00</td>
                  <td className="py-3"><Pill tone="mute">{o.method}</Pill></td>
                  <td className="py-3 num text-right">{money(o.total)}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <div className="mt-3 text-xs text-mute">Gastos de hoy: <span className="num">{money(spent)}</span> · Compras pagadas a proveedores: <span className="num">{money(paidToday)}</span> · Neto: <span className="num text-brand font-medium">{money(sold - spent - paidToday)}</span></div>
        </div>
      </section>
      <section className="card p-6 mt-4">
        <div className="flex flex-wrap items-center justify-between gap-3 mb-5">
          <div>
            <h2 className="font-display font-bold text-xl">Cuentas con proveedores</h2>
            <p className="text-sm text-mute">Lo comprado y lo que todavía debes</p>
          </div>
          <button className="btn btn-ghost" onClick={() => go('proveedores')}>Ver proveedores <ArrowUpRight size={16} /></button>
        </div>
        <div className="grid gap-6 lg:grid-cols-[auto_1fr]">
          <div className="grid grid-cols-3 lg:grid-cols-1 gap-4 lg:min-w-52">
            {[['Total comprado', bought, ''], ['Pendiente de pago', pending, 'text-berry'], ['Pagado', bought - pending, 'text-brand']].map(([l, v, c]) => (
              <div key={l as string}><div className="text-xs text-mute">{l}</div><div className={`num text-xl ${c}`}>{money(v as number)}</div></div>
            ))}
          </div>
          <ul className="space-y-4">
            {bySup.map((x) => (
              <li key={x.id}>
                <div className="flex justify-between text-sm mb-1.5">
                  <span className="font-semibold">{x.name} <span className="text-mute font-normal">· {x.category}</span></span>
                  <span className="num">{x.owed > 0 ? <span className="text-berry">debes {money(x.owed)}</span> : <span className="text-brand">al día</span>}</span>
                </div>
                <div className="h-2 rounded-full bg-sunk overflow-hidden flex">
                  <div className="bg-brand" style={{ width: `${x.total ? ((x.total - x.owed) / bought) * 100 : 0}%` }} />
                  <div className="bg-berry" style={{ width: `${bought ? (x.owed / bought) * 100 : 0}%` }} />
                </div>
              </li>
            ))}
            {bySup.length === 0 && <li className="text-mute text-sm">Aún no hay proveedores registrados.</li>}
          </ul>
        </div>
      </section>
    </>
  )
}
