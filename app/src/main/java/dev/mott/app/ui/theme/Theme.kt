package dev.mott.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.mott.app.R
import dev.mott.app.data.Brand
import dev.mott.app.data.parseHex6

// Figma master tokens (mottandmittdesing/src/index.css:4-34), light-first.
// Light: brand #0e7a5a, sun #f2a33a, paper base #f7f6f1, ink #16201b,
// mute #667069, berry #d6455d. Mixed tokens (paper, sunk, line,
// brand-soft, sunink) are color-mix() results against the default
// brand-base #0e7a5a, resolved once to static hexes so paint never
// depends on runtime CSS math. Dark column is the html.dark override
// set from the same file, resolved the same way.

// Token: brand (light primary, light tertiary)
private val Brand = Color(0xFF0E7A5A)

// Token: sun (secondary in both themes; master never overrides it)
private val Sun = Color(0xFFF2A33A)

// Token: paper (light background) = 5% brand over #f7f6f1
private val Paper = Color(0xFFEBF0E9)

// Token: surface (light) = #ffffff
private val SurfaceLight = Color(0xFFFFFFFF)

// Token: sunk (light surfaceVariant) = 6% brand over #efede6
private val SunkLight = Color(0xFFE2E6DE)

// Token: line (light outlineVariant) = 8% brand over #e6e3da
private val LineLight = Color(0xFFD5DBD0)

// Token: ink (light on-background/on-surface)
private val InkLight = Color(0xFF16201B)

// Token: mute (light on-surfaceVariant)
private val MuteLight = Color(0xFF667069)

// Token: brand-soft (light primaryContainer) = 12% brand over surface
private val BrandSoft = Color(0xFFE2EFEB)

// Token: sunink (light on-secondary) = 40% sun over #2e1c00
private val SunInk = Color(0xFF7C5217)

// Derived (not in master): 12% sun over surface, mirrors brand-soft so
// secondary containers stay symmetric with primary ones.
private val SunSoft = Color(0xFFFDF4E7)

// Token: on-brand (light on-primary) = #ffffff
private val OnBrandLight = Color(0xFFFFFFFF)

// Token: berry (light error)
private val BerryLight = Color(0xFFD6455D)

// Dark overrides (master html.dark, same brand-base):
// brand = 60% brand over white, paper = 9% brand over #0e1210,
// surface = 8% brand over #171c19, sunk = 10% brand over #212724,
// line = 14% brand over #2a302d, ink #eef2ef, mute #98a39c,
// on-brand #0a0e0c, berry #ff7088.
private val BrandDark = Color(0xFF6EAF9C)
private val PaperDark = Color(0xFF0E1B17)
private val SurfaceDark = Color(0xFF16241E)
private val SunkDark = Color(0xFF1F2F29)
private val LineDark = Color(0xFF263A33)
private val InkDark = Color(0xFFEEF2EF)
private val MuteDark = Color(0xFF98A39C)
private val OnBrandDark = Color(0xFF0A0E0C)
private val BerryDark = Color(0xFFFF7088)

// Derived (not in master): 20% dark-brand over dark surface, so the
// dark primary container reads as a tint, not a flat fill.
private val BrandSoftDark = Color(0xFF284037)

// Derived (not in master): 14% sun over dark surface, mirrors SunSoft.
private val SunSoftDark = Color(0xFF353622)

private val MottLightColors = lightColorScheme(
    primary = Brand,
    onPrimary = OnBrandLight,
    primaryContainer = BrandSoft,
    onPrimaryContainer = Brand,
    secondary = Sun,
    onSecondary = SunInk,
    secondaryContainer = SunSoft,
    onSecondaryContainer = SunInk,
    tertiary = Brand,
    onTertiary = OnBrandLight,
    background = Paper,
    onBackground = InkLight,
    surface = SurfaceLight,
    onSurface = InkLight,
    surfaceVariant = SunkLight,
    onSurfaceVariant = MuteLight,
    outlineVariant = LineLight,
    error = BerryLight,
    onError = OnBrandLight,
)

