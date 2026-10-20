import { useMemo, useState } from 'react'
import { Text, View } from 'react-native'

import { money } from '../data'
import { useStore } from '../store'
import { usePalette } from '../usePalette'
import { Btn, Card, Field, Header, Num, Pill, styles as ui } from '../ui'

export default function Catalogo() {
  const s = useStore()
  const p = usePalette()
  const [name, setName] = useState('')
  const [price, setPrice] = useState('')
  const [category, setCategory] = useState('Carta')

  const categories = useMemo(
    () => [...new Set(s.products.map((x) => x.category || 'Carta'))],
    [s.products],
  )
  const availableCount = useMemo(
    () => s.products.filter((x) => s.productAvailable[x.id] !== false).length,
    [s.products, s.productAvailable],
  )

  const handleAdd = async () => {
    const numPrice = Number(price)
    if (!name.trim() || !numPrice || numPrice <= 0) return
    const ok = await s.createProduct(name.trim(), numPrice, category.trim() || 'Carta')
    if (ok) {
      setName('')
      setPrice('')
    }
  }

  return (
    <View style={{ gap: 16 }}>
      <Header
        p={p}
        title="Catálogo"
        sub={`${availableCount} disponibles de ${s.products.length} productos en carta`}
      />

      <Card p={p}>
        <View style={{ gap: 8 }}>
          <Field p={p} value={name} onChange={setName} placeholder="Nombre del producto" />
          <Field p={p} value={price} onChange={setPrice} placeholder="Precio" keyboardType="decimal-pad" />
          <Field p={p} value={category} onChange={setCategory} placeholder="Rubro (Carta, Platos, Bebidas…)" />
          <Btn
            p={p}
            label="AGREGAR PRODUCTO"
            disabled={!name.trim() || !Number(price) || Number(price) <= 0}
            onPress={() => void handleAdd()}
          />
          {s.hubError ? (
            <Text style={[ui.sub, { color: p.berry, fontWeight: '700' }]}>{s.hubError}</Text>
          ) : null}
        </View>
      </Card>

      {categories.map((cat) => (
        <View key={cat} style={{ gap: 10 }}>
          <Text style={[ui.label, { color: p.mute }]}>{cat}</Text>
          {s.products
            .filter((x) => (x.category || 'Carta') === cat)
            .map((x) => {
              const available = s.productAvailable[x.id] !== false
              return (
                <Card key={x.id} p={p} style={available ? undefined : { opacity: 0.72 }}>
                  <View style={{ flexDirection: 'row', alignItems: 'center', gap: 12 }}>
                    <View
                      style={{
                        width: 44,
                        height: 44,
                        borderRadius: 12,
                        backgroundColor: available ? p.brandSoft : p.sunk,
                        alignItems: 'center',
                        justifyContent: 'center',
                      }}
                    >
                      <Text style={[ui.display, { color: available ? p.brand : p.mute, fontSize: 18 }]}>
                        {(x.name[0] ?? 'P').toUpperCase()}
                      </Text>
                    </View>
                    <View style={{ flex: 1 }}>
                      <Text style={[ui.btnLabel, { color: p.ink }]} numberOfLines={2}>
                        {x.name}
                      </Text>
                      <Num p={p} value={money(x.price)} size={13} color={p.mute} />
                    </View>
                    <Pill
                      p={p}
                      tone={available ? 'brand' : 'sun'}
                      dotColor={available ? p.brand : p.berry}
                    >
                      {available ? 'Disponible' : 'Dada de baja'}
                    </Pill>
                  </View>
                  <View style={{ marginTop: 10 }}>
                    <Btn
                      p={p}
                      label={available ? 'DAR DE BAJA' : 'VOLVER A LA CARTA'}
                      variant="ghost"
                      onPress={() => void s.setProductAvailable(x.id, !available)}
                    />
                  </View>
                </Card>
              )
            })}
        </View>
      ))}

      {s.products.length === 0 ? (
        <Text style={[ui.sub, { color: p.mute }]}>Todavía no hay productos en el catálogo.</Text>
      ) : null}
    </View>
  )
}
