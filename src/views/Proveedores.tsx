import { useState } from 'react'
import { Text, useWindowDimensions, View } from 'react-native'

import { money, uid } from '../data'
import { useStore } from '../store'
import { BREAKPOINTS } from '../theme'
import { usePalette } from '../usePalette'
import { Btn, Card, Field, Header, LinkBtn, Num, Pill, styles as ui } from '../ui'

// Proveedores ported from the web master (views/Proveedores.tsx): suppliers
// with what they sell, what was bought and what is still owed. The balance
// actions are inline (no dialogs) and destructive ones need a second tap.
export default function Proveedores() {
  const s = useStore()
  const p = usePalette()
  const { width } = useWindowDimensions()
  const [sel, setSel] = useState(s.suppliers[0]?.id ?? '')
  const [form, setForm] = useState({ name: '', phone: '', category: '' })
  const [newProduct, setNewProduct] = useState({ name: '', price: '', unit: 'u.' })
  const [buy, setBuy] = useState({ productId: '', qty: 1 })
  const [armed, setArmed] = useState(false)

  const supplier = s.suppliers.find((x) => x.id === sel)
  const split = width >= BREAKPOINTS.twoPane

  const upd = (id: string, fn: (x: (typeof s.suppliers)[number]) => (typeof s.suppliers)[number]) =>
    s.setSuppliers((list) => list.map((x) => (x.id === id ? fn(x) : x)))

  const owed = (x: (typeof s.suppliers)[number]) =>
    x.purchases.filter((c) => !c.paid).reduce((a, c) => a + c.qty * c.price, 0)
  const totalOwed = s.suppliers.reduce((a, x) => a + owed(x), 0)

  const list = (
    <View style={{ gap: 16 }}>
      <Header
        p={p}
        title="Proveedores"
        sub={`${s.suppliers.length} proveedores · lo que ofrecen y cuánto se les debe`}
        right={
          <Card p={p}>
            <Text style={[ui.note, { color: p.mute }]}>Pendiente de pago</Text>
            <Num p={p} value={money(totalOwed)} size={24} color={p.berry} />
          </Card>
        }
      />

      <Card p={p}>
        <Text style={[ui.label, { color: p.mute }]}>Agregar proveedor</Text>
        <View style={{ marginTop: 8, gap: 8 }}>
          <Field p={p} value={form.name} onChange={(v) => setForm({ ...form, name: v })} placeholder="Nombre del proveedor" />
          <Field p={p} value={form.phone} onChange={(v) => setForm({ ...form, phone: v })} placeholder="Teléfono" />
          <Field
            p={p}
            value={form.category}
            onChange={(v) => setForm({ ...form, category: v })}
            placeholder="Rubro (Carnes…)"
          />
          <Btn
            p={p}
            label="AGREGAR PROVEEDOR"
            disabled={!form.name.trim()}
            onPress={() => {
              const id = uid()
              s.setSuppliers((l) => [
                ...l,
                {
                  id,
                  name: form.name.trim(),
                  phone: form.phone,
                  category: form.category.trim() || 'General',
                  products: [],
                  purchases: [],
                },
              ])
              setSel(id)
              setForm({ name: '', phone: '', category: '' })
            }}
          />
        </View>
      </Card>

      <View style={{ gap: 10 }}>
        {s.suppliers.map((x) => (
          <Card
            key={x.id}
            p={p}
            style={{ borderColor: x.id === sel ? p.brand : p.line, borderWidth: x.id === sel ? 2 : 1 }}
          >
            <View style={{ flexDirection: 'row', alignItems: 'center', gap: 12 }}>
              <View style={{ flex: 1 }}>
                <Text style={[ui.btnLabel, { color: p.ink }]} numberOfLines={1}>
                  {x.name}
                </Text>
                <Text style={[ui.note, { color: p.mute }]}>
                  {x.category} · {x.products.length} productos
                </Text>
              </View>
              {owed(x) > 0 ? <Num p={p} value={money(owed(x))} size={14} color={p.berry} /> : <Pill p={p}>Al día</Pill>}
            </View>
            <View style={{ marginTop: 6 }}>
              <LinkBtn p={p} label="VER FICHA" color={p.brand} onPress={() => setSel(x.id)} />
            </View>
          </Card>
        ))}
        {s.suppliers.length === 0 ? (
          <Text style={[ui.sub, { color: p.mute }]}>Agrega tu primer proveedor.</Text>
        ) : null}
      </View>
    </View>
  )

  const detail = supplier ? (
    <View style={{ gap: 16 }}>
      <Card p={p}>
        <View style={{ flexDirection: 'row', alignItems: 'flex-start', gap: 8 }}>
          <View style={{ flex: 1 }}>
            <Text style={[ui.title, { color: p.ink }]}>{supplier.name}</Text>
            <View style={{ flexDirection: 'row', gap: 8, marginTop: 6, flexWrap: 'wrap' }}>
              <Pill p={p} tone="mute">
                {supplier.category}
              </Pill>
              <Text style={[ui.note, { color: p.mute }]}>{supplier.phone || 'Sin teléfono'}</Text>
            </View>
          </View>
        </View>

        <Text style={[ui.label, { color: p.mute, marginTop: 16 }]}>Productos que vende</Text>
        <View style={{ marginTop: 8, gap: 8 }}>
          {supplier.products.map((prod) => (
            <View key={prod.id} style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
              <Text style={[ui.btnLabel, { color: p.ink, flex: 1 }]} numberOfLines={1}>
                {prod.name}
              </Text>
              <Num p={p} value={`${money(prod.price)} / ${prod.unit}`} size={12} />
              <LinkBtn
                p={p}
                label="✕"
                color={p.mute}
                onPress={() =>
                  upd(supplier.id, (x) => ({ ...x, products: x.products.filter((y) => y.id !== prod.id) }))
                }
              />
            </View>
          ))}
          {supplier.products.length === 0 ? (
            <Text style={[ui.sub, { color: p.mute }]}>Sin productos todavía.</Text>
          ) : null}
        </View>
        <View style={{ marginTop: 8, gap: 8 }}>
          <Field
            p={p}
            value={newProduct.name}
            onChange={(v) => setNewProduct({ ...newProduct, name: v })}
            placeholder="Producto"
          />
          <Field
            p={p}
            value={newProduct.price}
            onChange={(v) => setNewProduct({ ...newProduct, price: v })}
            placeholder="Precio"
            keyboardType="decimal-pad"
          />
          <Field
            p={p}
            value={newProduct.unit}
            onChange={(v) => setNewProduct({ ...newProduct, unit: v })}
            placeholder="Unidad (kg, u.)"
          />
          <Btn
            p={p}
            label="AGREGAR PRODUCTO"
            variant="ghost"
            disabled={!newProduct.name.trim() || !Number(newProduct.price)}
            onPress={() => {
              upd(supplier.id, (x) => ({
                ...x,
                products: [
                  ...x.products,
                  {
                    id: uid(),
                    name: newProduct.name.trim(),
                    price: Number(newProduct.price),
                    unit: newProduct.unit.trim() || 'u.',
                  },
                ],
              }))
              setNewProduct({ name: '', price: '', unit: 'u.' })
            }}
          />
        </View>
      </Card>

      <Card p={p}>
        <Text style={[ui.title, { color: p.ink }]}>Compras y cuenta</Text>
        <View style={{ marginTop: 8 }}>
          <Num p={p} value={owed(supplier) > 0 ? `Debes ${money(owed(supplier))}` : 'Al día'} size={16} color={owed(supplier) > 0 ? p.berry : p.brand} />
        </View>

        {owed(supplier) > 0 ? (
          <View style={{ marginTop: 8 }}>
            {armed ? (
              <View style={{ gap: 8 }}>
                <Btn
                  p={p}
                  label={`CONFIRMAR SALDAR ${money(owed(supplier))}`}
                  onPress={() => {
                    upd(supplier.id, (x) => ({ ...x, purchases: x.purchases.map((c) => ({ ...c, paid: true })) }))
                    setArmed(false)
                  }}
                />
                <Btn p={p} label="CANCELAR" variant="ghost" onPress={() => setArmed(false)} />
              </View>
            ) : (
              <Btn p={p} label={`Saldar ${money(owed(supplier))}`} onPress={() => setArmed(true)} />
            )}
          </View>
        ) : null}

        <View style={{ marginTop: 12, gap: 8 }}>
          {supplier.products.map((prod) => (
            <View
              key={prod.id}
              style={{
                flexDirection: 'row',
                alignItems: 'center',
                gap: 8,
              }}
            >
              <View style={{ flex: 1 }}>
                <Text style={[ui.btnLabel, { color: p.ink }]} numberOfLines={1}>
                  {prod.name}
                </Text>
                <Num p={p} value={money(prod.price)} size={12} color={p.mute} />
              </View>
              <LinkBtn
                p={p}
                label="REGISTRAR COMPRA"
                color={p.brand}
                onPress={() => {
                  upd(supplier.id, (x) => ({
                    ...x,
                    purchases: [
                      { id: uid(), productName: prod.name, qty: buy.qty, price: prod.price, paid: false, date: 'Hoy' },
                      ...x.purchases,
                    ],
                  }))
                }}
              />
            </View>
          ))}
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
            <Text style={[ui.note, { color: p.mute }]}>Cantidad</Text>
            <Btn
              p={p}
              label="−"
              variant="ghost"
              style={{ minWidth: 48 }}
              onPress={() => setBuy((c) => ({ ...c, qty: Math.max(1, c.qty - 1) }))}
            />
            <Num p={p} value={String(buy.qty)} size={16} />
            <Btn p={p} label="+" variant="ghost" style={{ minWidth: 48 }} onPress={() => setBuy((c) => ({ ...c, qty: c.qty + 1 }))} />
          </View>
        </View>

        <Text style={[ui.label, { color: p.mute, marginTop: 16 }]}>Historial</Text>
        <View style={{ marginTop: 8, gap: 10 }}>
          {supplier.purchases.map((c) => (
            <View key={c.id} style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
              <View style={{ flex: 1 }}>
                <Text style={[ui.btnLabel, { color: p.ink }]} numberOfLines={1}>
                  {c.productName}
                </Text>
                <Text style={[ui.note, { color: p.mute }]}>
                  {c.date} · {c.qty} × {money(c.price)}
                </Text>
              </View>
              <Num p={p} value={money(c.qty * c.price)} size={13} />
              <LinkBtn
                p={p}
                label={c.paid ? 'Pagado' : 'Pendiente'}
                color={c.paid ? p.brand : p.berry}
                onPress={() =>
                  upd(supplier.id, (x) => ({
                    ...x,
                    purchases: x.purchases.map((y) => (y.id === c.id ? { ...y, paid: !y.paid } : y)),
                  }))
                }
              />
            </View>
          ))}
          {supplier.purchases.length === 0 ? (
            <Text style={[ui.sub, { color: p.mute }]}>Aún no registras compras.</Text>
          ) : null}
        </View>
      </Card>
    </View>
  ) : (
    <Card p={p}>
      <Text style={[ui.sub, { color: p.mute }]}>Selecciona o agrega un proveedor.</Text>
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
  return supplier && sel ? detail : list
}
