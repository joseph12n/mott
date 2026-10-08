package dev.mott.app.ui.theme

import android.content.Context
import android.content.SharedPreferences

// Shell theme mode: Sistema follows the device (today's behavior),
// Claro/Oscuro pin the scheme. Persisted in plain SharedPreferences next
// to PairingStore/BrandStore; the choice is local-only and never travels
// in the branding DTO. T8 owns the full Claro/Oscuro UI in Personalizar;
// the shell only carries this toggle plus the MottTheme plumbing.
enum class ThemeMode(val label: String) {
    SISTEMA("Sistema"),
    CLARO("Claro"),
    OSCURO("Oscuro"),
}

// Resolves the mode against the device flag: Sistema tracks the system,
// pinned modes ignore it.
fun ThemeMode.resolveDark(systemDark: Boolean): Boolean = when (this) {
    ThemeMode.SISTEMA -> systemDark
    ThemeMode.CLARO -> false
    ThemeMode.OSCURO -> true
}

// Parses the persisted raw value; unknown, blank or missing values fall
// back to Sistema so a corrupt pref never pins the wrong scheme.
fun parseThemeMode(raw: String?): ThemeMode = when (raw?.trim()?.lowercase()) {
    "claro" -> ThemeMode.CLARO
    "oscuro" -> ThemeMode.OSCURO
    else -> ThemeMode.SISTEMA
}

class ThemeModeStore(
    private val prefs: SharedPreferences,
) {
    constructor(context: Context) : this(
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
    )

    fun get(): ThemeMode = parseThemeMode(prefs.getString(KEY_MODE, null))

    fun save(mode: ThemeMode) {
        prefs.edit().putString(KEY_MODE, storageValue(mode)).apply()
    }

    companion object {
        const val PREFS_NAME = "theme_mode"
        const val KEY_MODE = "mode"

        // Canonical lowercase storage values, parsed by parseThemeMode.
        fun storageValue(mode: ThemeMode): String = when (mode) {
            ThemeMode.SISTEMA -> "sistema"
            ThemeMode.CLARO -> "claro"
            ThemeMode.OSCURO -> "oscuro"
        }
    }
}
