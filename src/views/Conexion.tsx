import { useState } from 'react'
import { Share, Text, useWindowDimensions, View } from 'react-native'

import { useStore } from '../store'
import { BREAKPOINTS } from '../theme'
import { usePalette } from '../usePalette'
import { Btn, Card, Field, Header, Num, Pill, styles as ui } from '../ui'

// Conexión ported from the web master (views/Conexion.tsx). On the PC that
// screen shows a QR for the phone to scan; on the phone the same data is
// useful as the address of the bar's server plus what this device holds, so
// the QR block becomes a share/copy action and an explicit note that the QR
// is displayed on the PC. The endpoint list stays as documentation of the
// hub's public API.
type Target = 'panel' | 'mesas' | 'catalogo' | 'proveedores' | 'gastos' | 'conexion' | 'personalizar'

export default function Conexion({ go }: { go: (v: Target) => void }) {
  const s = useStore()
  const p = usePalette()
  const { width } = useWindowDimensions()
  const [host, setHost] = useState('192.168.1.34')
  const [port, setPort] = useState('8080')
  const [shared, setShared] = useState(false)

  const url = `http://${host}:${port}`
  const synced = s.tables.filter((t) => t.synced).length
  const split = width >= BREAKPOINTS.twoPane

  const steps = [
    'Abrí la PC del bar y entrá a la sección Conexión.',
    'En el teléfono, tocá “Escanear código” y apuntá al QR de la PC.',
    'Mesas y productos se descargan solos en la app.',
  ]

  const endpoints: [string, string][] = [
    ['GET', '/api/tables'],
    ['GET', '/api/products'],
    ['POST', '/api/tabs'],
    ['PATCH', '/api/tables/:id'],
  ]

  const share = async () => {
    try {
      await Share.share({ message: `Servidor del bar: ${url}` })
      setShared(true)
    } catch {
      // Sharing is optional: a dismissed sheet is not an error worth showing.
    }
  }

  const serverCard = (
    <Card p={p}>
      <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
        <Text style={[ui.title, { color: p.ink }]}>Servidor</Text>
        <Pill p={p}>
          <View style={[ui.dot, { backgroundColor: p.brand }]} />
          Datos locales
        </Pill>
      </View>
      <View style={{ marginTop: 12 }}>
        <Num p={p} value={url} size={18} />
      </View>
      <Text style={[ui.sub, { color: p.mute, marginTop: 4 }]}>
        Dirección del servidor del bar. El QR se muestra en la PC, en su sección Conexión.
      </Text>
      <View style={{ marginTop: 12, gap: 8 }}>
        <Btn p={p} label={shared ? 'Dirección compartida' : 'Compartir dirección'} variant="ghost" onPress={share} />
        <Field p={p} value={host} onChange={setHost} placeholder="IP local" />
        <Field p={p} value={port} onChange={setPort} placeholder="Puerto" keyboardType="numeric" />
      </View>
    </Card>
  )

  const howTo = (
    <Card p={p}>
      <Text style={[ui.title, { color: p.ink }]}>Cómo conectar</Text>
      <View style={{ marginTop: 12, gap: 12 }}>
        {steps.map((t, i) => (
          <View key={t} style={{ flexDirection: 'row', gap: 12, alignItems: 'flex-start' }}>
            <View
              style={{
                width: 32,
                height: 32,
                borderRadius: 16,
                backgroundColor: p.brandSoft,
                alignItems: 'center',
                justifyContent: 'center',
              }}
            >
              <Num p={p} value={String(i + 1)} size={13} color={p.brand} />
            </View>
            <Text style={[ui.sub, { color: p.ink, flex: 1, paddingTop: 6 }]}>{t}</Text>
          </View>
        ))}
      </View>
    </Card>
  )

  return (
    <View style={{ gap: 16 }}>
      <Header
        p={p}
        title="Conexión"
        sub="Enlaza la app móvil con el servidor de la PC"
        right={<Btn p={p} label="Ver mesas" variant="ghost" onPress={() => go('mesas')} />}
      />

      {split ? (
        <View style={{ flexDirection: 'row', gap: 16 }}>
          <View style={{ flex: 1, gap: 16 }}>{serverCard}</View>
          <View style={{ flex: 1, gap: 16 }}>{howTo}</View>
        </View>
      ) : (
        <>
          {serverCard}
          {howTo}
        </>
      )}

      <View style={{ gap: 12, flexDirection: split ? 'row' : 'column' }}>
        {[
          { label: 'Mesas sincronizadas', value: `${synced}/${s.tables.length}` },
          { label: 'Productos publicados', value: String(s.products.length) },
          { label: 'Datos en el teléfono', value: `${s.expenses.length + s.orders.length} registros` },
        ].map((k) => (
          <Card key={k.label} p={p} style={{ flex: 1 }}>
            <Num p={p} value={k.value} size={22} />
            <Text style={[ui.note, { color: p.mute, marginTop: 4 }]}>{k.label}</Text>
          </Card>
        ))}
      </View>

      <Card p={p}>
        <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 8 }}>
          <Text style={[ui.title, { color: p.ink, flex: 1 }]}>API de la PC</Text>
        </View>
        <Text style={[ui.sub, { color: p.mute, marginTop: 4 }]}>
          Rutas que expone el servidor del bar para la app móvil.
        </Text>
        <View style={{ marginTop: 12, gap: 10 }}>
          {endpoints.map(([method, path]) => (
            <View key={path} style={{ flexDirection: 'row', alignItems: 'center', gap: 12 }}>
              <Pill p={p} tone={method === 'GET' ? 'brand' : 'sun'}>
                {method}
              </Pill>
              <Num p={p} value={`${url}${path}`} size={12} color={p.mute} />
            </View>
          ))}
        </View>
        <View style={{ marginTop: 14 }}>
          <Btn
            p={p}
            label="Registrar todas las mesas"
            onPress={() => s.setTables((t) => t.map((x) => ({ ...x, synced: true })))}
          />
        </View>
      </Card>
    </View>
  )
}
