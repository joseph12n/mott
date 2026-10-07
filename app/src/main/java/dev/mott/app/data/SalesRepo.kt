package dev.mott.app.data

import dev.mott.app.data.remote.MittApi
import dev.mott.app.data.remote.OrderLineDto
import dev.mott.app.data.remote.SaleDto
import dev.mott.app.data.remote.TabDto
import dev.mott.app.domain.OrderLine
import dev.mott.app.domain.Tab
import java.io.IOException

// Typed hub failure: carries the HTTP status so callers can tell an
// expired pairing (401/403) from a hub-side problem (5xx).
class ApiException(val status: Int, message: String) : Exception(message)

// Closed sale for the Panel section: mapped from the mitt sale wire shape
// (same as POST /api/tabs/{id}/close), never a second DTO.
data class Sale(
    val id: String,
    val tableId: String,
    val items: List<OrderLine>,
    val totalCents: Long,
    val closedAt: String,
)

// Day aggregate for the Panel hero: mirrors GET /api/sales/today exactly.
data class TodayResult(
    val date: String,
    val count: Int,
    val totalCents: Long,
) {
    companion object {
        fun empty() = TodayResult(date = "", count = 0, totalCents = 0)
    }
}

// Panel data seam: today totals plus recent sales for Panel, open tabs for
// the Mesas operate view. Fail-soft for the offline Panel: every getter
// keeps its last good payload in memory and serves it when the hub is
// unreachable or unpaired; with nothing cached yet the getters return the
// explicit empty (empty list / TodayResult.empty()), never throw on IO.
// HTTP errors always throw ApiException so a bad token or hub failure can
// never masquerade as "no sales".
class SalesRepo(
    private val apiProvider: () -> MittApi?,
) {
    private var lastToday: TodayResult? = null
    private var lastRecent: List<Sale> = emptyList()
    private var hasRecent = false
    private var lastTabs: List<Tab> = emptyList()
    private var hasTabs = false

    suspend fun today(): TodayResult {
        val api = apiProvider() ?: return lastToday ?: TodayResult.empty()
        try {
            val response = api.salesToday()
            if (!response.isSuccessful) throw ApiException(response.code(), "sales today failed: ${response.code()}")
            val body = response.body() ?: throw ApiException(response.code(), "sales today empty body")
            return TodayResult(date = body.date, count = body.count, totalCents = body.totalCents)
                .also { lastToday = it }
        } catch (e: IOException) {
            return lastToday ?: TodayResult.empty()
        }
    }

    // Newest first, hub order preserved (the client never re-sorts).
    suspend fun recent(limit: Int = 50): List<Sale> {
        val api = apiProvider() ?: return lastRecentOrEmpty()
        try {
            val response = api.listSales(limit)
            if (!response.isSuccessful) throw ApiException(response.code(), "sales failed: ${response.code()}")
            val sales = (response.body()?.sales ?: emptyList()).map { it.toSale() }
            lastRecent = sales
            hasRecent = true
            return sales
        } catch (e: IOException) {
            return lastRecentOrEmpty()
        }
    }

    // Reuses the existing GET /api/tabs/open plus TabDto: the only new code
    // is this DTO-to-domain map onto Tab/OrderLine, no new wire types.
    suspend fun openTabs(): List<Tab> {
        val api = apiProvider() ?: return if (hasTabs) lastTabs else emptyList()
        try {
            val response = api.openTabs()
            if (!response.isSuccessful) throw ApiException(response.code(), "open tabs failed: ${response.code()}")
            val tabs = (response.body()?.tabs ?: emptyList()).map { it.toTab() }
            lastTabs = tabs
            hasTabs = true
            return tabs
        } catch (e: IOException) {
            return if (hasTabs) lastTabs else emptyList()
        }
    }

    private fun lastRecentOrEmpty(): List<Sale> = if (hasRecent) lastRecent else emptyList()

    private fun SaleDto.toSale() = Sale(
        id = id,
        tableId = tableId,
        items = items.map { it.toLine() },
        totalCents = totalCents,
        closedAt = closedAt,
    )

    private fun TabDto.toTab() = Tab(
        id = id,
        tableId = tableId,
        lines = items.map { it.toLine() },
        isClosed = status == "closed",
    )

    private fun OrderLineDto.toLine() = OrderLine(
        productId = productId,
        name = name,
        unitPriceCents = unitPriceCents,
        qty = qty,
    )
}
