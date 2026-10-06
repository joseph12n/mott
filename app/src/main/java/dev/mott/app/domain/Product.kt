package dev.mott.app.domain

// Catalogue product. Prices are in cents; formatting lives in Money.
data class Product(
    val id: String,
    val name: String,
    val priceCents: Long,
    val available: Boolean,
) {
    fun validate() {
        require(name.isNotBlank()) { "product name must not be blank" }
        require(priceCents >= 0) { "product price must be >= 0" }
    }
}