private val MottDarkColors = darkColorScheme(
    primary = BrandDark,
    onPrimary = OnBrandDark,
    primaryContainer = BrandSoftDark,
    onPrimaryContainer = BrandDark,
    secondary = Sun,
    onSecondary = OnBrandDark,
    secondaryContainer = SunSoftDark,
    onSecondaryContainer = Sun,
    tertiary = BrandDark,
    onTertiary = OnBrandDark,
    background = PaperDark,
    onBackground = InkDark,
    surface = SurfaceDark,
    onSurface = InkDark,
    surfaceVariant = SunkDark,
    onSurfaceVariant = MuteDark,
    outlineVariant = LineDark,
    error = BerryDark,
    onError = OnBrandDark,
)

// Default schemes without hub branding. Public so JVM token tests can
// assert Figma parity without composing anything.
fun mottLightScheme(): ColorScheme = MottLightColors

fun mottDarkScheme(): ColorScheme = MottDarkColors

// Picks readable content for an arbitrary hub color: light text on dark
// fills, dark text on light fills. Keeps any hub palette legible without
// per-hub tuning.
private fun contentOn(background: Color, onDarkBg: Color, onLightBg: Color): Color =
    if (background.luminance() > 0.5f) onLightBg else onDarkBg

// Dark scheme follows the hub brand 1:1 with the mitt web hub: background
// paints the backdrop, brand primary (a surface tone hub-side) paints
// cards/tiles, brand accent paints actions. Invalid hexes fall back to the
// token defaults per role, so bad hub data never breaks paint.
fun brandDarkScheme(brand: Brand?): ColorScheme {
    val primary = brand?.accentHex?.let(::parseHex6) ?: BrandDark
    val background = brand?.backgroundHex?.let(::parseHex6) ?: PaperDark
    val surface = brand?.primaryHex?.let(::parseHex6) ?: SurfaceDark
    return darkColorScheme(
        primary = primary,
        onPrimary = contentOn(primary, onDarkBg = OnBrandDark, onLightBg = OnBrandLight),
        primaryContainer = BrandSoftDark,
        onPrimaryContainer = BrandDark,
        secondary = Sun,
        onSecondary = OnBrandDark,
        tertiary = BrandDark,
        onTertiary = OnBrandDark,
        background = background,
        onBackground = contentOn(background, onDarkBg = InkDark, onLightBg = InkLight),
        surface = surface,
        onSurface = contentOn(surface, onDarkBg = InkDark, onLightBg = InkLight),
        surfaceVariant = SunkDark,
        onSurfaceVariant = MuteDark,
        outlineVariant = LineDark,
        error = BerryDark,
        onError = OnBrandDark,
    )
}

// Light scheme is the daylight variant: hub accent still drives actions
// for brand parity, but surfaces stay light tokens so daylight
// readability never depends on a dark-first hub palette.
fun brandLightScheme(brand: Brand?): ColorScheme {
    val primary = brand?.accentHex?.let(::parseHex6) ?: Brand
    return lightColorScheme(
        primary = primary,
        onPrimary = contentOn(primary, onDarkBg = OnBrandDark, onLightBg = OnBrandLight),
        primaryContainer = BrandSoft,
        onPrimaryContainer = Brand,
        secondary = Sun,
        onSecondary = SunInk,
        secondaryContainer = SunSoft,
        onSecondaryContainer = SunInk,
        tertiary = Brand,
        onTertiary = OnBrandLight,
        background = Paper,
        onBackground = InkLight,
        surface = SurfaceLight,
        onSurface = InkLight,
        surfaceVariant = SunkLight,
        onSurfaceVariant = MuteLight,
        outlineVariant = LineLight,
        error = BerryLight,
        onError = OnBrandLight,
    )
}

// Type (master index.css:1 + views usage): display = Bricolage Grotesque
// 500/700/800 for headers, titles and hero numbers; body = Figtree
// 400..700; numbers = DM Mono 400/500 with tabular figures (.num).
// Bricolage and Figtree ship as variable TTFs (google/fonts, fvar axis
// confirmed) driven per-weight via FontVariation — supported by the
// Compose BOM in use (FontVariation stable since Compose UI 1.7, here
// 1.9.x) and by minSdk 26. DM Mono ships static and stays static.
@OptIn(ExperimentalTextApi::class)
val DisplayFontFamily = FontFamily(
    Font(
        R.font.bricolage_grotesque,
        FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500)),
    ),
    Font(
        R.font.bricolage_grotesque,
        FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700)),
    ),
    Font(
        R.font.bricolage_grotesque,
        FontWeight.ExtraBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(800)),
    ),
)

