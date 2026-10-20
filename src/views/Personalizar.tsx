import { useEffect, useRef, useState } from 'react'
import { Image, Text, useWindowDimensions, View } from 'react-native'

import { presets } from '../presets'
import { useStore } from '../store'
import { BREAKPOINTS, normalizeHex, radius } from '../theme'
import { usePalette } from '../usePalette'
import { Btn, Card, Field, Header, styles as ui } from '../ui'

export default function Personalizar() {
  const s = useStore()
  const p = usePalette()
  const { width } = useWindowDimensions()
  const [name, setName] = useState(s.name)
  const [accentDraft, setAccentDraft] = useState(s.accent)
  const [accent2Draft, setAccent2Draft] = useState(s.accent2)
  const [logoFailed, setLogoFailed] = useState(false)
  const [savingName, setSavingName] = useState(false)
  const [savingColors, setSavingColors] = useState(false)
  const [savingPresetId, setSavingPresetId] = useState<string | null>(null)
  const brandingBusyRef = useRef(false)

  useEffect(() => {
    setName(s.name)
  }, [s.name])

  useEffect(() => {
    setLogoFailed(false)
  }, [s.logoUrl])

  useEffect(() => {
    setAccentDraft(s.accent)
  }, [s.accent])

  useEffect(() => {
    setAccent2Draft(s.accent2)
  }, [s.accent2])

  const brandColor = normalizeHex(s.accent)
  const accentColor = normalizeHex(s.accent2)
  const columns = width >= 900 ? 3 : width >= BREAKPOINTS.inlineForm ? 2 : 1
  const split = width >= BREAKPOINTS.twoPane

  const colorsChanged =
    normalizeHex(accentDraft) !== brandColor || normalizeHex(accent2Draft) !== accentColor
  const brandingBusy = savingName || savingColors || savingPresetId !== null

  const handleSaveName = async () => {
    const trimmed = name.trim()
    if (!trimmed || trimmed === s.name || brandingBusy || brandingBusyRef.current) return
    brandingBusyRef.current = true
    setSavingName(true)
    try {
      await s.saveBranding({ name: trimmed })
    } finally {
      brandingBusyRef.current = false
      setSavingName(false)
    }
  }

  const handleSaveColors = async () => {
    if (!colorsChanged || brandingBusy || brandingBusyRef.current) return
    brandingBusyRef.current = true
    setSavingColors(true)
    try {
      await s.saveBranding({
        accent: normalizeHex(accentDraft),
        accent2: normalizeHex(accent2Draft),
      })
    } finally {
      brandingBusyRef.current = false
      setSavingColors(false)
    }
  }

  const handleApplyPreset = async (presetId: string, brand: string, accent: string) => {
    if (brandingBusy || brandingBusyRef.current) return
    brandingBusyRef.current = true
    setSavingPresetId(presetId)
    try {
      await s.saveBranding({
        accent: brand,
        accent2: accent,
      })
    } finally {
      brandingBusyRef.current = false
      setSavingPresetId(null)
    }
  }

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
              overflow: 'hidden',
            }}
          >
            {s.logoUrl && !logoFailed ? (
              <Image
                source={{ uri: s.logoUrl }}
                style={{ width: 80, height: 80, borderRadius: radius.tile }}
                resizeMode="cover"
                onError={() => setLogoFailed(true)}
              />
            ) : (
              <Text style={[ui.display, { color: p.mute, fontSize: 26 }]}>
                {(name.trim()[0] ?? 'm').toUpperCase()}
              </Text>
            )}
          </View>
          <View style={{ flex: 1 }}>
            <Text style={[ui.sub, { color: p.mute }]}>
              {s.pairing
                ? 'El logo proviene del servidor de la PC; si no hay imagen cargada se muestra la inicial.'
                : 'Sin PC vinculada se muestra la inicial del nombre del local.'}
            </Text>
          </View>
        </View>
        <View style={{ marginTop: 14, gap: 8 }}>
          <Text style={[ui.btnLabel, { color: p.ink }]}>Nombre del local</Text>
          <Field p={p} value={name} onChange={setName} placeholder="Nombre del local" />
          <Btn
            p={p}
            label="GUARDAR NOMBRE"
            loading={savingName}
            disabled={!name.trim() || name.trim() === s.name || brandingBusy}
            onPress={() => void handleSaveName()}
          />
          {s.hubError ? (
            <Text style={[ui.sub, { color: p.berry, fontWeight: '700' }]}>{s.hubError}</Text>
          ) : null}
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
            <View
              style={{
                width: 40,
                height: 40,
                borderRadius: 10,
                backgroundColor: normalizeHex(accentDraft),
              }}
            />
            <View style={{ flex: 1 }}>
              <Field
                p={p}
                value={accentDraft}
                onChange={setAccentDraft}
                placeholder="#0e7a5a"
                autoCapitalize="none"
              />
            </View>
          </View>
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: 10 }}>
            <View
              style={{
                width: 40,
                height: 40,
                borderRadius: 10,
                backgroundColor: normalizeHex(accent2Draft),
              }}
            />
            <View style={{ flex: 1 }}>
              <Field
                p={p}
                value={accent2Draft}
                onChange={setAccent2Draft}
                placeholder="#f2a33a"
                autoCapitalize="none"
              />
            </View>
          </View>
          <Btn
            p={p}
            label="GUARDAR COLORES"
            loading={savingColors}
            disabled={!colorsChanged || brandingBusy}
            onPress={() => void handleSaveColors()}
          />
          <Text style={[ui.note, { color: p.mute }]}>
            Valores en hexadecimal; tocá Guardar colores para aplicar la paleta.
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
                  loading={savingPresetId === preset.id}
                  disabled={active || brandingBusy}
                  onPress={() => void handleApplyPreset(preset.id, preset.brand, preset.accent)}
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
