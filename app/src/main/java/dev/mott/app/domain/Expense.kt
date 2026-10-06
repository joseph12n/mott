package dev.mott.app.domain

// Bar expense (e.g. restocking). Cost is in cents; formatting lives in Money.
data class Expense(
    val id: String,
    val description: String,
    val qty: Double,
    val costCents: Long,
    val dateEpochMs: Long,
) {
    fun validate() {
        require(description.isNotBlank()) { "expense description must not be blank" }
        require(qty > 0) { "expense qty must be > 0" }
        require(costCents >= 0) { "expense cost must be >= 0" }
    }
}
