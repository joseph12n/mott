import { useMemo } from 'react'

import { useStore } from './store'
import { Palette, buildPalette } from './theme'

// The palette for the current bar brand and theme, computed once per change.
// Every screen reads its colors from here, so changing the brand in
// Personalizar repaints the whole app.
export function usePalette(): Palette {
  const s = useStore()
  return useMemo(() => buildPalette(s.accent, s.accent2, s.dark), [s.accent, s.accent2, s.dark])
}
