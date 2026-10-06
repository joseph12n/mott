package dev.mott.app.domain

// Open tab for one table. All mutating operations return a new Tab.
data class Tab(
    val id: String,
    val tableId: String,
    val lines: List<OrderLine> = emptyList(),
    val isClosed: Boolean = false,
) {
    // Adds a line, merging quantities when the product is already present
    // (offline convenience; backend merges the same way). Keeps the
    // existing line's name/price and sums qty.
    fun addLine(line: OrderLine, productAvailable: Boolean): Tab {
        require(!isClosed) { "tab is closed" }
        require(productAvailable) { "product unavailable" }
        line.validate()
        val existing = lines.find { it.productId == line.productId }
        val next = if (existing == null) {
            lines + line
        } else {
            lines.map {
                if (it.productId == line.productId) it.copy(qty = it.qty + line.qty) else it
            }
        }
        return copy(lines = next)
    }

    fun totalCents(): Long = lines.sumOf { it.lineTotal }

    // Snapshots this tab into an immutable closed record.
    fun close(closedAtEpochMs: Long = System.currentTimeMillis()): ClosedTab {
        require(!isClosed) { "tab is closed" }
        return ClosedTab(
            id = id,
            tableId = tableId,
            lines = lines.toList(),
            totalCents = totalCents(),
            closedAtEpochMs = closedAtEpochMs,
        )
    }
}

// Immutable snapshot taken when a tab is closed.
data class ClosedTab(
    val id: String,
    val tableId: String,
    val lines: List<OrderLine>,
    val totalCents: Long,
    val closedAtEpochMs: Long,
)
