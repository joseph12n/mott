package dev.mott.app.data.standalone

import dev.mott.app.data.remote.AddItemRequest
import dev.mott.app.data.remote.BrandingResponse
import dev.mott.app.data.remote.ErrorBody
import dev.mott.app.data.remote.ErrorEnvelope
import dev.mott.app.data.remote.ExpenseCreateRequest
import dev.mott.app.data.remote.ExpenseDto
import dev.mott.app.data.remote.ExpensesResponse
import dev.mott.app.data.remote.HealthResponse
import dev.mott.app.data.remote.MittApi
import dev.mott.app.data.remote.OpenTabRequest
import dev.mott.app.data.remote.OpenTabsResponse
import dev.mott.app.data.remote.OrderLineDto
import dev.mott.app.data.remote.ProductCreateRequest
import dev.mott.app.data.remote.ProductDto
import dev.mott.app.data.remote.ProductPatchRequest
import dev.mott.app.data.remote.ProductsResponse
import dev.mott.app.data.remote.SaleDto
import dev.mott.app.data.remote.SalesResponse
import dev.mott.app.data.remote.SupplierCreateRequest
import dev.mott.app.data.remote.SupplierDto
import dev.mott.app.data.remote.SupplierPatchRequest
import dev.mott.app.data.remote.SuppliersResponse
import dev.mott.app.data.remote.TabDto
import dev.mott.app.data.remote.TableCreateRequest
import dev.mott.app.data.remote.TableDto
import dev.mott.app.data.remote.TablesResponse
import dev.mott.app.data.remote.TodayResponse
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response
import java.time.Instant
import java.time.ZoneId
import java.util.Base64
import java.util.UUID

