package dev.mott.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color

// Cached hub identity from public GET /api/branding. The hub owns the
// name and colors; this cache only lets the phone paint on-brand while
// offline. Failures always keep the cached (or default) values silently.
data class Brand(
    val shopName: String,
    val primaryHex: String,
    val accentHex: String,
    val backgroundHex: String,
    val updatedAt: String = "",
)

class BrandStore(
    private val prefs: SharedPreferences,
) {
    constructor(context: Context) : this(
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
    )

    fun get(): Brand? {
        val shopName = prefs.getString(KEY_SHOP_NAME, null)
        val primary = prefs.getString(KEY_PRIMARY, null)
        val accent = prefs.getString(KEY_ACCENT, null)
        val background = prefs.getString(KEY_BACKGROUND, null)
        if (shopName.isNullOrEmpty() || primary.isNullOrEmpty() ||
            accent.isNullOrEmpty() || background.isNullOrEmpty()
        ) {
            return null
        }
        return Brand(
            shopName = shopName,
            primaryHex = primary,
            accentHex = accent,
            backgroundHex = background,
            updatedAt = prefs.getString(KEY_UPDATED_AT, "") ?: "",
        )
    }

    fun getOrDefault(): Brand = get() ?: DEFAULT

    fun save(brand: Brand) {
        save(
            shopName = brand.shopName,
            primary = brand.primaryHex,
            accent = brand.accentHex,
            background = brand.backgroundHex,
            updatedAt = brand.updatedAt,
        )
    }

    fun save(
        shopName: String,
        primary: String,
        accent: String,
        background: String,
        updatedAt: String = "",
    ) {
        prefs.edit()
            .putString(KEY_SHOP_NAME, shopName)
            .putString(KEY_PRIMARY, primary)
            .putString(KEY_ACCENT, accent)
            .putString(KEY_BACKGROUND, background)
            .putString(KEY_UPDATED_AT, updatedAt)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        const val PREFS_NAME = "brand"
        const val KEY_SHOP_NAME = "shop_name"
        const val KEY_PRIMARY = "primary"
        const val KEY_ACCENT = "accent"
        const val KEY_BACKGROUND = "background"
        const val KEY_UPDATED_AT = "updated_at"

        // Offline defaults mirror mitt DefaultBranding (dark tokens):
        // surface #1C1F24 as primary, accent #E8A33D, bg #121417.
        const val DEFAULT_SHOP_NAME = "mitt"
        const val DEFAULT_PRIMARY = "#1C1F24"
        const val DEFAULT_ACCENT = "#E8A33D"
        const val DEFAULT_BACKGROUND = "#121417"

        val DEFAULT = Brand(
            shopName = DEFAULT_SHOP_NAME,
            primaryHex = DEFAULT_PRIMARY,
            accentHex = DEFAULT_ACCENT,
            backgroundHex = DEFAULT_BACKGROUND,
        )
    }
}

// Parses a strict #rrggbb hub hex into an opaque Color, null on anything
// else. Same rule as mitt validHexColor: exactly '#' plus six hex digits,
// so bad hub data falls back to token defaults instead of crashing paint.
fun parseHex6(raw: String?): Color? {
    if (raw == null || raw.length != 7 || raw[0] != '#') return null
    var bits = 0
    for (char in raw.substring(1)) {
        val digit = char.digitToIntOrNull(16) ?: return null
        bits = bits * 16 + digit
    }
    return Color(0xFF000000.toInt() or bits)
}
