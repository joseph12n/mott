package dev.mott.app.ui

import dev.mott.app.data.ApiException
import dev.mott.app.data.Brand
import dev.mott.app.data.parseHex6
import dev.mott.app.data.remote.MittApi
import dev.mott.app.ui.theme.ThemeMode
import java.util.Base64
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

// Pure Personalizar helpers behind the T8 section. They mirror the mitt PC
// reduction (frontend/src/views/Personalizar.tsx) exactly:
// - Every change stages locally; one merged PATCH /api/branding lands it.
//   Absent keys keep the stored value, explicit null clears the logo (hub
//   parseLogoField: absent stays nil, null decodes to "null").
// - The hub background stays untouched (the PC view sends it only on
//   factory reset); name, primary, accent and logo travel.
// - Dark mode is a local ThemeModeStore preference and never enters the
//   branding DTO (documented hub gap).
// - Logo cap mirrors domain.MaxLogoBytes (512KiB); the client rejects
//   oversized payloads BEFORE sending, the hub answers 413 past that.
// JVM-pure (no Android/Compose imports) so unit tests cover it fully;
// English identifiers, every user-visible string Spanish.

// Gallery preset: one {brand, accent} pair from presets.ts (mitt PC +
// Figma master share the same 22). The third gallery stripe is not
// stored: both web views derive it as color-mix(in srgb, brand 14%,
// white), ported here as presetTintHex.
data class BrandPreset(
    val id: String,
    val name: String,
    val tag: String,
    val brandHex: String,
    val accentHex: String,
)

val BRAND_PRESETS: List<BrandPreset> = listOf(
    BrandPreset("esmeralda", "Esmeralda Bistró", "Restaurante", "#0e7a5a", "#f2a33a"),
    BrandPreset("tomate", "Trattoria Tomate", "Pizzería", "#c8372d", "#f2b441"),
    BrandPreset("moka", "Café Moka", "Cafetería", "#7a4e33", "#d99a4e"),
    BrandPreset("nori", "Sushi Nori", "Asiática", "#24313d", "#e4573d"),
    BrandPreset("brasa", "Brasa y Fuego", "Parrilla", "#d4501a", "#f0b429"),
    BrandPreset("neon", "Bar Neón", "Coctelería", "#7c3aed", "#16c7e0"),
    BrandPreset("fresa", "Heladería Fresa", "Heladería", "#e0457b", "#3cc0a6"),
    BrandPreset("trigo", "Panadería Trigo", "Panadería", "#b7791f", "#7a9e3a"),
    BrandPreset("oceano", "Océano", "Marisquería", "#0b6fb8", "#f2a33a"),
    BrandPreset("fresco", "Verde Fresco", "Saludable", "#3f8f3a", "#f08a24"),
    BrandPreset("chile", "Taquería Chile", "Mexicana", "#b3261e", "#2f9e6e"),
    BrandPreset("lavanda", "Pastelería Lavanda", "Pastelería", "#8e6bbf", "#f08fb2"),
    BrandPreset("medianoche", "Medianoche", "Alta cocina", "#2d3b55", "#d9aa45"),
    BrandPreset("indigo", "Índigo Express", "Comida rápida", "#4a4fd8", "#ffb020"),
    BrandPreset("ambar", "Cervecería Ámbar", "Cervecería", "#b86f0a", "#3b7a9e"),
    BrandPreset("tropical", "Jugos Tropical", "Jugos", "#ee6a1c", "#2fb344"),
    BrandPreset("sakura", "Rosa Sakura", "Té y brunch", "#cf4d79", "#6b94a8"),
    BrandPreset("matcha", "Té Matcha", "Casa de té", "#5b7f3b", "#c9a24a"),
    BrandPreset("turquesa", "Turquesa Caribe", "Playa / Bar", "#0e8c97", "#ff7a59"),
    BrandPreset("vino", "Bodega Vino", "Vinoteca", "#7d1f3d", "#d8a657"),
    BrandPreset("carbon", "Carbón", "Minimal", "#303733", "#e8c547"),
    BrandPreset("mostaza", "Deli Mostaza", "Sándwiches", "#c58b0a", "#d6452f"),
)

