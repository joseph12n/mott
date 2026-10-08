package dev.mott.app

import dev.mott.app.ui.AppSection
import dev.mott.app.ui.LoadFailureReason
import dev.mott.app.ui.connectionPillState
import dev.mott.app.ui.mesasBadgeText
import dev.mott.app.ui.message
import dev.mott.app.ui.nextThemeMode
import dev.mott.app.ui.showRePair
import dev.mott.app.ui.subtitle
import dev.mott.app.ui.theme.ThemeMode
import dev.mott.app.ui.theme.ThemeModeStore
import dev.mott.app.ui.theme.parseThemeMode
import dev.mott.app.ui.theme.resolveDark
import dev.mott.app.ui.title
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// T3 shell contract: 7-section nav in master order, Mesas badge text,
// connectivity pill state, persisted theme mode, and the load-error card
// copy contract (R3-004 advisory). JVM-pure, no Compose.
class ShellTest {

    @Test
    fun `seven sections follow master order`() {
        assertEquals(
            listOf("panel", "mesas", "catalogo", "proveedores", "gastos", "conexion", "personalizar"),
            AppSection.entries.map { it.route },
        )
    }

    @Test
    fun `seven spanish labels in master order`() {
        assertEquals(
            listOf("Panel", "Mesas", "Catálogo", "Proveedores", "Gastos", "Conexión", "Personalizar"),
            AppSection.entries.map { it.label },
        )
    }

    @Test
    fun `every section carries a non-blank shell subtitle`() {
        for (section in AppSection.entries) {
            assertTrue("${section.route} subtitle blank", section.subtitle().isNotBlank())
        }
    }

    @Test
    fun `mesas badge hides at zero and shows the open count otherwise`() {
        assertNull(mesasBadgeText(0))
        assertNull(mesasBadgeText(-1))
        assertEquals("1", mesasBadgeText(1))
        assertEquals("12", mesasBadgeText(12))
    }

    @Test
    fun `pill is conectado-live while paired`() {
        val pill = connectionPillState(paired = true)
        assertEquals("CONECTADO", pill.text)
        assertTrue(pill.live)
    }

    @Test
    fun `pill is offline while unpaired`() {
        val pill = connectionPillState(paired = false)
        assertEquals("SIN CONEXIÓN", pill.text)
        assertFalse(pill.live)
    }

    @Test
    fun `theme mode sistema follows the system flag`() {
        assertTrue(ThemeMode.SISTEMA.resolveDark(systemDark = true))
        assertFalse(ThemeMode.SISTEMA.resolveDark(systemDark = false))
    }

    @Test
    fun `theme mode claro and oscuro ignore the system flag`() {
        assertFalse(ThemeMode.CLARO.resolveDark(systemDark = true))
        assertFalse(ThemeMode.CLARO.resolveDark(systemDark = false))
        assertTrue(ThemeMode.OSCURO.resolveDark(systemDark = true))
        assertTrue(ThemeMode.OSCURO.resolveDark(systemDark = false))
    }

    @Test
    fun `theme mode defaults to sistema on empty prefs`() {
        assertEquals(ThemeMode.SISTEMA, ThemeModeStore(FakePrefs()).get())
    }

    @Test
    fun `theme mode round-trips through prefs`() {
        val store = ThemeModeStore(FakePrefs())
        store.save(ThemeMode.OSCURO)
        assertEquals(ThemeMode.OSCURO, store.get())
        store.save(ThemeMode.CLARO)
        assertEquals(ThemeMode.CLARO, store.get())
        store.save(ThemeMode.SISTEMA)
        assertEquals(ThemeMode.SISTEMA, store.get())
    }

    @Test
    fun `theme mode parses unknown raw values to sistema`() {
        assertEquals(ThemeMode.SISTEMA, parseThemeMode(null))
        assertEquals(ThemeMode.SISTEMA, parseThemeMode(""))
        assertEquals(ThemeMode.SISTEMA, parseThemeMode("dark"))
        assertEquals(ThemeMode.CLARO, parseThemeMode("claro"))
        assertEquals(ThemeMode.OSCURO, parseThemeMode("oscuro"))
    }

    @Test
    fun `shell toggle cycles sistema claro oscuro`() {
        assertEquals(ThemeMode.CLARO, nextThemeMode(ThemeMode.SISTEMA))
        assertEquals(ThemeMode.OSCURO, nextThemeMode(ThemeMode.CLARO))
        assertEquals(ThemeMode.SISTEMA, nextThemeMode(ThemeMode.OSCURO))
    }

    @Test
    fun `error card copy names every reason in spanish`() {
        assertEquals("Token inválido", LoadFailureReason.TOKEN_INVALIDO.title())
        assertEquals("Sin servidor", LoadFailureReason.SIN_SERVIDOR.title())
        assertEquals("Error inesperado", LoadFailureReason.ERROR_INESPERADO.title())
        for (reason in LoadFailureReason.entries) {
            assertTrue("$reason message blank", reason.message().isNotBlank())
        }
    }

    @Test
    fun `error card re-pair only shows for token_invalido`() {
        assertTrue(LoadFailureReason.TOKEN_INVALIDO.showRePair())
        assertFalse(LoadFailureReason.SIN_SERVIDOR.showRePair())
        assertFalse(LoadFailureReason.ERROR_INESPERADO.showRePair())
    }
}
