package dev.mott.app.data

import android.content.Context
import android.content.SharedPreferences

// How this phone runs the bar. The app no longer requires a PC:
// MOBILE  — the phone is its own hub (local data, full catalog control).
// HUB     — a PC hub owns the catalog and the phone is an operating client.
// UNSET   — no choice made yet (or a v0.2 install that never picked one).
enum class AppMode { UNSET, MOBILE, HUB }

// What a mode allows the phone to do. canAdminCatalog gates creating or
// deleting tables, adding products, suppliers and branding edits; needsHub
// marks modes whose data lives on the PC.
data class Capabilities(
    val canAdminCatalog: Boolean,
    val needsHub: Boolean,
    val label: String,
)

fun capabilitiesFor(mode: AppMode): Capabilities = when (mode) {
    AppMode.MOBILE -> Capabilities(
        canAdminCatalog = true,
        needsHub = false,
        label = "Solo en el móvil",
    )

    AppMode.HUB -> Capabilities(
        canAdminCatalog = false,
        needsHub = true,
        label = "Con la PC del bar",
    )

    AppMode.UNSET -> Capabilities(
        canAdminCatalog = false,
        needsHub = false,
        label = "Sin definir",
    )
}

// Persisted mode choice. Plain prefs, same as PairingStore: the value is a
// preference, not a secret.
class AppModeStore(
    private val prefs: SharedPreferences,
) {
    constructor(context: Context) : this(
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
    )

    // Missing or unknown stored text degrades to UNSET so a future enum
    // value never crashes an older install.
    fun get(): AppMode {
        val raw = prefs.getString(KEY_MODE, null) ?: return AppMode.UNSET
        return runCatching { AppMode.valueOf(raw) }.getOrDefault(AppMode.UNSET)
    }

    fun save(mode: AppMode) {
        prefs.edit().putString(KEY_MODE, mode.name).apply()
    }

    // Migration rule for installs that paired before modes existed: with no
    // stored choice, an existing pairing keeps behaving as HUB (v0.2
    // behavior). Nothing is persisted, so the user can still switch.
    fun effective(hasPairing: Boolean): AppMode {
        val stored = get()
        if (stored != AppMode.UNSET) return stored
        return if (hasPairing) AppMode.HUB else AppMode.UNSET
    }

    companion object {
        const val PREFS_NAME = "app_mode"
        const val KEY_MODE = "mode"
    }
}
