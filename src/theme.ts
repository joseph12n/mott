// Design tokens ported from the web master (mottandmittdesing/src/index.css).
// The web builds its palette with CSS color-mix() over the bar's brand color;
// here the same mixes are computed in JS so the app is a single source of
// truth for both platforms. Light and dark roles mirror the html.dark block.
//
// Deviation from the web: the master's Bricolage Grotesque / Figtree / DM Mono
// webfonts are not shipped with the app yet, so text uses the platform font
// (numbers still use the platform monospace family with tabular figures).

export type Palette = {
  paper: string
  surface: string
  sunk: string
  line: string
  ink: string
  mute: string
  brand: string
  onBrand: string
  brandSoft: string
  sun: string
  sunInk: string
  berry: string
}

export const DEFAULT_BRAND = '#0e7a5a'
export const DEFAULT_ACCENT = '#f2a33a'

const LIGHT_BASE = { paper: '#f7f6f1', sunk: '#efede6', line: '#e6e3da' }
const DARK_BASE = { paper: '#0e1210', surface: '#171c19', sunk: '#212724', line: '#2a302d' }

function parseHex(value: string, fallback: string): [number, number, number] {
  const raw = value.trim().replace('#', '')
  const hex = /^[0-9a-fA-F]{6}$/.test(raw) ? raw : fallback.replace('#', '')
  return [
    parseInt(hex.slice(0, 2), 16),
    parseInt(hex.slice(2, 4), 16),
    parseInt(hex.slice(4, 6), 16),
  ]
}

export function normalizeHex(value: string, fallback: string = DEFAULT_BRAND): string {
  const raw = value.trim().replace('#', '')
  if (!/^[0-9a-fA-F]{6}$/.test(raw)) return fallback
  return `#${raw.toLowerCase()}`
}

function toHex(rgb: [number, number, number]): string {
  return `#${rgb
    .map((c) => Math.max(0, Math.min(255, Math.round(c))).toString(16).padStart(2, '0'))
    .join('')}`
}

// color-mix(in srgb, a weight%, b): blend `weight` percent of a into b.
function mix(a: string, weight: number, b: string): string {
  const [ar, ag, ab] = parseHex(a, DEFAULT_BRAND)
  const [br, bg, bb] = parseHex(b, '#ffffff')
  const w = weight / 100
  return toHex([
    ar * w + br * (1 - w),
    ag * w + bg * (1 - w),
    ab * w + bb * (1 - w),
  ])
}

export function buildPalette(brandBase: string, accentBase: string, dark: boolean): Palette {
  const brand = normalizeHex(brandBase, DEFAULT_BRAND)
  const accent = normalizeHex(accentBase, DEFAULT_ACCENT)
  if (dark) {
    const surf = mix(brand, 8, DARK_BASE.surface)
    return {
      paper: mix(brand, 9, DARK_BASE.paper),
      surface: surf,
      sunk: mix(brand, 10, DARK_BASE.sunk),
      line: mix(brand, 14, DARK_BASE.line),
      ink: '#eef2ef',
      mute: '#98a39c',
      brand: mix(brand, 60, '#ffffff'),
      onBrand: '#0a0e0c',
      brandSoft: mix(brand, 12, surf),
      sun: accent,
      sunInk: accent,
      berry: '#ff7088',
    }
  }
  const surf = '#ffffff'
  return {
    paper: mix(brand, 5, LIGHT_BASE.paper),
    surface: surf,
    sunk: mix(brand, 6, LIGHT_BASE.sunk),
    line: mix(brand, 8, LIGHT_BASE.line),
    ink: '#16201b',
    mute: '#667069',
    brand,
    onBrand: '#ffffff',
    brandSoft: mix(brand, 12, surf),
    sun: accent,
    sunInk: mix(accent, 40, '#2e1c00'),
    berry: '#d6455d',
  }
}

// Shared shape vocabulary: .card radius 1.25rem = 20dp, .btn/field = 12dp.
export const radius = { card: 20, control: 12, tile: 16, pill: 999 } as const

// Layout breakpoints, same numbers the Kotlin shell used: a permanent side
// rail needs 840dp, and two panes need 720dp of section width.
export const BREAKPOINTS = { railWidth: 256, sideRail: 840, twoPane: 720, inlineForm: 420 } as const
