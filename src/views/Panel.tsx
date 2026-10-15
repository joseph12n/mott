import React, { useMemo } from 'react'
import { Text, View } from 'react-native'

import { money } from '../data'
import { useStore } from '../store'
import { usePalette } from '../usePalette'
import { Bar, Btn, Card, Header, Num, Pill, styles as ui } from '../ui'

// Panel ported from the web master (views/Panel.tsx). The master charts with
// recharts; here the same data is drawn with plain Views (labelled bars), so
// the app ships no charting or SVG dependency and still shows every number
// the web shows.
export default function Panel({ go }: { go: (v: 'conexion' | 'proveedores') => void }) {
  const s = useStore()
  const p = usePalette()

  const stats = useMemo(() => {
    const price = (id: string) => s.products.find((x) => x.id === id)?.price ?? 0
    const openTotal = s.tables
      .filter((t) => t.open)
      .reduce((a, t) => a + t.lines.reduce((x, l) => x + l.qty * price(l.productId), 0), 0)
    const sold = s.orders.reduce((a, o) => a + o.total, 0)
    const spent = s.expenses.filter((e) => e.date === 'Hoy').reduce((a, e) => a + e.amount, 0)
    const purchases = s.suppliers.flatMap((x) =>
      x.purchases.map((c) => ({ ...c, amount: c.qty * c.price })),
    )
    const bought = purchases.reduce((a, c) => a + c.amount, 0)
    const pending = purchases.filter((c) => !c.paid).reduce((a, c) => a + c.amount, 0)
    const paidToday = purchases
      .filter((c) => c.paid && c.date === 'Hoy')
      .reduce((a, c) => a + c.amount, 0)
    const bySupplier = s.suppliers
      .map((x) => {
        const rows = x.purchases.map((c) => ({ ...c, amount: c.qty * c.price }))
        return {
          id: x.id,
          name: x.name,
          category: x.category,
          total: rows.reduce((a, c) => a + c.amount, 0),
          owed: rows.filter((c) => !c.paid).reduce((a, c) => a + c.amount, 0),
        }
      })
      .sort((a, b) => b.owed - a.owed)
    const hours = Array.from({ length: 12 }, (_, i) => ({
      label: `${i + 11}h`,
      value: s.orders.filter((o) => o.hour === i + 11).reduce((a, o) => a + o.total, 0),
    }))
    const byTableMap: Record<string, { total: number; n: number }> = {}
    s.orders.forEach((o) => {
      byTableMap[o.table] = {
        total: (byTableMap[o.table]?.total ?? 0) + o.total,
        n: (byTableMap[o.table]?.n ?? 0) + 1,
      }
    })
    const byTable = Object.entries(byTableMap)
      .map(([table, v]) => ({ table, ...v }))
      .sort((a, b) => b.total - a.total)
    const counts: Record<string, number> = {}
    s.orders.forEach((o) => o.items.forEach((i) => (counts[i.name] = (counts[i.name] ?? 0) + i.qty)))
    const top = Object.entries(counts)
      .sort((a, b) => b[1] - a[1])
      .slice(0, 5)
    return {
      openTotal,
      sold,
      spent,
      bought,
      pending,
      paidToday,
      bySupplier,
      hours,
      byTable,
      top,
      avg: s.orders.length ? sold / s.orders.length : 0,
      openTables: s.tables.filter((t) => t.open).length,
    }
  }, [s.products, s.tables, s.orders, s.expenses, s.suppliers])

  const maxHour = Math.max(1, ...stats.hours.map((h) => h.value))
  const maxTop = stats.top[0]?.[1] ?? 1
  const maxTable = Math.max(1, ...stats.byTable.map((t) => t.total))

  const kpis = [
    { label: 'Ventas cobradas', value: money(stats.sold), note: '+12% vs. ayer', hero: true },
    { label: 'Órdenes', value: String(s.orders.length), note: 'cerradas hoy', hero: false },
    { label: 'Ticket promedio', value: money(stats.avg), note: 'por orden', hero: false },
    { label: 'En mesas abiertas', value: money(stats.openTotal), note: `${stats.openTables} mesas en curso`, hero: false },
  ]

  return (
    <View style={{ gap: 16 }}>
      <Header
        p={p}
        title="Panel"
        sub={`Resumen del servicio de hoy · ${s.name}`}
        right={<Btn p={p} label="Conectar app móvil" onPress={() => go('conexion')} />}
      />

      <View style={{ gap: 12 }}>
        {kpis.map((k) => (
          <Card
            key={k.label}
            p={p}
            style={k.hero ? { backgroundColor: p.brand, borderColor: p.brand } : undefined}
          >
            <View style={{ flexDirection: 'row', justifyContent: 'space-between' }}>
              <Text style={[ui.btnLabel, { color: k.hero ? p.onBrand : p.mute }]}>{k.label}</Text>
            </View>
            <View style={{ marginTop: 10 }}>
              <Num p={p} value={k.value} size={26} color={k.hero ? p.onBrand : p.ink} />
            </View>
            <Text style={[ui.note, { color: k.hero ? p.onBrand : p.mute, marginTop: 4 }]}>{k.note}</Text>
          </Card>
        ))}
      </View>

      <Card p={p}>
        <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
          <Text style={[ui.title, { color: p.ink }]}>Ventas por hora</Text>
          <Pill p={p} tone="mute">
            Hoy
          </Pill>
        </View>
        <View style={{ marginTop: 14, gap: 8 }}>
          {stats.hours.map((h) => (
            <View key={h.label} style={{ gap: 4 }}>
              <View style={{ flexDirection: 'row', justifyContent: 'space-between' }}>
                <Text style={[ui.note, { color: p.mute }]}>{h.label}</Text>
                <Num p={p} value={money(h.value)} size={12} color={p.mute} />
              </View>
              <Bar p={p} fraction={h.value / maxHour} />
            </View>
          ))}
        </View>
      </Card>

      <Card p={p}>
        <Text style={[ui.title, { color: p.ink }]}>Más pedidos</Text>
        <View style={{ marginTop: 14, gap: 14 }}>
          {stats.top.map(([name, qty], i) => (
            <View key={name} style={{ gap: 6 }}>
              <View style={{ flexDirection: 'row', justifyContent: 'space-between' }}>
                <Text style={[ui.btnLabel, { color: p.ink, flex: 1 }]}>
                  {i + 1}. {name}
                </Text>
                <Num p={p} value={`${qty} u.`} size={12} color={p.mute} />
              </View>
              <Bar p={p} fraction={qty / maxTop} />
            </View>
          ))}
          {stats.top.length === 0 ? (
            <Text style={[ui.sub, { color: p.mute }]}>Todavía no hay pedidos.</Text>
          ) : null}
        </View>
      </Card>

      <Card p={p}>
        <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
          <Text style={[ui.title, { color: p.ink }]}>Compras por mesa</Text>
          <Text style={[ui.note, { color: p.mute }]}>{stats.byTable.length} mesas</Text>
        </View>
        <Text style={[ui.sub, { color: p.mute, marginTop: 4 }]}>Total cobrado de cada mesa hoy</Text>
        <View style={{ marginTop: 12, gap: 12 }}>
          {stats.byTable.map((row, i) => (
            <View key={row.table} style={{ gap: 6 }}>
              <View style={{ flexDirection: 'row', justifyContent: 'space-between' }}>
                <Text style={[ui.btnLabel, { color: p.ink }]}>{row.table}</Text>
                <Num p={p} value={money(row.total)} size={12} color={p.mute} />
              </View>
              <Bar p={p} fraction={row.total / maxTable} color={i === 0 ? p.sun : p.brand} />
            </View>
          ))}
        </View>
      </Card>

      <Card p={p}>
        <Text style={[ui.title, { color: p.ink }]}>Órdenes recientes</Text>
        <View style={{ marginTop: 12, gap: 10 }}>
          {[...s.orders]
            .reverse()
            .slice(0, 7)
            .map((o) => (
              <View
                key={o.id}
                style={{ flexDirection: 'row', alignItems: 'center', gap: 8, borderTopWidth: 1, borderTopColor: p.line, paddingTop: 10 }}
              >
                <Text style={[ui.btnLabel, { color: p.ink, flex: 1 }]} numberOfLines={1}>
                  {o.table}
                </Text>
                <Num p={p} value={`${o.hour}:00`} size={12} color={p.mute} />
                <Pill p={p} tone="mute">
                  {o.method}
                </Pill>
                <Num p={p} value={money(o.total)} size={13} />
              </View>
            ))}
        </View>
        <Text style={[ui.note, { color: p.mute, marginTop: 12 }]}>
          Gastos de hoy: {money(stats.spent)} · Compras pagadas a proveedores: {money(stats.paidToday)} · Neto:{' '}
          <Text style={{ color: p.brand }}>{money(stats.sold - stats.spent - stats.paidToday)}</Text>
        </Text>
      </Card>

      <Card p={p}>
        <Text style={[ui.title, { color: p.ink }]}>Cuentas con proveedores</Text>
        <Text style={[ui.sub, { color: p.mute, marginTop: 4 }]}>Lo comprado y lo que todavía debes</Text>
        <View style={{ flexDirection: 'row', gap: 16, marginTop: 14, flexWrap: 'wrap' }}>
          <View style={{ minWidth: 120 }}>
            <Text style={[ui.note, { color: p.mute }]}>Total comprado</Text>
            <Num p={p} value={money(stats.bought)} size={18} />
          </View>
          <View style={{ minWidth: 120 }}>
            <Text style={[ui.note, { color: p.mute }]}>Pendiente de pago</Text>
            <Num p={p} value={money(stats.pending)} size={18} color={p.berry} />
          </View>
          <View style={{ minWidth: 120 }}>
            <Text style={[ui.note, { color: p.mute }]}>Pagado</Text>
            <Num p={p} value={money(stats.bought - stats.pending)} size={18} color={p.brand} />
          </View>
        </View>
        <View style={{ marginTop: 16, gap: 14 }}>
          {stats.bySupplier.map((x) => (
            <View key={x.id} style={{ gap: 6 }}>
              <View style={{ flexDirection: 'row', justifyContent: 'space-between', gap: 8 }}>
                <Text style={[ui.btnLabel, { color: p.ink, flex: 1 }]} numberOfLines={1}>
                  {x.name} <Text style={{ color: p.mute, fontWeight: '400' }}>· {x.category}</Text>
                </Text>
                <Num
                  p={p}
                  value={x.owed > 0 ? `debes ${money(x.owed)}` : 'al día'}
                  size={12}
                  color={x.owed > 0 ? p.berry : p.brand}
                />
              </View>
              <View style={{ flexDirection: 'row', height: 8, borderRadius: 4, backgroundColor: p.sunk, overflow: 'hidden' }}>
                <View
                  style={{
                    width: `${stats.bought ? ((x.total - x.owed) / stats.bought) * 100 : 0}%`,
                    backgroundColor: p.brand,
                  }}
                />
                <View
                  style={{ width: `${stats.bought ? (x.owed / stats.bought) * 100 : 0}%`, backgroundColor: p.berry }}
                />
              </View>
            </View>
          ))}
          {stats.bySupplier.length === 0 ? (
            <Text style={[ui.sub, { color: p.mute }]}>Aún no hay proveedores registrados.</Text>
          ) : null}
        </View>
        <View style={{ marginTop: 12 }}>
          <Btn p={p} label="Ver proveedores" variant="ghost" onPress={() => go('proveedores')} />
        </View>
      </Card>
    </View>
  )
}
