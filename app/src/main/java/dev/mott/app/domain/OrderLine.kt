package dev.mott.app.domain

// One line on an open tab. Immutable; quantity changes go through Tab.addLine.
data class OrderLine(
    val productId: String,
    val name: String,
    val unitPriceCents: Long,
    val qty: Int,
) {
    val lineTotal: Long get() = unitPriceCents * qty

    fun validate() {
        require(qty > 0) { "line qty must be > 0" }
        require(unitPriceCents >= 0) { "line unit price must be >= 0" }
    }
}
