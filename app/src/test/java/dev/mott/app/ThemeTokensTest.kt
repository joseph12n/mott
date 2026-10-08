package dev.mott.app

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.mott.app.data.Brand
import dev.mott.app.ui.theme.BodyFontFamily
import dev.mott.app.ui.theme.DisplayFontFamily
import dev.mott.app.ui.theme.FigureStyle
import dev.mott.app.ui.theme.MittButtonHeight
import dev.mott.app.ui.theme.MittShapes
import dev.mott.app.ui.theme.MonoFontFamily
import dev.mott.app.ui.theme.MottTypography
import dev.mott.app.ui.theme.TotalStyle
import dev.mott.app.ui.theme.brandDarkScheme
import dev.mott.app.ui.theme.brandLightScheme
import dev.mott.app.ui.theme.mottDarkScheme
import dev.mott.app.ui.theme.mottLightScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Test

// Figma master tokens (mottandmittdesing/src/index.css:4-34), light-first.
// Light: brand #0e7a5a, sun #f2a33a, paper #f7f6f1 base, ink #16201b,
// mute #667069, berry #d6455d, brand-soft = brand at 12% over surface.
// Dark: color-mix overrides from the same file, resolved to static hexes.
class ThemeTokensTest {

    @Test
    fun light_primaryIsBrand() {
        assertEquals(Color(0xFF0E7A5A), mottLightScheme().primary)
    }

    @Test
    fun light_backgroundIsPaper() {
        assertEquals(Color(0xFFEBF0E9), mottLightScheme().background)
    }

    @Test
    fun light_errorIsBerry() {
        assertEquals(Color(0xFFD6455D), mottLightScheme().error)
    }

    @Test
    fun light_secondaryIsSun() {
        assertEquals(Color(0xFFF2A33A), mottLightScheme().secondary)
    }

    @Test
    fun light_onBackgroundIsInk() {
        assertEquals(Color(0xFF16201B), mottLightScheme().onBackground)
    }

    @Test
    fun light_onSurfaceVariantIsMute() {
        assertEquals(Color(0xFF667069), mottLightScheme().onSurfaceVariant)
    }

    @Test
    fun light_primaryContainerIsBrandSoft() {
        assertEquals(Color(0xFFE2EFEB), mottLightScheme().primaryContainer)
    }

    @Test
    fun dark_primaryMatchesMasterOverride() {
        assertEquals(Color(0xFF6EAF9C), mottDarkScheme().primary)
    }

    @Test
    fun dark_backgroundMatchesMasterOverride() {
        assertEquals(Color(0xFF0E1B17), mottDarkScheme().background)
    }

    @Test
    fun dark_errorMatchesMasterOverride() {
        assertEquals(Color(0xFFFF7088), mottDarkScheme().error)
    }

    @Test
    fun dark_onBackgroundMatchesMasterOverride() {
        assertEquals(Color(0xFFEEF2EF), mottDarkScheme().onBackground)
    }

    @Test
    fun dark_onSurfaceVariantMatchesMasterOverride() {
        assertEquals(Color(0xFF98A39C), mottDarkScheme().onSurfaceVariant)
    }

    @Test
    fun brandOverride_darkAppliesHubColors() {
        val brand = Brand(
            shopName = "hub",
            primaryHex = "#112233",
            accentHex = "#445566",
            backgroundHex = "#778899",
        )
        val scheme = brandDarkScheme(brand)
        assertEquals(Color(0xFF445566), scheme.primary)
        assertEquals(Color(0xFF778899), scheme.background)
        assertEquals(Color(0xFF112233), scheme.surface)
    }

    @Test
    fun brandOverride_darkFallsBackOnBadHex() {
        val brand = Brand(
            shopName = "hub",
            primaryHex = "nope",
            accentHex = "#GGGGGG",
            backgroundHex = "",
        )
        val scheme = brandDarkScheme(brand)
        assertEquals(mottDarkScheme().primary, scheme.primary)
        assertEquals(mottDarkScheme().background, scheme.background)
        assertEquals(mottDarkScheme().surface, scheme.surface)
    }

    @Test
    fun brandOverride_lightKeepsAccentOnLightSurfaces() {
        val brand = Brand(
            shopName = "hub",
            primaryHex = "#112233",
            accentHex = "#445566",
            backgroundHex = "#778899",
        )
        val scheme = brandLightScheme(brand)
        assertEquals(Color(0xFF445566), scheme.primary)
        assertEquals(mottLightScheme().background, scheme.background)
        assertEquals(mottLightScheme().surface, scheme.surface)
    }

    @Test
    fun typography_displayStylesUseBricolageFamily() {
        assertSame(DisplayFontFamily, MottTypography.displayLarge.fontFamily)
        assertSame(DisplayFontFamily, MottTypography.headlineMedium.fontFamily)
        assertSame(DisplayFontFamily, MottTypography.titleLarge.fontFamily)
    }

    @Test
    fun typography_bodyStylesUseFigtreeFamily() {
        assertSame(BodyFontFamily, MottTypography.bodyLarge.fontFamily)
        assertSame(BodyFontFamily, MottTypography.bodyMedium.fontFamily)
        assertSame(BodyFontFamily, MottTypography.labelLarge.fontFamily)
    }

    @Test
    fun typography_moneyStylesUseMonoFamily() {
        assertSame(MonoFontFamily, TotalStyle.fontFamily)
        assertSame(MonoFontFamily, FigureStyle.fontFamily)
    }

    @Test
    fun typography_familiesAreBundledNotSystem() {
        assertNotEquals(FontFamily.Default, DisplayFontFamily)
        assertNotEquals(FontFamily.Default, BodyFontFamily)
        assertNotEquals(FontFamily.Monospace, MonoFontFamily)
        assertNotEquals(DisplayFontFamily, BodyFontFamily)
        assertNotEquals(BodyFontFamily, MonoFontFamily)
    }

    @Test
    fun kit_buttonHeightMatchesMasterBtn() {
        assertEquals(40.dp, MittButtonHeight)
    }

    @Test
    fun kit_shapesMatchMasterCardAndBtnRadius() {
        assertEquals(RoundedCornerShape(12.dp), MittShapes.small)
        assertEquals(RoundedCornerShape(20.dp), MittShapes.large)
    }
}
