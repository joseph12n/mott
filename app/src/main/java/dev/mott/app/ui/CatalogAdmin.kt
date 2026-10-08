package dev.mott.app.ui

import dev.mott.app.data.ApiException
import dev.mott.app.data.remote.MittApi
import dev.mott.app.data.remote.ProductCreateRequest
import dev.mott.app.data.remote.ProductDto
import dev.mott.app.data.remote.ProductPatchRequest
import dev.mott.app.domain.Product
import java.io.IOException

// Pure Catalogo admin helpers behind the T6 parity UI. They mirror the mitt
// PC Catalogo reduction exactly:
// - The hub has NO category field, so the list stays FLAT (no client-side
//   pseudo-categories; the mottandmittdesing mock groups by category but the
//   hub reduction flattened that away, and grouping by a field the hub
//   never sends would invent data).
// - The hub has NO product DELETE, so the trash control PATCHes
//   available=false (products stay listed, visibly disabled, same as web).
// - POST /api/products {name, price_cents} answers 201 or 422
//   (validation_error on empty name / negative price); PATCH
//   /api/products/{id} {available} answers 200, 404 or 422.
// English identifiers; every user-visible string stays Spanish.
fun parseProductPriceToCents(raw: String): Long? {
    val normalized = raw.trim().replace(',', '.')
    if (normalized.isEmpty()) return null
    val amount = normalized.toDoubleOrNull() ?: return null
    // Hub rule is price_cents >= 0: a free item is a valid product, unlike
    // the Gastos amount which must stay positive.
    if (!amount.isFinite() || amount < 0.0) return null
    return Math.round(amount * 100)
}

// Client-side mirror of the hub product rule (non-blank name,
// price_cents >= 0). Null means the input is valid; anything else is the
// inline message, same words the web master notifies with.
fun validateProductInput(name: String, priceCents: Long?): String? {
    if (name.trim().isEmpty()) return "Escribí el nombre del producto."
    if (priceCents == null) return "Escribí un precio válido."
    return null
}

fun productCreateInlineCopy(status: Int): String? = when (status) {
    422 -> "Revisá el nombre y el precio del producto."
    else -> null
}

fun productToggleInlineCopy(status: Int): String? = when (status) {
    404 -> "El producto ya no existe."
    else -> null
}

// Mesas pick-flow exclusion: unavailable products never reach the order
// draft. The Catalogo rows stay visible but untappable (same rule as web),
// and the Mesas product select filters on this too.
fun pickableProducts(products: List<Product>): List<Product> =
    products.filter { it.available }

// Catalogo admin seam: direct product mutations against the hub-owned
// catalog (create + availability toggle). Like the T5 SalesRepo admin path
// these throw instead of fail-softing: ApiException carries the HTTP status
// so the UI can name 422/404 inline, while transport/auth failures flow
// into the shared SectionLoaders contract. The offline guard throws
// IOException before any request goes out, so an offline admin tap reads
// as SIN_SERVIDOR instead of hanging on a doomed socket. Cancellation is
// never caught here, so it keeps propagating (R3-002).
class CatalogAdminRepo(
    private val apiProvider: () -> MittApi?,
    private val isOnline: () -> Boolean = { true },
) {
    suspend fun createProduct(name: String, priceCents: Long): Product {
        val api = requireAdminApi()
        val response = api.createProduct(ProductCreateRequest(name = name.trim(), priceCents = priceCents))
        if (!response.isSuccessful) throw ApiException(response.code(), "create product failed: ${response.code()}")
        return response.body()?.toProduct()
            ?: throw ApiException(response.code(), "create product empty body")
    }

    // Trash control: marks the product unavailable, never deletes it (the
    // hub exposes no product DELETE; unavailable rows stay listed).
    suspend fun setAvailable(id: String, available: Boolean): Product {
        val api = requireAdminApi()
        val response = api.patchProduct(id, ProductPatchRequest(available = available))
        if (!response.isSuccessful) throw ApiException(response.code(), "patch product failed: ${response.code()}")
        return response.body()?.toProduct()
            ?: throw ApiException(response.code(), "patch product empty body")
    }

    private fun requireAdminApi(): MittApi {
        if (!isOnline()) throw IOException("offline: admin mutation needs connectivity")
        return apiProvider() ?: throw IOException("unpaired: admin mutation needs a hub")
    }

    private fun ProductDto.toProduct() = Product(
        id = id,
        name = name,
        priceCents = priceCents,
        available = available,
    )
}
