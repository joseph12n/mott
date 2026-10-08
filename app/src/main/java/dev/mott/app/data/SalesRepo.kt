package dev.mott.app.data

import dev.mott.app.data.remote.OpenTabRequest
import dev.mott.app.data.remote.AddItemRequest
import dev.mott.app.data.remote.MittApi
import dev.mott.app.data.remote.OrderLineDto
import dev.mott.app.data.remote.SaleDto
import dev.mott.app.data.remote.SupplierCreateRequest
import dev.mott.app.data.remote.SupplierDto
import dev.mott.app.data.remote.SupplierPatchRequest
import dev.mott.app.data.remote.TabDto
import dev.mott.app.data.remote.TableCreateRequest
import dev.mott.app.data.remote.TableDto
import dev.mott.app.domain.OrderLine
import dev.mott.app.domain.Tab
import dev.mott.app.ui.order.TableRef
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

// Supplier for the Proveedores section: mirrors the mitt supplier wire
// shape exactly (id, name, phone, note). The hub stores no per-supplier
// catalog or purchase ledger, so no balance fields exist here either.
data class Supplier(
    val id: String,
    val name: String,
    val phone: String,
    val note: String,
)

// Panel data seam: today totals plus recent sales for Panel, open tabs for
// the Mesas operate view. Fail-soft for the offline Panel: every getter
// keeps its last good payload in memory and serves it when the hub is
// unreachable or unpaired; with nothing cached yet the getters return the
// explicit empty (empty list / TodayResult.empty()), never throw on IO.
// HTTP errors always throw ApiException so a bad token or hub failure can
// never masquerade as "no sales".
class SalesRepo(
    private val apiProvider: () -> MittApi?,
    private val isOnline: () -> Boolean = { true },
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

    // T5 Mesas admin: direct table/tab mutations against the hub-owned
    // tables (list/create/delete) and the running bills (open/add/close).
    // Unlike the fail-soft getters above these throw: ApiException carries
    // the HTTP status so the UI can name 409 table_occupied and 422 label
    // failures inline, while transport/auth failures flow into the shared
    // SectionLoaders contract. The offline guard throws IOException before
    // any request goes out, so an offline admin tap reads as SIN_SERVIDOR
    // instead of hanging on a doomed socket. Nothing here touches the
    // offline-first queue paths in OrderViewModel.
    private fun requireAdminApi(): MittApi {
        if (!isOnline()) throw IOException("offline: admin mutation needs connectivity")
        return apiProvider() ?: throw IOException("unpaired: admin mutation needs a hub")
    }

    suspend fun listTables(): List<TableRef> {
        val api = requireAdminApi()
        val response = api.listTables()
        if (!response.isSuccessful) throw ApiException(response.code(), "tables failed: ${response.code()}")
        return (response.body()?.tables ?: emptyList()).map { it.toRef() }
    }

    suspend fun createTable(label: String): TableRef {
        val api = requireAdminApi()
        val response = api.createTable(TableCreateRequest(label = label))
        if (!response.isSuccessful) throw ApiException(response.code(), "create table failed: ${response.code()}")
        return response.body()?.toRef() ?: throw ApiException(response.code(), "create table empty body")
    }

    suspend fun deleteTable(id: String) {
        val api = requireAdminApi()
        val response = api.deleteTable(id)
        if (!response.isSuccessful) throw ApiException(response.code(), "delete table failed: ${response.code()}")
    }

    // Idempotent per open table: the hub answers 200 with the existing tab
    // when one is already open, 201 with a fresh one otherwise.
    suspend fun openTab(tableId: String): Tab {
        val api = requireAdminApi()
        val response = api.openTab(OpenTabRequest(tableId = tableId))
        if (!response.isSuccessful) throw ApiException(response.code(), "open tab failed: ${response.code()}")
        return response.body()?.toTab() ?: throw ApiException(response.code(), "open tab empty body")
    }

    // Same-product lines merge server-side; the returned tab is the merged
    // truth, never a local sum.
    suspend fun addItem(tabId: String, productId: String, qty: Int): Tab {
        val api = requireAdminApi()
        val response = api.addItem(tabId, AddItemRequest(productId = productId, qty = qty))
        if (!response.isSuccessful) throw ApiException(response.code(), "add item failed: ${response.code()}")
        return response.body()?.toTab() ?: throw ApiException(response.code(), "add item empty body")
    }

    // Direct close with no payment method (hub reduction): the sale shape
    // is the same one the close-tab queue op syncs, mapped once here.
    suspend fun closeTabNow(tabId: String): Sale {
        val api = requireAdminApi()
        val response = api.closeTab(tabId)
        if (!response.isSuccessful) throw ApiException(response.code(), "close tab failed: ${response.code()}")
        return response.body()?.toSale() ?: throw ApiException(response.code(), "close tab empty body")
    }

    // T7 Proveedores admin: full supplier CRUD against the hub-owned
    // supplier list (list/create/patch/delete). Hub ordering is by name
    // and the client preserves it (never re-sorts). Same throwing style
    // as the table admin above: ApiException carries the HTTP status so
    // the UI can name 422 limits and 404 gone-supplier inline, while
    // transport/auth failures flow into the shared SectionLoaders
    // contract. Unlike tables, DELETE has no guards (nothing references
    // suppliers yet), so delete needs no local occupancy check either.
    suspend fun listSuppliers(): List<Supplier> {
        val api = requireAdminApi()
        val response = api.listSuppliers()
        if (!response.isSuccessful) throw ApiException(response.code(), "suppliers failed: ${response.code()}")
        return (response.body()?.suppliers ?: emptyList()).map { it.toSupplier() }
    }

    suspend fun createSupplier(name: String, phone: String, note: String): Supplier {
        val api = requireAdminApi()
        val response = api.createSupplier(
            SupplierCreateRequest(name = name.trim(), phone = phone, note = note),
        )
        if (!response.isSuccessful) throw ApiException(response.code(), "create supplier failed: ${response.code()}")
        return response.body()?.toSupplier()
            ?: throw ApiException(response.code(), "create supplier empty body")
    }

    // Partial update: null fields keep their stored value server-side,
    // so callers pass only the fields the user changed.
    suspend fun patchSupplier(
        id: String,
        name: String? = null,
        phone: String? = null,
        note: String? = null,
    ): Supplier {
        val api = requireAdminApi()
        val response = api.patchSupplier(
            id,
            SupplierPatchRequest(name = name?.trim(), phone = phone, note = note),
        )
        if (!response.isSuccessful) throw ApiException(response.code(), "patch supplier failed: ${response.code()}")
        return response.body()?.toSupplier()
            ?: throw ApiException(response.code(), "patch supplier empty body")
    }

    suspend fun deleteSupplier(id: String) {
        val api = requireAdminApi()
        val response = api.deleteSupplier(id)
        if (!response.isSuccessful) throw ApiException(response.code(), "delete supplier failed: ${response.code()}")
    }

    private fun TableDto.toRef() = TableRef(
        id = id,
        label = label,
        occupied = occupied,
    )

    private fun SupplierDto.toSupplier() = Supplier(
        id = id,
        name = name,
        phone = phone,
        note = note,
    )

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
