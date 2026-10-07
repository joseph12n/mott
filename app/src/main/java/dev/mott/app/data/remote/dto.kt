package dev.mott.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Wire DTOs matching the mitt LAN HTTP API (snake_case on the wire,
// camelCase in Kotlin via SerialName). Time fields stay raw ISO strings;
// parsing them is a UI concern, not a sync concern.

// GET /api/health -> {"status": "ok"}.
@Serializable
data class HealthResponse(
    val status: String,
)

// Catalogue product as stored by mitt.
@Serializable
data class ProductDto(
    val id: String,
    val name: String,
    @SerialName("price_cents") val priceCents: Long,
    val available: Boolean,
)

// GET /api/products -> {"products": [...]}.
@Serializable
data class ProductsResponse(
    val products: List<ProductDto> = emptyList(),
)

// POST /api/products body: available defaults true server-side.
@Serializable
data class ProductCreateRequest(
    val name: String,
    @SerialName("price_cents") val priceCents: Long,
    val available: Boolean? = null,
)

// PATCH /api/products/{id} body: nil fields keep their stored value.
@Serializable
data class ProductPatchRequest(
    val name: String? = null,
    @SerialName("price_cents") val priceCents: Long? = null,
    val available: Boolean? = null,
)

// GET /api/tables -> {"tables": [{id, label, occupied}]} with occupied
// derived from open tabs at read time, never stored.
@Serializable
data class TableDto(
    val id: String,
    val label: String,
    val occupied: Boolean,
)

@Serializable
data class TablesResponse(
    val tables: List<TableDto> = emptyList(),
)

// One running-bill line with the price snapshotted at order time.
@Serializable
data class OrderLineDto(
    @SerialName("product_id") val productId: String,
    val name: String,
    @SerialName("unit_price_cents") val unitPriceCents: Long,
    val qty: Int,
    @SerialName("line_total_cents") val lineTotalCents: Long,
)

// Running bill (cuenta) representation.
@Serializable
data class TabDto(
    val id: String,
    @SerialName("table_id") val tableId: String,
    val status: String,
    @SerialName("opened_at") val openedAt: String,
    val items: List<OrderLineDto> = emptyList(),
    @SerialName("total_cents") val totalCents: Long,
)

// GET /api/tabs/open -> {"tabs": [...]} oldest first.
@Serializable
data class OpenTabsResponse(
    val tabs: List<TabDto> = emptyList(),
)

// POST /api/tabs body; idempotent per open table.
@Serializable
data class OpenTabRequest(
    @SerialName("table_id") val tableId: String,
)

// POST /api/tabs/{id}/items body; same-product lines merge server-side.
@Serializable
data class AddItemRequest(
    @SerialName("product_id") val productId: String,
    val qty: Int,
)

// POST /api/tabs/{id}/close -> the finished sale.
@Serializable
data class SaleDto(
    val id: String,
    @SerialName("table_id") val tableId: String,
    val items: List<OrderLineDto> = emptyList(),
    @SerialName("total_cents") val totalCents: Long,
    @SerialName("closed_at") val closedAt: String,
)

// GET /api/sales -> {"sales": [...]} newest first. Each sale has the same
// {id, table_id, items, total_cents, closed_at} shape as closing a tab,
// so SaleDto is reused here, not duplicated.
@Serializable
data class SalesResponse(
    val sales: List<SaleDto> = emptyList(),
)

// GET /api/sales/today -> {"date" YYYY-MM-DD, count, total_cents}.
@Serializable
data class TodayResponse(
    val date: String,
    val count: Int,
    @SerialName("total_cents") val totalCents: Long,
)

// Bar expense as stored by mitt.
@Serializable
data class ExpenseDto(
    val id: String,
    val description: String,
    val qty: Double,
    @SerialName("cost_cents") val costCents: Long,
    val date: String,
)

// GET /api/expenses -> {"expenses": [...]} oldest first.
@Serializable
data class ExpensesResponse(
    val expenses: List<ExpenseDto> = emptyList(),
)

// POST /api/expenses body.
@Serializable
data class ExpenseCreateRequest(
    val description: String,
    val qty: Double,
    @SerialName("cost_cents") val costCents: Long,
)

// Failure envelope shared by every mitt endpoint:
// {"error": {"code", "message"}}.
@Serializable
data class ErrorBody(
    val code: String,
    val message: String,
)

@Serializable
data class ErrorEnvelope(
    val error: ErrorBody,
)
