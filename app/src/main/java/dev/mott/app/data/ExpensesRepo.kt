package dev.mott.app.data

import dev.mott.app.data.remote.MittApi
import java.io.IOException
import java.util.UUID

// Expense row for the Gastos section: mirrors the mitt expense wire
// (description/qty/cost_cents/date) with the date kept as the raw hub
// string; formatting it is a UI concern, not a sync concern.
data class ExpenseItem(
    val id: String,
    val description: String,
    val costCents: Long,
    val date: String,
)

// Gastos data seam: list plus add over the mitt expenses wire with the
// same fail-soft contract as SalesRepo. The list keeps its last good
// payload in memory and serves it when the hub is unreachable or the
// device is unpaired; with nothing cached yet it returns the explicit
// empty list, never throws on IO. HTTP errors always throw ApiException
// so a bad token or hub failure can never masquerade as "no expenses".
// Adds go through the offline outbox (ADD_EXPENSE) and drain when online,
// reporting SINCRONIZADO or PENDIENTE (n) like the ANOTAR path.
class ExpensesRepo(
    private val apiProvider: () -> MittApi?,
    private val queue: PendingQueue,
    private val isOnline: () -> Boolean = { true },
    private val drain: (suspend () -> SyncReport)? = null,
) {
    private var last: List<ExpenseItem> = emptyList()
    private var hasCache = false

    suspend fun list(): List<ExpenseItem> {
        val api = apiProvider() ?: return cachedOrEmpty()
        try {
            val response = api.listExpenses()
            if (!response.isSuccessful) throw ApiException(response.code(), "expenses failed: ${response.code()}")
            val items = (response.body()?.expenses ?: emptyList()).map {
                ExpenseItem(id = it.id, description = it.description, costCents = it.costCents, date = it.date)
            }
            last = items
            hasCache = true
            return items
        } catch (e: IOException) {
            return cachedOrEmpty()
        }
    }

    suspend fun add(description: String, qty: Double, costCents: Long): String {
        require(description.isNotBlank()) { "expense description must not be blank" }
        require(qty > 0) { "expense qty must be > 0" }
        require(costCents >= 0) { "expense cost must be >= 0" }
        val payload = ExpensePayload(
            id = UUID.randomUUID().toString(),
            description = description.trim(),
            qty = qty,
            costCents = costCents,
            dateEpochMs = System.currentTimeMillis(),
        )
        queue.enqueue(OpTypes.ADD_EXPENSE, PendingQueue.encode(payload))
        if (!isOnline()) {
            return "PENDIENTE (${queue.peekAll().size})"
        }
        drain?.invoke()
        val pending = queue.peekAll().size
        return if (pending == 0) "SINCRONIZADO" else "PENDIENTE ($pending)"
    }

    private suspend fun cachedOrEmpty(): List<ExpenseItem> = if (hasCache) last else emptyList()
}
