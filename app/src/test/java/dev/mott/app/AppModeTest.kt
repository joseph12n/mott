package dev.mott.app

import dev.mott.app.data.AppMode
import dev.mott.app.data.AppModeStore
import dev.mott.app.data.capabilitiesFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// T6a: mode enum, capability table and the persisted choice with the v0.2
// migration rule (paired users with no saved mode behave as HUB).
class AppModeTest {
    @Test
    fun `missing mode reads as UNSET`() {
        assertEquals(AppMode.UNSET, AppModeStore(FakePrefs()).get())
    }

    @Test
    fun `save then get round trips every mode`() {
        val prefs = FakePrefs()
        for (mode in listOf(AppMode.MOBILE, AppMode.HUB, AppMode.UNSET)) {
            AppModeStore(prefs).save(mode)
            assertEquals(mode, AppModeStore(prefs).get())
        }
    }

    @Test
    fun `unknown stored value reads as UNSET`() {
        val prefs = FakePrefs()
        prefs.edit().putString(AppModeStore.KEY_MODE, "SOMETHING_ELSE").apply()
        assertEquals(AppMode.UNSET, AppModeStore(prefs).get())
    }

    @Test
    fun `effective treats unset plus pairing as HUB without persisting`() {
        val prefs = FakePrefs()
        val store = AppModeStore(prefs)
        assertEquals(AppMode.HUB, store.effective(hasPairing = true))
        assertEquals(AppMode.UNSET, store.get())
        assertFalse(prefs.contains(AppModeStore.KEY_MODE))
    }

    @Test
    fun `effective keeps UNSET when unset and unpaired`() {
        assertEquals(AppMode.UNSET, AppModeStore(FakePrefs()).effective(hasPairing = false))
    }

    @Test
    fun `effective prefers the stored mode over the pairing`() {
        val store = AppModeStore(FakePrefs())
        store.save(AppMode.MOBILE)
        assertEquals(AppMode.MOBILE, store.effective(hasPairing = true))
        assertEquals(AppMode.MOBILE, store.effective(hasPairing = false))
        store.save(AppMode.HUB)
        assertEquals(AppMode.HUB, store.effective(hasPairing = false))
    }

    @Test
    fun `capabilities per mode`() {
        val mobile = capabilitiesFor(AppMode.MOBILE)
        assertTrue(mobile.canAdminCatalog)
        assertFalse(mobile.needsHub)
        val hub = capabilitiesFor(AppMode.HUB)
        assertFalse(hub.canAdminCatalog)
        assertTrue(hub.needsHub)
        val unset = capabilitiesFor(AppMode.UNSET)
        assertFalse(unset.canAdminCatalog)
        assertFalse(unset.needsHub)
        assertTrue(listOf(mobile, hub, unset).all { it.label.isNotBlank() })
        assertEquals(3, listOf(mobile, hub, unset).map { it.label }.toSet().size)
    }
}