@OptIn(ExperimentalTextApi::class)
val BodyFontFamily = FontFamily(
    Font(
        R.font.figtree,
        FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400)),
    ),
    Font(
        R.font.figtree,
        FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500)),
    ),
    Font(
        R.font.figtree,
        FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600)),
    ),
    Font(
        R.font.figtree,
        FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700)),
    ),
)

val MonoFontFamily = FontFamily(
    Font(R.font.dm_mono_regular, FontWeight.Normal),
    Font(R.font.dm_mono_medium, FontWeight.Medium),
)

private fun displayStyle(size: Int, line: Int, weight: FontWeight) = TextStyle(
    fontFamily = DisplayFontFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
)

private fun bodyStyle(size: Int, line: Int, weight: FontWeight) = TextStyle(
    fontFamily = BodyFontFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
)

val MottTypography = Typography(
    displayLarge = displayStyle(57, 64, FontWeight.Bold),
    displayMedium = displayStyle(45, 52, FontWeight.Bold),
    displaySmall = displayStyle(36, 44, FontWeight.Bold),
    headlineLarge = displayStyle(32, 40, FontWeight.Bold),
    headlineMedium = displayStyle(28, 36, FontWeight.Bold),
    headlineSmall = displayStyle(24, 32, FontWeight.Bold),
    titleLarge = displayStyle(22, 28, FontWeight.Medium),
    titleMedium = displayStyle(16, 24, FontWeight.Medium),
    titleSmall = displayStyle(14, 20, FontWeight.Medium),
    bodyLarge = bodyStyle(16, 24, FontWeight.Normal),
    bodyMedium = bodyStyle(14, 20, FontWeight.Normal),
    bodySmall = bodyStyle(12, 16, FontWeight.Normal),
    labelLarge = bodyStyle(14, 20, FontWeight.SemiBold),
    labelMedium = bodyStyle(12, 16, FontWeight.SemiBold),
    labelSmall = bodyStyle(11, 16, FontWeight.Medium),
)

@Composable
fun MottTheme(
    mode: ThemeMode = ThemeMode.SISTEMA,
    systemDark: Boolean = isSystemInDarkTheme(),
    brand: Brand? = null,
    content: @Composable () -> Unit
) {
    val darkTheme = mode.resolveDark(systemDark)
    MaterialTheme(
        colorScheme = if (darkTheme) brandDarkScheme(brand) else brandLightScheme(brand),
        typography = MottTypography,
        shapes = MittShapes,
        content = content
    )
}

// Shape language from the Figma web master (index.css:54-67):
// .card border-radius 1.25rem = 20dp -> large (table, product, pairing
// cards). Cards separate with a 1px line border, never with shadow, so
// MittCard pairs this shape with BorderStroke + 0dp elevation.
// .btn border-radius 0.75rem = 12dp -> small (all CTA buttons).
// .pill rounded-full -> CircleShape at the call site (MittStatusPill /
// MittStockPill), not a theme slot, mirroring the web full-round pill.
val MittShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
)

// Figma .btn (index.css:54): fixed 40dp height, 12dp radius. Single source
// of truth shared by every Mitt*Button so height never drifts per call
// site. Touch target stays 40dp per the master; Material3 ripple still
// handles press feedback.
val MittButtonHeight = 40.dp

// Totals and quantities use DM Mono figures at display size so digits
// align and stay legible. This is the mobile translation of the web .num
// class (DM Mono is fixed-width, hence inherently tabular-nums).
val TotalStyle = TextStyle(
    fontFamily = MonoFontFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 57.sp,
    lineHeight = 64.sp,
    letterSpacing = 0.sp,
)

// Compact mono figure for prices inside rows and steppers.
val FigureStyle = TextStyle(
    fontFamily = MonoFontFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 18.sp,
    lineHeight = 24.sp,
    letterSpacing = 0.sp,
)
