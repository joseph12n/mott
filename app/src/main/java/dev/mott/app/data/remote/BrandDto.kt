package dev.mott.app.data.remote

import dev.mott.app.data.Brand
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// GET /api/branding -> {shop_name, primary, accent, background,
// has_logo, updated_at}. Field names mirror mitt internal/api/branding.go
// brandingDTO 1:1. has_logo stays informational: the logo paints on the
// web hub only and is never fetched on-device (no image deps by design).
@Serializable
data class BrandingResponse(
    @SerialName("shop_name") val shopName: String,
    val primary: String,
    val accent: String,
    val background: String,
    @SerialName("has_logo") val hasLogo: Boolean = false,
    @SerialName("updated_at") val updatedAt: String = "",
) {
    fun toBrand(): Brand = Brand(
        shopName = shopName,
        primaryHex = primary,
        accentHex = accent,
        backgroundHex = background,
        updatedAt = updatedAt,
    )
}
