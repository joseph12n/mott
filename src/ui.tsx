import React from 'react'
import { StyleSheet, Text, TextInput, TextStyle, View, ViewStyle } from 'react-native'

import { Palette, radius } from './theme'

// Ported UI vocabulary from the web master (mottandmittdesing/src/views/ui.tsx
// and index.css): .card, .btn (primary/ghost/danger), .pill, .field, .num.
// Every control is >= 48dp tall and never nests a tap target inside another,
// and status is always text plus a dot — never color alone.

export const styles = StyleSheet.create({
  screen: { flex: 1 },
  card: { borderRadius: radius.card, borderWidth: 1, padding: 16 },
  // Figma .btn: 12dp radius, 48dp minimum height on touch screens.
  btn: {
    minHeight: 48,
    paddingHorizontal: 16,
    borderRadius: radius.control,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
  },
  btnLabel: { fontSize: 14, fontWeight: '700' },
  pill: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    borderRadius: radius.pill,
    paddingHorizontal: 10,
    paddingVertical: 4,
    alignSelf: 'flex-start',
  },
  pillText: { fontSize: 12, fontWeight: '700' },
  dot: { width: 8, height: 8, borderRadius: 4 },
  field: {
    minHeight: 48,
    borderRadius: radius.control,
    borderWidth: 1,
    paddingHorizontal: 12,
    fontSize: 14,
  },
  // .num: tabular figures so money columns never jitter.
  num: { fontVariant: ['tabular-nums'], fontFamily: 'monospace' } as TextStyle,
  display: { fontWeight: '800', letterSpacing: -0.5 },
  title: { fontWeight: '800', fontSize: 20 },
  sectionTitle: { fontWeight: '800', fontSize: 26, letterSpacing: -0.6 },
  sub: { fontSize: 13 },
  label: { fontSize: 12, fontWeight: '700', textTransform: 'uppercase', letterSpacing: 0.6 },
  note: { fontSize: 12 },
})

export function Card({ p, style, children }: { p: Palette; style?: ViewStyle; children: React.ReactNode }) {
  return (
    <View style={[styles.card, { backgroundColor: p.surface, borderColor: p.line }, style]}>{children}</View>
  )
}

export function Btn({
  p,
  label,
  onPress,
  variant = 'primary',
  disabled,
  style,
}: {
  p: Palette
  label: string
  onPress: () => void
  variant?: 'primary' | 'ghost' | 'danger'
  disabled?: boolean
  style?: ViewStyle
}) {
  const background =
    variant === 'primary' ? p.brand : variant === 'danger' ? p.berry : p.surface
  const border = variant === 'ghost' ? p.line : background
  const color = variant === 'primary' ? p.onBrand : variant === 'danger' ? '#ffffff' : p.ink
  return (
    <View
      style={[
        styles.btn,
        { backgroundColor: background, borderColor: border, borderWidth: 1, opacity: disabled ? 0.4 : 1 },
        style,
      ]}
    >
      <Text
        onPress={disabled ? undefined : onPress}
        suppressHighlighting
        style={[styles.btnLabel, { color, paddingVertical: 14, paddingHorizontal: 4 }]}
      >
        {label}
      </Text>
    </View>
  )
}

// Text button that only paints a label (rows, links): still 48dp tall.
export function LinkBtn({
  p,
  label,
  onPress,
  color,
  style,
}: {
  p: Palette
  label: string
  onPress: () => void
  color?: string
  style?: ViewStyle
}) {
  return (
    <View style={[{ minHeight: 48, justifyContent: 'center' }, style]}>
      <Text
        onPress={onPress}
        suppressHighlighting
        style={[styles.btnLabel, { color: color ?? p.ink, paddingVertical: 12 }]}
      >
        {label}
      </Text>
    </View>
  )
}

export type PillTone = 'brand' | 'sun' | 'mute' | 'berry'

export function Pill({ p, tone = 'brand', children }: { p: Palette; tone?: PillTone; children: React.ReactNode }) {
  const background =
    tone === 'brand' ? p.brandSoft : tone === 'sun' ? p.sun : tone === 'berry' ? '#f3d5da' : p.sunk
  const color = tone === 'brand' ? p.brand : tone === 'sun' ? p.sunInk : tone === 'berry' ? p.berry : p.mute
  return (
    <View style={[styles.pill, { backgroundColor: background }]}>
      <Text style={[styles.pillText, { color }]}>{children}</Text>
    </View>
  )
}

// Status pill with a leading dot, mirroring the master's occupancy/stock pills.
export function StatusPill({ p, on, onLabel, offLabel }: { p: Palette; on: boolean; onLabel: string; offLabel: string }) {
  return (
    <View style={[styles.pill, { backgroundColor: on ? p.sun : p.brandSoft }]}>
      <View style={[styles.dot, { backgroundColor: on ? p.berry : p.brand }]} />
      <Text style={[styles.pillText, { color: on ? p.sunInk : p.brand }]}>{on ? onLabel : offLabel}</Text>
    </View>
  )
}

export function Header({
  p,
  title,
  sub,
  right,
}: {
  p: Palette
  title: string
  sub?: string
  right?: React.ReactNode
}) {
  return (
    <View style={{ marginBottom: 20, gap: 10 }}>
      <View style={{ gap: 2 }}>
        <Text style={[styles.sectionTitle, { color: p.ink }]}>{title}</Text>
        {sub ? <Text style={[styles.sub, { color: p.mute }]}>{sub}</Text> : null}
      </View>
      {right}
    </View>
  )
}

export function Field({
  p,
  value,
  onChange,
  placeholder,
  keyboardType,
  style,
}: {
  p: Palette
  value: string
  onChange: (value: string) => void
  placeholder?: string
  keyboardType?: 'default' | 'numeric' | 'decimal-pad'
  style?: ViewStyle
}) {
  return (
    <TextInput
      value={value}
      onChangeText={onChange}
      placeholder={placeholder}
      placeholderTextColor={p.mute}
      keyboardType={keyboardType ?? 'default'}
      style={[
        styles.field,
        { backgroundColor: p.sunk, borderColor: p.line, color: p.ink },
        style,
      ]}
    />
  )
}

export function Num({
  p,
  value,
  size = 14,
  color,
  style,
}: {
  p: Palette
  value: string
  size?: number
  color?: string
  style?: TextStyle
}) {
  return (
    <Text style={[styles.num, { fontSize: size, color: color ?? p.ink }, style]}>{value}</Text>
  )
}

// Simple labelled bar used by the Panel chart and the supplier balances.
export function Bar({ p, fraction, color }: { p: Palette; fraction: number; color?: string }) {
  const pct = Math.max(0, Math.min(1, fraction))
  return (
    <View style={{ height: 8, borderRadius: 4, backgroundColor: p.sunk, overflow: 'hidden' }}>
      <View style={{ width: `${pct * 100}%`, height: '100%', backgroundColor: color ?? p.brand }} />
    </View>
  )
}