// MittApi implemented on a LocalStore so the phone works with no PC hub.
// Mirrors mitt internal/api/*.go and internal/domain/*.go: same status codes,
// {error:{code,message}} bodies, validation rules, ordering and merge behavior.
//
// Intentional deviations from the hub:
//  - GET /api/sales/today uses the local calendar day of `zone` (the hub uses
//    UTC), because the phone's owner lives in one local timezone.
//  - Ids are whatever `newId` returns (UUID by default, hub uses 128-bit hex).
// Everything else is parity, including quirks (duplicate table labels allowed,
// product names not trimmed, shop name stored untrimmed after validation).
class LocalMittApi(
    private val store: LocalStore,
    private val clock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val zone: ZoneId = ZoneId.systemDefault(),
) : MittApi {
    // Serializes read-modify-write sequences (open tab, add item, close, patch).
    private val mutex = Mutex()

    // ---- health -----------------------------------------------------------

    override suspend fun health(): Response<HealthResponse> = ok(HealthResponse("ok"))

    // ---- products ---------------------------------------------------------

    override suspend fun listProducts(available: Boolean?): Response<ProductsResponse> {
        val products = store.listProducts()
            .filter { available == null || it.available == available }
            .sortedBy { it.name }
            .map { it.toDto() }
        return ok(ProductsResponse(products))
    }

    override suspend fun createProduct(body: ProductCreateRequest): Response<ProductDto> = mutex.withLock {
        val p = StoredProduct(newId(), body.name, body.priceCents, body.available ?: true)
        validateProduct(p)?.let { return fail(422, "validation_error", it) }
        store.saveProduct(p)
        ok(p.toDto(), 201)
    }

    override suspend fun patchProduct(id: String, body: ProductPatchRequest): Response<ProductDto> = mutex.withLock {
        val current = store.getProduct(id) ?: return fail(404, "not_found", "unknown product")
        val p = current.copy(
            name = body.name ?: current.name,
            priceCents = body.priceCents ?: current.priceCents,
            available = body.available ?: current.available,
        )
        validateProduct(p)?.let { return fail(422, "validation_error", it) }
        store.saveProduct(p)
        ok(p.toDto())
    }

    // ---- tables -----------------------------------------------------------

    override suspend fun listTables(): Response<TablesResponse> {
        val occupied = store.listOpenTabs().map { it.tableId }.toSet()
        val tables = store.listTables().sortedBy { it.label }.map { TableDto(it.id, it.label, it.id in occupied) }
        return ok(TablesResponse(tables))
    }

    override suspend fun createTable(body: TableCreateRequest): Response<TableDto> = mutex.withLock {
        val label = body.label.trim()
        validateTableLabel(label)?.let { return fail(422, "validation_error", it) }
        val t = StoredTable(newId(), label)
        store.saveTable(t)
        ok(TableDto(t.id, t.label, false), 201)
    }

    override suspend fun deleteTable(id: String): Response<Unit> = mutex.withLock {
        if (store.listOpenTabs().any { it.tableId == id }) {
            return fail(409, "table_occupied", "table has an open tab")
        }
        if (!store.deleteTable(id)) return fail(404, "not_found", "unknown table")
        ok(Unit, 204)
    }

    // ---- tabs -------------------------------------------------------------

    override suspend fun openTab(body: OpenTabRequest): Response<TabDto> = mutex.withLock {
        validateTableLabel(body.tableId.trim())?.let { return fail(422, "validation_error", it) }
        if (store.getTable(body.tableId) == null) {
            return fail(422, "unknown_table", "unknown table: create it first via POST /api/tables")
        }
        store.getOpenTabByTable(body.tableId)?.let { return ok(it.toDto()) }
        val tab = StoredTab(newId(), body.tableId, open = true, openedAt = clock())
        store.saveTab(tab)
        ok(tab.toDto(), 201)
    }

    override suspend fun openTabs(): Response<OpenTabsResponse> =
        ok(OpenTabsResponse(store.listOpenTabs().map { it.toDto() }))

    override suspend fun addItem(tabId: String, body: AddItemRequest): Response<TabDto> = mutex.withLock {
        val product = store.getProduct(body.productId) ?: return fail(404, "not_found", "unknown product")
        val tab = store.listOpenTabs().firstOrNull { it.id == tabId }
            ?: return fail(404, "not_found", "unknown tab")
        if (!product.available) return fail(422, "validation_error", "product is not available")
        if (body.qty <= 0) return fail(422, "validation_error", "quantity must be greater than zero")
        if (product.priceCents < 0) return fail(422, "validation_error", "unit price must not be negative")
        // Same-product lines merge into the first line, keeping its price snapshot.
        val lines = tab.lines.toMutableList()
        val at = lines.indexOfFirst { it.productId == product.id }
        if (at >= 0) {
            lines[at] = lines[at].copy(qty = lines[at].qty + body.qty)
        } else {
            lines.add(StoredLine(product.id, product.name, product.priceCents, body.qty))
        }
        val saved = tab.copy(lines = lines)
        store.saveTab(saved)
        // The hub answers with the merged tab in first-seen line order.
        ok(saved.toDto(sorted = false))
    }

    override suspend fun closeTab(tabId: String): Response<SaleDto> = mutex.withLock {
        val tab = store.listOpenTabs().firstOrNull { it.id == tabId }
            ?: return fail(404, "not_found", "unknown tab")
        if (tab.lines.isEmpty()) return fail(422, "validation_error", "tab has no items")
        val sale = StoredSale(
            id = tab.id,
            tableId = tab.tableId,
            lines = tab.lines,
            totalCents = tab.lines.sumOf { it.unitPriceCents * it.qty },
            closedAt = clock(),
        )
        // Archive the sale first, then flip the tab: a retry upserts the same row.
        store.saveSale(sale)
        store.saveTab(tab.copy(open = false))
        ok(sale.toDto())
    }

    // ---- sales ------------------------------------------------------------

    override suspend fun listSales(limit: Int?): Response<SalesResponse> {
        var n = SALES_DEFAULT_LIMIT
        if (limit != null) {
            if (limit < 1) return fail(400, "bad_request", "limit must be a positive integer")
            n = minOf(limit, SALES_MAX_LIMIT)
        }
        return ok(SalesResponse(store.listSales(n).map { it.toDto() }))
    }

    override suspend fun salesToday(): Response<TodayResponse> {
        val day = Instant.ofEpochMilli(clock()).atZone(zone).toLocalDate()
        val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val sales = store.listSalesBetween(start, end)
        return ok(TodayResponse(day.toString(), sales.size, sales.sumOf { it.totalCents }))
    }

    // ---- expenses ---------------------------------------------------------

    override suspend fun listExpenses(): Response<ExpensesResponse> =
        ok(ExpensesResponse(store.listExpenses().map { it.toDto() }))

    override suspend fun createExpense(body: ExpenseCreateRequest): Response<ExpenseDto> = mutex.withLock {
        val e = StoredExpense(newId(), body.description, body.qty, body.costCents, clock())
        val problem = when {
            e.description.isEmpty() -> "expense description must not be empty"
            !(e.qty > 0) -> "expense quantity must be greater than zero"
            e.costCents < 0 -> "expense cost must not be negative"
            else -> null
        }
        problem?.let { return fail(422, "validation_error", it) }
        store.addExpense(e)
        ok(e.toDto(), 201)
    }

    // ---- suppliers --------------------------------------------------------

    override suspend fun listSuppliers(): Response<SuppliersResponse> =
        ok(SuppliersResponse(store.listSuppliers().sortedBy { it.name }.map { it.toDto() }))

    override suspend fun createSupplier(body: SupplierCreateRequest): Response<SupplierDto> = mutex.withLock {
        val s = StoredSupplier(newId(), body.name.trim(), body.phone, body.note)
        validateSupplier(s)?.let { return fail(422, "validation_error", it) }
        store.saveSupplier(s)
        ok(s.toDto(), 201)
    }

    override suspend fun patchSupplier(id: String, body: SupplierPatchRequest): Response<SupplierDto> =
        mutex.withLock {
            val current = store.getSupplier(id) ?: return fail(404, "not_found", "unknown supplier")
            val s = current.copy(
                name = body.name?.trim() ?: current.name,
                phone = body.phone ?: current.phone,
                note = body.note ?: current.note,
            )
            validateSupplier(s)?.let { return fail(422, "validation_error", it) }
            store.saveSupplier(s)
            ok(s.toDto())
        }

    override suspend fun deleteSupplier(id: String): Response<Unit> = mutex.withLock {
        if (!store.deleteSupplier(id)) return fail(404, "not_found", "unknown supplier")
        ok(Unit, 204)
    }

    // ---- branding ---------------------------------------------------------

    override suspend fun getBranding(): Response<BrandingResponse> =
        ok((store.getBranding() ?: DEFAULT_BRANDING).toDto())

    override suspend fun patchBranding(body: JsonObject): Response<BrandingResponse> = mutex.withLock {
        var merged = store.getBranding() ?: DEFAULT_BRANDING
        // Text fields mirror Go *string: absent or null keeps, a non-string is a bad body.
        fun text(key: String): String? {
            val v = body[key] ?: return null
            if (v is JsonNull) return null
            if (v is JsonPrimitive && v.isString) return v.content
            throw BadBody
        }
        try {
            merged = merged.copy(
                shopName = text("shop_name") ?: merged.shopName,
                primary = text("primary") ?: merged.primary,
                accent = text("accent") ?: merged.accent,
                background = text("background") ?: merged.background,
            )
        } catch (_: BadBody) {
            return fail(400, "bad_request", "invalid JSON body")
        }
        body["logo_data_url"]?.let { raw ->
            when {
                raw is JsonNull -> merged = merged.copy(logoDataUrl = null)
                raw is JsonPrimitive && raw.isString -> {
                    when (val parsed = parseLogo(raw.content)) {
                        is LogoResult.TooLarge -> return fail(413, "too_large", parsed.message)
                        is LogoResult.Invalid -> return fail(422, "validation_error", parsed.message)
                        is LogoResult.Ok -> merged = merged.copy(logoDataUrl = raw.content)
                    }
                }
                else -> return fail(422, "validation_error", "logo_data_url must be a data URL string or null")
            }
        }
        validateBranding(merged)?.let { (status, code, message) -> return fail(status, code, message) }
        val saved = merged.copy(updatedAt = clock())
        store.saveBranding(saved)
        ok(saved.toDto())
    }

    // ---- validation (ports of internal/domain) -----------------------------

    private fun validateProduct(p: StoredProduct): String? = when {
        p.name.isEmpty() -> "product name must not be empty"
        p.priceCents < 0 -> "price must not be negative"
        else -> null
    }

    private fun validateTableLabel(label: String): String? = when {
        label.isEmpty() -> "table label must not be empty"
        label.codePointCount(0, label.length) > MAX_TABLE_LABEL -> "table label must be at most 40 characters"
        else -> null
    }

    private fun validateSupplier(s: StoredSupplier): String? = when {
        s.name.trim().isEmpty() -> "supplier name must not be empty"
        s.name.trim().let { it.codePointCount(0, it.length) } > 80 -> "supplier name must be at most 80 characters"
        s.phone.codePointCount(0, s.phone.length) > 40 -> "supplier phone must be at most 40 characters"
        s.note.codePointCount(0, s.note.length) > 200 -> "supplier note must be at most 200 characters"
        else -> null
    }

    // Returns (status, code, message) or null when valid. The logo was already
    // checked when it was parsed, so only name and colors can fail here.
    private fun validateBranding(b: StoredBranding): Triple<Int, String, String>? {
        val name = b.shopName.trim()
        if (name.codePointCount(0, name.length) !in 1..60) {
            return Triple(422, "validation_error", "shop name must be 1..60 characters")
        }
        listOf("primary" to b.primary, "accent" to b.accent, "background" to b.background).forEach { (_, hex) ->
            if (!HEX_COLOR.matches(hex)) {
                return Triple(422, "validation_error", "branding color must match #rrggbb")
            }
        }
        return null
    }

    private sealed interface LogoResult {
        data object Ok : LogoResult
        data class TooLarge(val message: String) : LogoResult
        data class Invalid(val message: String) : LogoResult
    }

    // Ports parseLogoDataURL + domain.Branding logo validation.
    private fun parseLogo(dataUrl: String): LogoResult {
        if (!dataUrl.startsWith("data:")) return LogoResult.Invalid("logo_data_url must start with data:")
        val rest = dataUrl.substring(5)
        val idx = rest.indexOf(";base64,")
        if (idx < 0) return LogoResult.Invalid("logo_data_url must be base64")
        val mime = rest.substring(0, idx)
        if (mime != MIME_PNG && mime != MIME_JPEG && mime != MIME_SVG) {
            return LogoResult.Invalid("unsupported logo mime \"$mime\"")
        }
        val payload = rest.substring(idx + ";base64,".length)
        if (payload.length > MAX_LOGO_BYTES * 4 / 3 + 100) return LogoResult.TooLarge("logo exceeds 512KiB")
        val raw = try {
            Base64.getDecoder().decode(payload)
        } catch (_: IllegalArgumentException) {
            return LogoResult.Invalid("logo is not valid base64")
        }
        if (raw.size > MAX_LOGO_BYTES) return LogoResult.TooLarge("logo exceeds 512KiB")
        if (raw.isEmpty()) return LogoResult.Invalid("logo is empty")
        val kind = logoKind(raw) ?: return LogoResult.Invalid("logo must be PNG, JPEG, or SVG")
        if (kind == MIME_SVG && String(raw, Charsets.ISO_8859_1).lowercase().contains("<script")) {
            return LogoResult.Invalid("svg logo must not contain scripts")
        }
        if (kind != mime) return LogoResult.Invalid("logo mime does not match image kind")
        return LogoResult.Ok
    }

    private fun logoKind(logo: ByteArray): String? {
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        if (logo.size >= 8 && logo.copyOfRange(0, 8).contentEquals(png)) return MIME_PNG
        if (logo.size >= 3 && logo[0] == 0xFF.toByte() && logo[1] == 0xD8.toByte() && logo[2] == 0xFF.toByte()) {
            return MIME_JPEG
        }
        return if (isSvg(String(logo, Charsets.ISO_8859_1))) MIME_SVG else null
    }

    private fun isSvg(s: String): Boolean {
        fun skip(from: Int): Int {
            var i = from
            while (i < s.length && s[i] in " \t\n\r\u000c\u000b") i++
            return i
        }
        var i = skip(0)
        if (s.startsWith("<?xml", i)) {
            val end = s.indexOf("?>", i)
            if (end < 0) return false
            i = skip(end + 2)
        }
        if (s.length - i < 5 || !s.regionMatches(i, "<svg", 0, 4, ignoreCase = true)) return false
        return s[i + 4] in " \t\n\r>/"
    }

    // ---- mapping and response helpers --------------------------------------

    private fun StoredProduct.toDto() = ProductDto(id, name, priceCents, available)

    private fun StoredSupplier.toDto() = SupplierDto(id, name, phone, note)

    private fun StoredExpense.toDto() = ExpenseDto(id, description, qty, costCents, iso(date))

    private fun StoredBranding.toDto() = BrandingResponse(
        shopName = shopName,
        primary = primary,
        accent = accent,
        background = background,
        hasLogo = logoDataUrl != null,
        updatedAt = if (updatedAt == 0L) "" else iso(updatedAt),
    )

    // The hub reads lines back ordered by product id; addItem answers unsorted.
    private fun List<StoredLine>.toDtos(sorted: Boolean = true): List<OrderLineDto> =
        (if (sorted) sortedBy { it.productId } else this).map {
            OrderLineDto(it.productId, it.name, it.unitPriceCents, it.qty, it.unitPriceCents * it.qty)
        }

    private fun StoredTab.toDto(sorted: Boolean = true) = TabDto(
        id = id,
        tableId = tableId,
        status = if (open) "open" else "closed",
        openedAt = iso(openedAt),
        items = lines.toDtos(sorted),
        totalCents = lines.sumOf { it.unitPriceCents * it.qty },
    )

    private fun StoredSale.toDto() = SaleDto(id, tableId, lines.toDtos(), totalCents, iso(closedAt))

    // Go RFC3339Nano: UTC, fractional seconds without trailing zeros.
    private fun iso(epochMillis: Long): String {
        val s = Instant.ofEpochMilli(epochMillis).toString()
        val dot = s.indexOf('.')
        return if (dot < 0) s else s.substring(0, dot) + s.substring(dot, s.length - 1).trimEnd('0') + "Z"
    }

    private object BadBody : RuntimeException() {
        private fun readResolve(): Any = BadBody
    }

    private fun raw(code: Int): okhttp3.Response = okhttp3.Response.Builder()
        .request(Request.Builder().url("http://localhost/").build())
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message(if (code < 400) "OK" else "Error")
        .build()

    private fun <T> ok(body: T, code: Int = 200): Response<T> = Response.success(body, raw(code))

    private fun <T> fail(status: Int, code: String, message: String): Response<T> {
        val json = Json.encodeToString(ErrorEnvelope.serializer(), ErrorEnvelope(ErrorBody(code, message)))
        return Response.error(json.toResponseBody("application/json".toMediaType()), raw(status))
    }

    private companion object {
        const val SALES_DEFAULT_LIMIT = 50
        const val SALES_MAX_LIMIT = 500
        const val MAX_TABLE_LABEL = 40
        const val MAX_LOGO_BYTES = 512 * 1024
        const val MIME_PNG = "image/png"
        const val MIME_JPEG = "image/jpeg"
        const val MIME_SVG = "image/svg+xml"
        val HEX_COLOR = Regex("#[0-9a-fA-F]{6}")

        // Hub defaults from domain.DefaultBranding; updatedAt 0 serves "".
        val DEFAULT_BRANDING = StoredBranding(
            shopName = "mitt",
            primary = "#1C1F24",
            accent = "#E8A33D",
            background = "#121417",
            logoDataUrl = null,
            updatedAt = 0L,
        )
    }
}
