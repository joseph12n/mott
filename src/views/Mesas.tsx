import { useMemo, useState } from 'react'
import { Text, useWindowDimensions, View } from 'react-native'

import { money, uid } from '../data'
import { useStore } from '../store'
import { BREAKPOINTS } from '../theme'
import { usePalette } from '../usePalette'
import { Btn, Card, Field, Header, LinkBtn, Num, StatusPill, styles as ui } from '../ui'

// Mesas ported from the web master (views/Mesas.tsx), with the dialog-free
// rule the owner asked for: the table detail is a view inside the screen
// (full width with a back button on phones, a right-hand pane on wide
// screens) instead of a modal, and closing a table needs a second tap.
export default function Mesas() {
  const s = useStore()
  const p = usePalette()
  const { width } = useWindowDimensions()
  const [selected, setSelected] = useState<string | null>(null)
  const [newName, setNewName] = useState('')
  const [pick, setPick] = useState<{ productId: string; qty: number }>({ productId: '', qty: 1 })
  const [armedClose, setArmedClose] = useState(false)
  const [armedDelete, setArmedDelete] = useState(false)

  const price = (id: string) => s.products.find((x) => x.id === id)?.price ?? 0
  const total = (lines: { productId: string; qty: number }[]) =>
    lines.reduce((a, l) => a + l.qty * price(l.productId), 0)

  const openTables = s.tables.filter((t) => t.open)
  const freeTables = s.tables.filter((t) => !t.open)
  const inProgress = openTables.reduce((a, t) => a + total(t.lines), 0)
  const table = s.tables.find((t) => t.id === selected) ?? null
  const split = width >= BREAKPOINTS.twoPane

  const upd = (id: string, fn: (t: (typeof s.tables)[number]) => (typeof s.tables)[number]) =>
    s.setTables((ts) => ts.map((t) => (t.id === id ? fn(t) : t)))

  const addItem = () => {
    if (!table || !pick.productId) return
    upd(table.id, (t) => {
      const existing = t.lines.find((l) => l.productId === pick.productId)
      return {
        ...t,
        synced: false,
        lines: existing
          ? t.lines.map((l) => (l.productId === pick.productId ? { ...l, qty: l.qty + pick.qty } : l))
          : [...t.lines, { productId: pick.productId, qty: pick.qty }],
      }
    })
    setPick({ productId: '', qty: 1 })
  }

  const closeTable = () => {
    if (!table) return
    if (!armedClose) {
      setArmedClose(true)
      return
    }
    if (table.lines.length) {
      s.setOrders((o) => [
        ...o,
        {
          id: uid(),
          table: table.name,
          hour: new Date().getHours(),
          method: 'Efectivo',
          total: total(table.lines),
          items: table.lines.map((l) => ({
            name: s.products.find((x) => x.id === l.productId)?.name ?? '',
            qty: l.qty,
            price: price(l.productId),
          })),
        },
      ])
    }
    upd(table.id, (t) => ({ ...t, open: false, synced: true, lines: [] }))
    setArmedClose(false)
    setSelected(null)
  }

  const list = (
    <View style={{ gap: 16 }}>
      <Header p={p} title="Mesas" sub={`${openTables.length} abiertas · ${money(inProgress)} en curso`} />

      <Card p={p}>
        <Text style={[ui.label, { color: p.mute }]}>Agregar mesa</Text>
        <View style={{ marginTop: 8, gap: 8 }}>
          <Field p={p} value={newName} onChange={setNewName} placeholder="Nombre de la mesa" />
          <Btn
            p={p}
            label="AGREGAR MESA"
            variant="ghost"
            disabled={!newName.trim()}
            onPress={() => {
              s.setTables((ts) => [...ts, { id: uid(), name: newName.trim(), open: false, synced: false, lines: [] }])
              setNewName('')
            }}
          />
        </View>
      </Card>

      <View style={{ gap: 12, flexDirection: 'row', flexWrap: 'wrap' }}>
        {s.tables.map((t) => (
          <Card
            key={t.id}
            p={p}
            style={{
              minWidth: split ? '48%' : '100%',
              flexGrow: 1,
              borderColor: selected === t.id ? p.brand : p.line,
              borderWidth: t.id === selected ? 2 : 1,
            }}
          >
            <View style={{ gap: 8 }}>
              <Text style={[ui.title, { color: p.ink }]} numberOfLines={1}>
                {t.name}
              </Text>
              <StatusPill p={p} on={t.open} onLabel="Ocupada" offLabel="Libre" />
              {t.open ? <Num p={p} value={money(total(t.lines))} size={16} /> : null}
              <LinkBtn p={p} label={t.open ? 'Ver cuenta' : 'Abrir mesa'} onPress={() => setSelected(t.id)} />
            </View>
          </Card>
        ))}
      </View>
      {s.tables.length === 0 ? <Text style={[ui.sub, { color: p.mute }]}>Sin mesas registradas.</Text> : null}
    </View>
  )

  const detail = table ? (
    <View style={{ gap: 16 }}>
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
        {!split ? (
          <LinkBtn
            p={p}
            label="← VOLVER"
            onPress={() => {
              setSelected(null)
              setArmedClose(false)
              setArmedDelete(false)
            }}
          />
        ) : null}
        <Text style={[ui.title, { color: p.ink, flex: 1 }]} numberOfLines={1}>
          {table.name}
        </Text>
        <StatusPill p={p} on={table.open} onLabel="Ocupada" offLabel="Libre" />
      </View>

      {!table.open ? (
        <Card p={p}>
          <Btn
            p={p}
            label="ABRIR MESA"
            onPress={() => upd(table.id, (t) => ({ ...t, open: true }))}
          />
          <View style={{ height: 10 }} />
          {armedDelete ? (
            <View style={{ gap: 8 }}>
              <Btn
                p={p}
                label="CONFIRMAR ELIMINAR"
                variant="danger"
                onPress={() => {
                  s.setTables((ts) => ts.filter((x) => x.id !== table.id))
                  setSelected(null)
                  setArmedDelete(false)
                }}
              />
              <Btn p={p} label="CANCELAR" variant="ghost" onPress={() => setArmedDelete(false)} />
            </View>
          ) : (
            <Btn p={p} label="Eliminar mesa" variant="ghost" onPress={() => setArmedDelete(true)} />
          )}
        </Card>
      ) : null}

      {table.lines.length > 0 ? (
        <Card p={p}>
          <Text style={[ui.title, { color: p.ink }]}>Cuenta abierta</Text>
          <View style={{ marginTop: 10, gap: 8 }}>
            {table.lines.map((l) => (
              <View key={l.productId} style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
                <Text style={[ui.sub, { color: p.ink, flex: 1 }]} numberOfLines={2}>
                  {l.qty}× {s.products.find((x) => x.id === l.productId)?.name ?? 'Producto dado de baja'}
                </Text>
                <Num p={p} value={money(l.qty * price(l.productId))} size={13} />
              </View>
            ))}
          </View>
          <View style={{ marginTop: 12 }}>
            <Num p={p} value={money(total(table.lines))} size={30} />
          </View>
        </Card>
      ) : null}

      <Card p={p}>
        <Text style={[ui.title, { color: p.ink }]}>Agregar productos</Text>
        <View style={{ marginTop: 10, gap: 8 }}>
          {s.products.map((prod) => {
            const selectedProduct = pick.productId === prod.id
            return (
              <View
                key={prod.id}
                style={{
                  flexDirection: 'row',
                  alignItems: 'center',
                  gap: 8,
                  borderWidth: 1,
                  borderColor: selectedProduct ? p.brand : p.line,
                  borderRadius: 12,
                  padding: 10,
                }}
              >
                <View style={{ flex: 1 }}>
                  <Text style={[ui.btnLabel, { color: p.ink }]} numberOfLines={2}>
                    {prod.name}
                  </Text>
                  <Num p={p} value={money(prod.price)} size={12} color={p.mute} />
                </View>
                {selectedProduct ? (
                  <View style={{ flexDirection: 'row', alignItems: 'center', gap: 6 }}>
                    <Btn
                      p={p}
                      label="−"
                      variant="ghost"
                      style={{ minWidth: 48 }}
                      onPress={() => setPick((c) => ({ ...c, qty: Math.max(1, c.qty - 1) }))}
                    />
                    <Num p={p} value={String(pick.qty)} size={16} />
                    <Btn
                      p={p}
                      label="+"
                      variant="ghost"
                      style={{ minWidth: 48 }}
                      onPress={() => setPick((c) => ({ ...c, qty: c.qty + 1 }))}
                    />
                  </View>
                ) : (
                  <LinkBtn
                    p={p}
                    label="ELEGIR"
                    color={p.brand}
                    onPress={() => setPick({ productId: prod.id, qty: 1 })}
                  />
                )}
              </View>
            )
          })}
        </View>
        <View style={{ marginTop: 12, gap: 8 }}>
          <Btn p={p} label="AGREGAR" disabled={!pick.productId} onPress={addItem} />
          {table.open ? (
            armedClose ? (
              <View style={{ gap: 8 }}>
                <Btn p={p} label={`CONFIRMAR CIERRE · ${money(total(table.lines))}`} variant="danger" onPress={closeTable} />
                <Btn p={p} label="CANCELAR" variant="ghost" onPress={() => setArmedClose(false)} />
              </View>
            ) : (
              <Btn p={p} label="CERRAR MESA" variant="danger" onPress={closeTable} />
            )
          ) : null}
        </View>
      </Card>
    </View>
  ) : (
    <Card p={p}>
      <Text style={[ui.sub, { color: p.mute }]}>Elegí una mesa para ver su cuenta.</Text>
    </Card>
  )

  if (split) {
    return (
      <View style={{ flexDirection: 'row', gap: 16 }}>
        <View style={{ flex: 1 }}>{list}</View>
        <View style={{ flex: 1 }}>{detail}</View>
      </View>
    )
  }
  return selected && table ? detail : list
}
