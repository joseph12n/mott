import { useMemo, useState } from 'react'
import { Text, View } from 'react-native'

import { money, uid } from '../data'
import { useStore } from '../store'
import { usePalette } from '../usePalette'
import { Btn, Card, Field, Header, LinkBtn, Num, Pill, styles as ui } from '../ui'

// Catálogo ported from the web master (views/Catalogo.tsx): products grouped
// by category, add form, and a two-tap delete. The web master owns its own
// catalog, so categories are kept here (unlike the PC-hub client, which
// dropped them because the hub had no such field).
export default function Catalogo() {
  const s = useStore()
  const p = usePalette()
  const [name, setName] = useState('')
  const [price, setPrice] = useState('')
  const [category, setCategory] = useState('Platos')
  const [armed, setArmed] = useState<string | null>(null)

  const categories = useMemo(() => [...new Set(s.products.map((x) => x.category))], [s.products])

  return (
    <View style={{ gap: 16 }}>
      <Header p={p} title="Catálogo" sub={`${s.products.length} productos disponibles para la app móvil`} />

      <Card p={p}>
        <View style={{ gap: 8 }}>
          <Field p={p} value={name} onChange={setName} placeholder="Nombre del producto" />
          <Field p={p} value={price} onChange={setPrice} placeholder="Precio" keyboardType="decimal-pad" />
          <Field p={p} value={category} onChange={setCategory} placeholder="Rubro (Platos, Bebidas…)" />
          <Btn
            p={p}
            label="AGREGAR PRODUCTO"
            disabled={!name.trim() || !Number(price)}
            onPress={() => {
              s.setProducts((list) => [
                ...list,
                { id: uid(), name: name.trim(), price: Number(price), category: category.trim() || 'General' },
              ])
              setName('')
              setPrice('')
            }}
          />
        </View>
      </Card>

      {categories.map((cat) => (
        <View key={cat} style={{ gap: 10 }}>
          <Text style={[ui.label, { color: p.mute }]}>{cat}</Text>
          {s.products
            .filter((x) => x.category === cat)
            .map((x) => (
              <Card key={x.id} p={p}>
                <View style={{ flexDirection: 'row', alignItems: 'center', gap: 12 }}>
                  <View
                    style={{
                      width: 44,
                      height: 44,
                      borderRadius: 12,
                      backgroundColor: p.brandSoft,
                      alignItems: 'center',
                      justifyContent: 'center',
                    }}
                  >
                    <Text style={[ui.display, { color: p.brand, fontSize: 18 }]}>{x.name[0]}</Text>
                  </View>
                  <View style={{ flex: 1 }}>
                    <Text style={[ui.btnLabel, { color: p.ink }]} numberOfLines={2}>
                      {x.name}
                    </Text>
                    <Num p={p} value={money(x.price)} size={13} color={p.mute} />
                  </View>
                  <Pill p={p}>En la app</Pill>
                </View>
                <View style={{ marginTop: 8, flexDirection: 'row', gap: 8, alignItems: 'center' }}>
                  {armed === x.id ? (
                    <>
                      <LinkBtn
                        p={p}
                        label="CONFIRMAR"
                        color={p.berry}
                        onPress={() => {
                          s.setProducts((list) => list.filter((y) => y.id !== x.id))
                          setArmed(null)
                        }}
                      />
                      <LinkBtn p={p} label="CANCELAR" color={p.mute} onPress={() => setArmed(null)} />
                    </>
                  ) : (
                    <LinkBtn p={p} label="Eliminar" color={p.mute} onPress={() => setArmed(x.id)} />
                  )}
                </View>
              </Card>
            ))}
        </View>
      ))}
    </View>
  )
}
