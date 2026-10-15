import { StatusBar } from 'expo-status-bar'
import React, { useMemo, useState } from 'react'
import { Pressable, ScrollView, Text, useWindowDimensions, View } from 'react-native'
import { SafeAreaProvider, useSafeAreaInsets } from 'react-native-safe-area-context'

import { StoreProvider, useStore } from './src/store'
import { BREAKPOINTS, buildPalette, radius } from './src/theme'
import { Card, styles as ui } from './src/ui'
import Catalogo from './src/views/Catalogo'
import Conexion from './src/views/Conexion'
import Gastos from './src/views/Gastos'
import Mesas from './src/views/Mesas'
import Panel from './src/views/Panel'
import Personalizar from './src/views/Personalizar'
import Proveedores from './src/views/Proveedores'

// Shell ported from the web master (mottandmittdesing/src/App.tsx): the same
// seven sections in the same order, driven by one view state. The master's
// sidebar (lg:) becomes a permanent rail on wide screens and its horizontal
// nav strip becomes the bottom bar on phones — the responsive split the
// Kotlin client had, now in the same codebase as the web design.
type View = 'panel' | 'mesas' | 'catalogo' | 'proveedores' | 'gastos' | 'conexion' | 'personalizar'

const NAV: { id: View; label: string; glyph: string }[] = [
  { id: 'panel', label: 'Panel', glyph: '▮▮' },
  { id: 'mesas', label: 'Mesas', glyph: '▦' },
  { id: 'catalogo', label: 'Catálogo', glyph: '☰' },
  { id: 'proveedores', label: 'Proveedores', glyph: '▤' },
  { id: 'gastos', label: 'Gastos', glyph: '◧' },
  { id: 'conexion', label: 'Conexión', glyph: '▣' },
  { id: 'personalizar', label: 'Personalizar', glyph: '◐' },
]

function Shell() {
  const s = useStore()
  const { width } = useWindowDimensions()
  const insets = useSafeAreaInsets()
  const [view, setView] = useState<View>('panel')

  const p = useMemo(() => buildPalette(s.accent, s.accent2, s.dark), [s.accent, s.accent2, s.dark])
  const openCount = s.tables.filter((t) => t.open).length
  const wide = width >= BREAKPOINTS.sideRail

  const content = (() => {
    switch (view) {
      case 'mesas':
        return <Mesas />
      case 'catalogo':
        return <Catalogo />
      case 'proveedores':
        return <Proveedores />
      case 'gastos':
        return <Gastos />
      case 'conexion':
        return <Conexion go={setView} />
      case 'personalizar':
        return <Personalizar />
      default:
        return <Panel go={setView} />
    }
  })()

  // Wide: permanent rail + title header. Narrow: title header, content, then
  // the scrollable pill strip pinned above the system bar.
  if (wide) {
    return (
      <View style={[ui.screen, { backgroundColor: p.paper, flexDirection: 'row' }]}>
        <View
          style={{
            width: BREAKPOINTS.railWidth,
            backgroundColor: p.surface,
            borderRightWidth: 1,
            borderRightColor: p.line,
            padding: 20,
            gap: 16,
          }}
        >
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: 12 }}>
            <View
              style={{
                width: 40,
                height: 40,
                borderRadius: 12,
                backgroundColor: p.brand,
                alignItems: 'center',
                justifyContent: 'center',
              }}
            >
              <Text style={[ui.display, { color: p.onBrand, fontSize: 18 }]}>
                {(s.name.trim()[0] ?? 'm').toUpperCase()}
              </Text>
            </View>
            <View style={{ flex: 1 }}>
              <Text style={[ui.display, { color: p.ink, fontSize: 17 }]} numberOfLines={1}>
                {s.name.trim() || 'mitt'}
              </Text>
              <Text style={[ui.note, { color: p.mute }]}>Servidor local</Text>
            </View>
          </View>
          <ScrollView style={{ flex: 1 }} contentContainerStyle={{ gap: 4 }}>
            {NAV.map((item) => {
              const active = view === item.id
              return (
                <Pressable
                  key={item.id}
                  onPress={() => setView(item.id)}
                  style={{
                    minHeight: 48,
                    borderRadius: radius.control,
                    paddingHorizontal: 12,
                    flexDirection: 'row',
                    alignItems: 'center',
                    gap: 12,
                    backgroundColor: active ? p.ink : 'transparent',
                  }}
                >
                  <Text style={{ color: active ? p.paper : p.mute, fontSize: 14 }}>{item.glyph}</Text>
                  <Text style={[ui.btnLabel, { color: active ? p.paper : p.mute, flex: 1 }]}>{item.label}</Text>
                  {item.id === 'mesas' && openCount > 0 ? (
                    <Text style={[ui.num, { color: active ? p.paper : p.brand, fontSize: 12 }]}>{openCount}</Text>
                  ) : null}
                </Pressable>
              )
            })}
          </ScrollView>
          <View style={{ gap: 10 }}>
            <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
              <View style={[ui.dot, { backgroundColor: p.brand }]} />
              <Text style={[ui.label, { color: p.brand }]}>Datos en el teléfono</Text>
            </View>
            <Card p={p} style={{ padding: 12 }}>
              <Text style={[ui.note, { color: p.mute }]}>
                {s.tables.length} mesas · {s.products.length} productos · {s.suppliers.length} proveedores
              </Text>
            </Card>
          </View>
        </View>
        <ScrollView
          style={ui.screen}
          contentContainerStyle={{
            padding: 24,
            paddingBottom: 48,
            maxWidth: 1400,
            width: '100%',
            alignSelf: 'center',
          }}
        >
          {content}
        </ScrollView>
      </View>
    )
  }

  return (
    <View style={[ui.screen, { backgroundColor: p.paper, paddingTop: insets.top }]}>
      <ScrollView style={ui.screen} contentContainerStyle={{ padding: 16, paddingBottom: 24 }}>
        {content}
      </ScrollView>
      <ScrollView
        horizontal
        showsHorizontalScrollIndicator={false}
        style={{ borderTopWidth: 1, borderTopColor: p.line, backgroundColor: p.surface, flexGrow: 0 }}
        contentContainerStyle={{
          gap: 8,
          paddingHorizontal: 12,
          paddingVertical: 8,
          paddingBottom: 8 + insets.bottom,
        }}
      >
        {NAV.map((item) => {
          const active = view === item.id
          return (
            <Pressable
              key={item.id}
              onPress={() => setView(item.id)}
              style={{
                minHeight: 48,
                minWidth: 96,
                borderRadius: radius.pill,
                paddingHorizontal: 14,
                flexDirection: 'row',
                alignItems: 'center',
                justifyContent: 'center',
                gap: 8,
                backgroundColor: active ? p.ink : p.sunk,
              }}
            >
              <Text style={{ color: active ? p.paper : p.mute, fontSize: 13 }}>{item.glyph}</Text>
              <Text style={[ui.btnLabel, { color: active ? p.paper : p.mute }]}>{item.label}</Text>
              {item.id === 'mesas' && openCount > 0 ? (
                <Text style={[ui.num, { color: active ? p.paper : p.brand, fontSize: 11 }]}>{openCount}</Text>
              ) : null}
            </Pressable>
          )
        })}
      </ScrollView>
    </View>
  )
}

export default function App() {
  return (
    <SafeAreaProvider>
      <StoreProvider>
        <StatusBar style="auto" />
        <Shell />
      </StoreProvider>
    </SafeAreaProvider>
  )
}