// Client-side logo cap mirroring domain.MaxLogoBytes (512KiB).
const val MAX_LOGO_BYTES: Int = 512 * 1024

// Hub logo kinds (domain LogoMIME*): PNG, JPEG, SVG. The Android picker
// yields PNG/JPEG in practice; SVG stays accepted for hub parity.
val ACCEPTED_LOGO_MIMES: Set<String> = setOf("image/png", "image/jpeg", "image/svg+xml")

// Hub shop-name rule (domain ErrInvalidShopName): 1..60, trimmed.
const val SHOP_NAME_MAX = 60

// Theme-mode picker order for the Personalizar Tema card: Sistema first
// (today's default), then the pinned modes. The shell TEMA toggle cycles
// the same three values through the same ThemeModeStore.
fun personalizarThemeOptions(): List<ThemeMode> =
    listOf(ThemeMode.SISTEMA, ThemeMode.CLARO, ThemeMode.OSCURO)

// Strict #rrggbb check, same rule as parseHex6 (mitt validHexColor):
// exactly '#' plus six hex digits, so bad input never reaches the hub.
fun isValidHex6(raw: String?): Boolean = parseHex6(raw) != null

// Resolves the PC gallery tint stripe (color-mix(in srgb, brand 14%,
// white)) to a static lowercase hex: 14% brand over white per channel.
// Null on anything off spec so bad data never breaks paint.
fun presetTintHex(brandHex: String): String? {
    val raw = brandHex.trimStart('#')
    if (brandHex.length != 7 || !brandHex.startsWith('#')) return null
    val bits = raw.toLongOrNull(16) ?: return null
    fun mix(channel: Long): Int = (255 - (0.14 * (255 - channel))).toInt().coerceIn(0, 255)
    val red = mix((bits shr 16) and 0xFF)
    val green = mix((bits shr 8) and 0xFF)
    val blue = mix(bits and 0xFF)
    return "#%02x%02x%02x".format(red, green, blue)
}

// Client mirror of the hub branding rules. Null means the draft is
// valid; anything else is the inline message, same words the PC view
// notifies with.
fun validateBrandingDraft(base: Brand, shopName: String, primary: String, accent: String): String? {
    val name = shopName.trim()
    if (name.isEmpty() || name.length > SHOP_NAME_MAX) return "El nombre lleva de 1 a 60 caracteres."
    if (!isValidHex6(primary) || !isValidHex6(accent)) return "Usa el formato #rrggbb."
    return null
}

// Logo staging: KEEP sends nothing (absent key keeps the hub logo),
// SET sends the staged data URL, CLEAR sends explicit null.
enum class LogoMode {
    KEEP,
    SET,
    CLEAR,
}

// One staged save: the base live branding plus every locally edited
// value. toPatchBody returns null when nothing changed, so callers send
// at most ONE merged PATCH per save and never an empty one.
data class StagedBranding(
    val base: Brand,
    val shopName: String,
    val primary: String,
    val accent: String,
    val logoMode: LogoMode,
    val logoDataUrl: String,
) {
    val isDirty: Boolean
        get() = shopName.trim() != base.shopName ||
            primary != base.primaryHex ||
            accent != base.accentHex ||
            logoMode != LogoMode.KEEP

    fun toPatchBody(): JsonObject? {
        if (!isDirty) return null
        return buildJsonObject {
            val name = shopName.trim()
            if (name != base.shopName) put("shop_name", JsonPrimitive(name))
            if (primary != base.primaryHex) put("primary", JsonPrimitive(primary))
            if (accent != base.accentHex) put("accent", JsonPrimitive(accent))
            when (logoMode) {
                LogoMode.SET -> if (logoDataUrl.isNotEmpty()) put("logo_data_url", JsonPrimitive(logoDataUrl))
                LogoMode.CLEAR -> put("logo_data_url", JsonPrimitive(null as String?))
                LogoMode.KEEP -> Unit
            }
        }
    }
}

