import { useState } from 'react'
import { Text, useWindowDimensions, View } from 'react-native'

import { presets } from '../presets'
import { useStore } from '../store'
import { BREAKPOINTS, normalizeHex, radius } from '../theme'
import { usePalette } from '../usePalette'
import { Btn, Card, Field, Header, styles as ui } from '../ui'

// Personalizar ported from the web master (views/Personalizar.tsx): identity,
// theme and palette. Two adaptations, both stated in the UI instead of
// faking them: the web uses <input type="color"> and a file picker, so here
// the colors are hex fields with a live swatch and the logo tile shows the
// shop's initial (the logo is still uploaded from the web).
export default function Personalizar() {
  const s = useStore()
  const p = usePalette()
  const { width } = useWindowDimensions()
  const [name, setName] = useState(s.name)

  const brandColor = normalizeHex(s.accent)
  const accentColor = normalizeHex(s.accent2)
  const columns = width >= 900 ? 3 : width >= BREAKPOINTS.inlineForm ? 2 : 1
  const split = width >= BREAKPOINTS.twoPane

  const identity = (
    <View style={{ gap: 16 }}>
      <Card p={p}>
        <Text style={[ui.title, { color: p.ink }]}>Establecimiento</Text>
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: 16, marginTop: 12 }}>
          <View
            style={{
              width: 80,
              height: 80,
              borderRadius: radius.tile,
              backgroundColor: p.sunk,
              borderWidth: 1,
              borderStyle: 'dashed',
              borderColor: p.line,
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            <Text style={[ui.display, { color: p.mute, fontSize: 26 }]}>
              {(name.trim()[0] ?? 'm').toUpperCase()}
            </Text>
          </View>
          <View style={{ flex: 1 }}>
            <Text style={[ui.sub, { color: p.mute }]}>
              El logo del bar se sube desde la versión web; acá se muestra la inicial del nombre.
            </Text>
          </View>
        </View>
        <View style={{ marginTop: 14, gap: 8 }}>
          <Text style={[ui.btnLabel, { color: p.ink }]}>Nombre del local</Text>
          <Field p={p} value={name} onChange={setName} placeholder="Nombre del local" />
          <Btn
            p={p}
            label="GUARDAR NOMBRE"
            disabled={!name.trim() || name.trim() === s.name}
            onPress={() => s.setName(name.trim())}
          />
        </View>
      </Card>

      <Card p={p}>
        <Text style={[ui.title, { color: p.ink }]}>Tema</Text>
        <View style={{ flexDirection: 'row', gap: 12, marginTop: 12 }}>
          <Btn
            p={p}
            label="Claro"
            variant={s.dark ? 'ghost' : 'primary'}
            style={{ flex: 1 }}
            onPress={() => s.setDark(false)}
          />
          <Btn
            p={p}
            label="Oscuro"
            variant={s.dark ? 'primary' : 'ghost'}
            style={{ flex: 1 }}
            onPress={() => s.setDark(true)}
          />
        </View>

        <Text style={[ui.label, { color: p.mute, marginTop: 18 }]}>Colores a medida</Text>
        <View style={{ marginTop: 8, gap: 10 }}>
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: 10 }}>
            <View style={{ width: 40, height: 40, borderRadius: 10, backgroundColor: p.brand }} />
            <View style={{ flex: 1 }}>
              <Field p={p} value={s.accent} onChange={s.setAccent} placeholder="#0e7a5a" />
            </View>
          </View>
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: 10 }}>
            <View style={{ width: 40, height: 40, borderRadius: 10, backgroundColor: p.sun }} />
            <View style={{ flex: 1 }}>
              <Field p={p} value={s.accent2} onChange={s.setAccent2} placeholder="#f2a33a" />
            </View>
          </View>
          <Text style={[ui.note, { color: p.mute }]}>
            Valores en hexadecimal; el tema se recalcula al escribir.
          </Text>
        </View>
      </Card>
    </View>
  )

  const presetsCard = (
    <Card p={p}>
      <Text style={[ui.title, { color: p.ink }]}>Presets de diseño</Text>
      <Text style={[ui.sub, { color: p.mute, marginTop: 4 }]}>
        {presets.length} paletas para cada tipo de local
      </Text>
      <View style={{ marginTop: 14, gap: 12 }}>
        {presets.map((preset) => {
          const active = brandColor === preset.brand && accentColor === preset.accent
          return (
            <View
              key={preset.id}
              style={{
                borderWidth: active ? 2 : 1,
                borderColor: active ? p.brand : p.line,
                borderRadius: radius.tile,
                padding: 10,
                width: columns > 1 ? `${100 / columns - 2}%` : '100%',
              }}
            >
              <View style={{ height: 52, borderRadius: 12, overflow: 'hidden', flexDirection: 'row' }}>
                <View style={{ flex: 3, backgroundColor: preset.brand }} />
                <View style={{ flex: 1.2, backgroundColor: preset.accent }} />
                <View style={{ flex: 1, backgroundColor: p.sunk }} />
              </View>
              <View style={{ flexDirection: 'row', alignItems: 'center', marginTop: 10, gap: 8 }}>
                <View style={{ flex: 1 }}>
                  <Text style={[ui.btnLabel, { color: p.ink }]} numberOfLines={1}>
                    {preset.name}
                  </Text>
                  <Text style={[ui.note, { color: p.mute }]} numberOfLines={1}>
                    {preset.tag}
                  </Text>
                </View>
                {active ? <Text style={{ color: p.brand, fontWeight: '800' }}>✓</Text> : null}
              </View>
              <View style={{ marginTop: 6 }}>
                <Btn
                  p={p}
                  label={active ? 'EN USO' : 'USAR PRESET'}
                  variant={active ? 'ghost' : 'primary'}
                  disabled={active}
                  onPress={() => {
                    s.setAccent(preset.brand)
                    s.setAccent2(preset.accent)
                  }}
                />
              </View>
            </View>
          )
        })}
      </View>
    </Card>
  )

  return (
    <View style={{ gap: 16 }}>
      <Header p={p} title="Personalizar" sub="Identidad, tema y paleta de tu establecimiento" />
      {split ? (
        <View style={{ flexDirection: 'row', gap: 16 }}>
          <View style={{ flex: 1 }}>{identity}</View>
          <View style={{ flex: 1 }}>{presetsCard}</View>
        </View>
      ) : (
        <>
          {identity}
          {presetsCard}
        </>
      )}
    </View>
  )
}
