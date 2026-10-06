package dev.mott.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

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

@Composable
fun MottTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) MottDarkColors else MottLightColors,
        content = content
    )
}

// Totals and quantities use monospace figures at displayLarge size so
// digits align and stay legible under night-bar light. Token hexes above
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