fun stagedBrandingDraft(
    base: Brand,
    shopName: String,
    primary: String,
    accent: String,
    logoMode: LogoMode,
    logoDataUrl: String,
): StagedBranding = StagedBranding(
    base = base,
    shopName = shopName,
    primary = primary,
    accent = accent,
    logoMode = logoMode,
    logoDataUrl = logoDataUrl,
)

// Builds a hub-ready logo value: data:<mime>;base64,... with no line
// wraps (java.util.Base64 needs API 26, the app minSdk, so no Android
// Base64 dependency and JVM tests cover this).
fun buildLogoDataUrl(mime: String, bytes: ByteArray): String =
    "data:$mime;base64," + Base64.getEncoder().encodeToString(bytes)

// True when the staged data URL decodes past the 512KiB hub cap: the
// caller must surface the named error and send NOTHING.
fun logoDataUrlTooLarge(dataUrl: String): Boolean = logoBytesOf(dataUrl)?.let { it.size > MAX_LOGO_BYTES } ?: false

// Client mirror of the hub parseLogoDataURL gate. Null means the value
// is sendable; anything else is the inline message, same words the PC
// view notifies with ("El logo debe ser PNG, JPEG o SVG.",
// "El logo no debe superar 512 KB.").
fun validateLogoDataUrl(dataUrl: String): String? {
    if (!dataUrl.startsWith("data:")) return "El logo debe ser PNG, JPEG o SVG."
    val marker = ";base64,"
    val split = dataUrl.indexOf(marker)
    if (split < 0) return "El logo debe ser PNG, JPEG o SVG."
    val mime = dataUrl.substring("data:".length, split)
    if (mime !in ACCEPTED_LOGO_MIMES) return "El logo debe ser PNG, JPEG o SVG."
    val raw = runCatching {
        Base64.getDecoder().decode(dataUrl.substring(split + marker.length))
    }.getOrNull() ?: return "El logo debe ser PNG, JPEG o SVG."
    if (raw.isEmpty()) return "El logo debe ser PNG, JPEG o SVG."
    if (raw.size > MAX_LOGO_BYTES) return "El logo no debe superar 512 KB."
    return null
}

// Decodes the raw logo bytes behind a data URL, null when malformed.
fun logoBytesOf(dataUrl: String): ByteArray? {
    val marker = ";base64,"
    val split = dataUrl.indexOf(marker)
    if (!dataUrl.startsWith("data:") || split < 0) return null
    return runCatching {
        Base64.getDecoder().decode(dataUrl.substring(split + marker.length))
    }.getOrNull()
}

// HTTP status alone decides the inline save copy; anything without a
// named copy returns null and the caller falls back to the shared
// SectionLoaders contract. 413 is the hub oversized-logo twin of the
// client-side 512KiB guard (race: the file grew between pick and save).
fun brandingSaveInlineCopy(status: Int): String? = when (status) {
    401, 403 -> "El servidor se reinició o cambió su token. Volvé a vincular la app desde Conexión."
    413 -> "El logo es demasiado grande (máximo 512 KB)."
    422 -> "Revisá los datos: nombre 1-60 y colores #rrggbb."
    else -> null
}

// Single merged branding save against the authed PATCH /api/branding.
// The hub answers the merged truth, mapped once to Brand so the caller
// can persist it into BrandStore and rebase the staging form (PC
// onSaved parity). Throws ApiException with the HTTP status on any
// non-2xx so the UI can name 401/413/422 inline.
suspend fun patchBranding(api: MittApi, body: JsonObject): Brand {
    val response = api.patchBranding(body)
    if (!response.isSuccessful) throw ApiException(response.code(), "patch branding failed: ${response.code()}")
    return response.body()?.toBrand()
        ?: throw ApiException(response.code(), "patch branding empty body")
}
