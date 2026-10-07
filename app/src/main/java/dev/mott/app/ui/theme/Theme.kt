package dev.mott.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.mott.app.data.Brand
import dev.mott.app.data.parseHex6

// Color roles copied 1:1 from the shared token set (mitt docs/design-tokens.md).
// Dark column is the default theme; light is the daylight-admin variant.

// Token: bg
private val BgDark = Color(0xFF121417)
private val BgLight = Color(0xFFF5F3EC)

// Token: surface
private val SurfaceDark = Color(0xFF1C1F24)
private val SurfaceLight = Color(0xFFFFFFFF)

// Token: surface-raised
private val SurfaceRaisedDark = Color(0xFF262B33)
private val SurfaceRaisedLight = Color(0xFFEDEAE0)

// Token: text
private val TextDark = Color(0xFFF2EFE6)
private val TextLight = Color(0xFF1A1C1E)

// Token: text-muted
private val TextMutedDark = Color(0xFF9AA1AD)
private val TextMutedLight = Color(0xFF62676F)

// Token: accent
private val AccentDark = Color(0xFFE8A33D)
private val AccentLight = Color(0xFFB97A1A)

// Token: success
private val SuccessDark = Color(0xFF4CAF6D)
private val SuccessLight = Color(0xFF2E7D46)

// Token: warn
private val WarnDark = Color(0xFFE0B13E)
private val WarnLight = Color(0xFF9A6F14)

// Token: danger
private val DangerDark = Color(0xFFE05C5C)
private val DangerLight = Color(0xFFB33737)

// Token: on-accent
private val OnAccentDark = Color(0xFF1A1206)
private val OnAccentLight = Color(0xFFFFFFFF)

private val MottDarkColors = darkColorScheme(
    primary = AccentDark,
    onPrimary = OnAccentDark,
    secondary = TextMutedDark,
    onSecondary = BgDark,
    tertiary = SuccessDark,
    background = BgDark,
    onBackground = TextDark,
    surface = SurfaceDark,
    onSurface = TextDark,
    surfaceVariant = SurfaceRaisedDark,
    onSurfaceVariant = TextMutedDark,
    error = DangerDark,
    onError = OnAccentDark
)

private val MottLightColors = lightColorScheme(
    primary = AccentLight,
    onPrimary = OnAccentLight,
    secondary = TextMutedLight,
    onSecondary = BgLight,
    tertiary = SuccessLight,
    background = BgLight,
    onBackground = TextLight,
    surface = SurfaceLight,
    onSurface = TextLight,
    surfaceVariant = SurfaceRaisedLight,
    onSurfaceVariant = TextMutedLight,
    error = DangerLight,
    onError = OnAccentLight
)

// Picks readable content for an arbitrary hub color: light text on dark
// fills, dark text on light fills. Keeps any hub palette legible without
// per-hub tuning.
private fun contentOn(background: Color, onDarkBg: Color, onLightBg: Color): Color =
    if (background.luminance() > 0.5f) onLightBg else onDarkBg

// Dark scheme follows the hub brand 1:1 with the mitt web hub: background
// paints the backdrop, brand primary (a surface tone hub-side) paints
// cards/tiles, brand accent paints actions. Invalid hexes fall back to the
// token defaults per role, so bad hub data never breaks paint.
private fun brandDarkScheme(brand: Brand?): ColorScheme {
    val primary = brand?.accentHex?.let(::parseHex6) ?: AccentDark
    val background = brand?.backgroundHex?.let(::parseHex6) ?: BgDark
    val surface = brand?.primaryHex?.let(::parseHex6) ?: SurfaceDark
    return darkColorScheme(
        primary = primary,
        onPrimary = contentOn(primary, onDarkBg = OnAccentDark, onLightBg = OnAccentLight),
        secondary = TextMutedDark,
        onSecondary = BgDark,
        tertiary = SuccessDark,
        background = background,
        onBackground = contentOn(background, onDarkBg = TextDark, onLightBg = TextLight),
        surface = surface,
        onSurface = contentOn(surface, onDarkBg = TextDark, onLightBg = TextLight),
        surfaceVariant = SurfaceRaisedDark,
        onSurfaceVariant = TextMutedDark,
        error = DangerDark,
        onError = OnAccentDark
    )
}

// Light scheme is the daylight-admin variant: hub accent still drives
// actions for brand parity, but surfaces stay light tokens so daylight
// readability never depends on a dark-first hub palette.
private fun brandLightScheme(brand: Brand?): ColorScheme {
    val primary = brand?.accentHex?.let(::parseHex6) ?: AccentLight
    return lightColorScheme(
        primary = primary,
        onPrimary = contentOn(primary, onDarkBg = OnAccentDark, onLightBg = OnAccentLight),
        secondary = TextMutedLight,
        onSecondary = BgLight,
        tertiary = SuccessLight,
        background = BgLight,
        onBackground = TextLight,
        surface = SurfaceLight,
        onSurface = TextLight,
        surfaceVariant = SurfaceRaisedLight,
        onSurfaceVariant = TextMutedLight,
        error = DangerLight,
        onError = OnAccentLight
    )
}

@Composable
fun MottTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    brand: Brand? = null,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) brandDarkScheme(brand) else brandLightScheme(brand),
        shapes = MittShapes,
        content = content
    )
}

// Shape language translated from the Figma web master
// (mottandmittdesing/src/index.css, values read as px at 16px root):
// .card border-radius 1.25rem = 20dp -> large (table, product, pairing
// cards). Cards separate with a 1px line border, never with shadow, so
// MittCard pairs this shape with BorderStroke + 0dp elevation.
// .btn / .field border-radius 0.75rem = 12dp -> small (all CTA buttons,
// text fields via the Material3 default mapping).
// .pill rounded-full -> CircleShape at the call site (MittStatusPill /
// MittStockPill), not a theme slot, mirroring the web full-round pill.
val MittShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
)

// Totals and quantities use monospace figures at displayLarge size so
// digits align and stay legible under night-bar light. This is the mobile
// translation of the web .num class (tabular-nums figures). Token hexes above
// are the shared source of truth and stay untouched.
val TotalStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Bold,
    fontSize = 57.sp,
    lineHeight = 64.sp,
    letterSpacing = (-0.25).sp,
)

// Compact monospace figure for prices inside rows and steppers.
val FigureStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.SemiBold,
    fontSize = 18.sp,
    lineHeight = 24.sp,
)
