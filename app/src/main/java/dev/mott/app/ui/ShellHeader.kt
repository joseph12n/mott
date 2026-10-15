package dev.mott.app.ui

import dev.mott.app.ui.theme.ThemeMode

// Cycles Sistema -> Claro -> Oscuro -> Sistema for the expanded side-rail
// theme button. Personalizar owns the full Claro/Oscuro/Sistema picker, so
// the compact header carries no theme toggle. The header composables now
// live in AdaptiveShell.kt.
fun nextThemeMode(current: ThemeMode): ThemeMode = when (current) {
    ThemeMode.SISTEMA -> ThemeMode.CLARO
    ThemeMode.CLARO -> ThemeMode.OSCURO
    ThemeMode.OSCURO -> ThemeMode.SISTEMA
}
