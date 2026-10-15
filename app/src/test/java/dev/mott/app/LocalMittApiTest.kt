package dev.mott.app

import dev.mott.app.data.ApiException
import dev.mott.app.data.SalesRepo
import dev.mott.app.data.remote.AddItemRequest
import dev.mott.app.data.remote.ErrorEnvelope
import dev.mott.app.data.remote.ExpenseCreateRequest
import dev.mott.app.data.remote.OpenTabRequest
import dev.mott.app.data.remote.ProductCreateRequest
import dev.mott.app.data.remote.ProductPatchRequest
import dev.mott.app.data.remote.SupplierCreateRequest
import dev.mott.app.data.remote.SupplierPatchRequest
import dev.mott.app.data.remote.TableCreateRequest
import dev.mott.app.data.standalone.InMemoryLocalStore
import dev.mott.app.data.standalone.LocalMittApi
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.Response
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Base64

// T5 (standalone mode): LocalMittApi must behave like the mitt hub for every
// MittApi method, on the JVM, with no network and no Room.
class LocalMittApiTest {
    private val zone = ZoneId.of("America/Bogota")
    private var now = Instant.parse("2026-10-15T15:00:00Z").toEpochMilli()
    private var seq = 0
    private val store = InMemoryLocalStore()
    private val api = LocalMittApi(store, clock = { now }, newId = { "id-${++seq}" }, zone = zone)

    private fun <T> run(block: suspend () -> T): T = runBlocking { block() }

    private fun <T> Response<T>.ok(code: Int? = null): T {
        assertTrue("expected success but was ${code()} ${errorBody()?.string()}", isSuccessful)
        if (code != null) assertEquals(code, code())
        return body()!!
    }

    private fun Response<*>.fails(status: Int, errorCode: String? = null) {
        assertFalse("expected failure $status but succeeded", isSuccessful)
        assertEquals(status, code())
        if (errorCode != null) {
            val env = Json.decodeFromString<ErrorEnvelope>(errorBody()!!.string())
            assertEquals(errorCode, env.error.code)
            assertTrue(env.error.message.isNotBlank())
        }
    }

    private fun table(label: String) = run { api.createTable(TableCreateRequest(label)) }.ok(201)

    private fun product(name: String, price: Long, available: Boolean? = null) =
        run { api.createProduct(ProductCreateRequest(name, price, available)) }.ok(201)

    // ---- health ----------------------------------------------------------

    @Test
    fun health_is_ok() {
        assertEquals("ok", run { api.health() }.ok(200).status)
    }

    // ---- tables ----------------------------------------------------------

    @Test
    fun createTable_trims_label_and_lists_ordered_by_label() {
        val b = table("  B2 ")
        assertEquals("B2", b.label)
        assertFalse(b.occupied)
        table("A1")
        val labels = run { api.listTables() }.ok(200).tables.map { it.label }
        assertEquals(listOf("A1", "B2"), labels)
    }

    @Test
    fun createTable_rejects_blank_and_over_40_with_422() {
        run { api.createTable(TableCreateRequest("   ")) }.fails(422, "validation_error")
        run { api.createTable(TableCreateRequest("x".repeat(41))) }.fails(422, "validation_error")
        assertEquals("x".repeat(40), table("x".repeat(40)).label)
        assertEquals(1, run { api.listTables() }.ok().tables.size)
    }

    @Test
    fun createTable_allows_duplicate_labels_like_the_hub() {
        table("Barra")
        table("Barra")
        assertEquals(2, run { api.listTables() }.ok().tables.size)
    }

    @Test
    fun deleteTable_success_404_and_409_when_occupied() {
        val t = table("M1")
        val occupied = table("M2")
        run { api.openTab(OpenTabRequest(occupied.id)) }.ok(201)
        run { api.deleteTable(occupied.id) }.fails(409, "table_occupied")
        run { api.deleteTable("nope") }.fails(404, "not_found")
        run { api.deleteTable(t.id) }.ok(204)
        assertEquals(listOf(occupied.id), run { api.listTables() }.ok().tables.map { it.id })
    }

    // ---- tabs ------------------------------------------------------------

    @Test
    fun openTab_unknown_table_is_422_unknown_table_and_blank_is_validation() {
        run { api.openTab(OpenTabRequest("ghost")) }.fails(422, "unknown_table")
        run { api.openTab(OpenTabRequest("  ")) }.fails(422, "validation_error")
        run { api.openTab(OpenTabRequest("x".repeat(41))) }.fails(422, "validation_error")
    }

    @Test
    fun openTab_is_idempotent_201_then_200_same_tab_and_marks_table_occupied() {
        val t = table("M1")
        val first = run { api.openTab(OpenTabRequest(t.id)) }.ok(201)
        assertEquals("open", first.status)
        assertEquals(t.id, first.tableId)
        assertTrue(first.items.isEmpty())
        assertEquals(0L, first.totalCents)
        assertEquals("2026-10-15T15:00:00Z", first.openedAt)
        val second = run { api.openTab(OpenTabRequest(t.id)) }.ok(200)
        assertEquals(first.id, second.id)
        assertEquals(1, run { api.openTabs() }.ok().tabs.size)
        assertTrue(run { api.listTables() }.ok().tables.single().occupied)
    }

    @Test
    fun openTabs_are_oldest_first() {
        val a = table("A")
        val b = table("B")
        val tb = run { api.openTab(OpenTabRequest(b.id)) }.ok()
        now += 1_000
        val ta = run { api.openTab(OpenTabRequest(a.id)) }.ok()
        assertEquals(listOf(tb.id, ta.id), run { api.openTabs() }.ok().tabs.map { it.id })
    }

    @Test
    fun addItem_merges_same_product_and_keeps_first_price_snapshot_after_price_change() {
        val t = table("M1")
        val beer = product("Cerveza", 5_000)
        val tab = run { api.openTab(OpenTabRequest(t.id)) }.ok()
        val one = run { api.addItem(tab.id, AddItemRequest(beer.id, 2)) }.ok(200)
        assertEquals(10_000L, one.totalCents)
        // The hub supports price edits through PATCH: the open line must keep its snapshot.
        run { api.patchProduct(beer.id, ProductPatchRequest(priceCents = 9_000)) }.ok(200)
        val two = run { api.addItem(tab.id, AddItemRequest(beer.id, 1)) }.ok(200)
        val line = two.items.single()
        assertEquals(3, line.qty)
        assertEquals(5_000L, line.unitPriceCents)
        assertEquals(15_000L, line.lineTotalCents)
        assertEquals(15_000L, two.totalCents)
        assertEquals("Cerveza", line.name)
    }

    @Test
    fun addItem_new_tab_after_price_change_uses_new_price() {
        val beer = product("Cerveza", 5_000)
        run { api.patchProduct(beer.id, ProductPatchRequest(priceCents = 9_000)) }.ok()
        val tab = run { api.openTab(OpenTabRequest(table("M1").id)) }.ok()
        val got = run { api.addItem(tab.id, AddItemRequest(beer.id, 1)) }.ok()
        assertEquals(9_000L, got.items.single().unitPriceCents)
    }

    @Test
    fun addItem_rejections_match_the_hub() {
        val tab = run { api.openTab(OpenTabRequest(table("M1").id)) }.ok()
        val ok = product("Agua", 1_000)
        val off = product("Cafe", 2_000, available = false)
        run { api.addItem(tab.id, AddItemRequest("ghost", 1)) }.fails(404, "not_found")
        run { api.addItem("ghost-tab", AddItemRequest(ok.id, 1)) }.fails(404, "not_found")
        run { api.addItem(tab.id, AddItemRequest(off.id, 1)) }.fails(422, "validation_error")
        run { api.addItem(tab.id, AddItemRequest(ok.id, 0)) }.fails(422, "validation_error")
        run { api.addItem(tab.id, AddItemRequest(ok.id, -2)) }.fails(422, "validation_error")
        assertTrue(run { api.openTabs() }.ok().tabs.single().items.isEmpty())
    }

    @Test
    fun open_tab_lines_come_back_ordered_by_product_id_like_the_hub_store() {
        val tab = run { api.openTab(OpenTabRequest(table("M1").id)) }.ok()
        val p1 = product("Zumo", 100) // id-3
        val p2 = product("Agua", 100) // id-4
        run { api.addItem(tab.id, AddItemRequest(p2.id, 1)) }.ok()
        run { api.addItem(tab.id, AddItemRequest(p1.id, 1)) }.ok()
        val ids = run { api.openTabs() }.ok().tabs.single().items.map { it.productId }
        assertEquals(ids.sorted(), ids)
    }

    @Test
    fun closeTab_makes_sale_frees_table_and_second_close_is_404() {
        val t = table("M1")
        val beer = product("Cerveza", 5_000)
        val tab = run { api.openTab(OpenTabRequest(t.id)) }.ok()
        run { api.addItem(tab.id, AddItemRequest(beer.id, 3)) }.ok()
        now += 5_000
        val sale = run { api.closeTab(tab.id) }.ok(200)
        assertEquals(tab.id, sale.id)
        assertEquals(t.id, sale.tableId)
        assertEquals(15_000L, sale.totalCents)
        assertEquals("2026-10-15T15:00:05Z", sale.closedAt)
        assertEquals(1, sale.items.size)
        assertTrue(run { api.openTabs() }.ok().tabs.isEmpty())
        assertFalse(run { api.listTables() }.ok().tables.single().occupied)
        run { api.closeTab(tab.id) }.fails(404, "not_found")
        run { api.addItem(tab.id, AddItemRequest(beer.id, 1)) }.fails(404, "not_found")
        // The free table can open a brand-new tab.
        val again = run { api.openTab(OpenTabRequest(t.id)) }.ok(201)
        assertTrue(again.id != tab.id)
    }

    @Test
    fun closeTab_unknown_is_404_and_empty_tab_is_422() {
        run { api.closeTab("ghost") }.fails(404, "not_found")
        val tab = run { api.openTab(OpenTabRequest(table("M1").id)) }.ok()
        run { api.closeTab(tab.id) }.fails(422, "validation_error")
        assertEquals(1, run { api.openTabs() }.ok().tabs.size)
    }

    // ---- sales -----------------------------------------------------------

    private fun sell(label: String, price: Long, qty: Int = 1): String {
        val p = product("P-$label", price)
        val tab = run { api.openTab(OpenTabRequest(table(label).id)) }.ok()
        run { api.addItem(tab.id, AddItemRequest(p.id, qty)) }.ok()
        run { api.closeTab(tab.id) }.ok()
        return tab.id
    }

    @Test
    fun listSales_newest_first_honors_limit_and_validates_it() {
        val a = sell("A", 100)
        now += 1_000
        val b = sell("B", 200)
        now += 1_000
        val c = sell("C", 300)
        assertEquals(listOf(c, b, a), run { api.listSales() }.ok().sales.map { it.id })
        assertEquals(listOf(c, b), run { api.listSales(2) }.ok().sales.map { it.id })
        run { api.listSales(0) }.fails(400, "bad_request")
        assertEquals(3, run { api.listSales(100_000) }.ok().sales.size)
    }

    @Test
    fun listSales_default_limit_is_50() {
        repeat(52) { i -> sell("T$i", 10); now += 1 }
        assertEquals(50, run { api.listSales() }.ok().sales.size)
    }

    @Test
    fun salesToday_counts_only_the_local_calendar_day_of_the_clock() {
        // 2026-10-15T15:00Z = 10:00 in Bogota (UTC-5).
        sell("A", 1_000, qty = 2)
        sell("B", 500)
        // 2026-10-16T02:00Z is still 2026-10-15 21:00 in Bogota, so it counts as today.
        now = Instant.parse("2026-10-16T02:00:00Z").toEpochMilli()
        sell("C", 100)
        val today = run { api.salesToday() }.ok(200)
        assertEquals("2026-10-15", today.date)
        assertEquals(3, today.count)
        assertEquals(2_600L, today.totalCents)
        // Next local day starts empty; old sales remain listed.
        now = Instant.parse("2026-10-16T06:00:00Z").toEpochMilli() // 01:00 on the 16th in Bogota
        val next = run { api.salesToday() }.ok()
        assertEquals("2026-10-16", next.date)
        assertEquals(0, next.count)
        assertEquals(0L, next.totalCents)
        assertEquals(3, run { api.listSales() }.ok().sales.size)
        assertEquals(LocalDate.of(2026, 10, 16).toString(), next.date)
    }

    // ---- products --------------------------------------------------------

    @Test
    fun createProduct_defaults_available_true_validates_and_lists_by_name() {
        val z = product("Zumo", 300)
        assertTrue(z.available)
        product("Agua", 100, available = false)
        run { api.createProduct(ProductCreateRequest("", 100)) }.fails(422, "validation_error")
        run { api.createProduct(ProductCreateRequest("Mal", -1)) }.fails(422, "validation_error")
        val all = run { api.listProducts() }.ok(200).products
        assertEquals(listOf("Agua", "Zumo"), all.map { it.name })
        assertEquals(listOf("Zumo"), run { api.listProducts(true) }.ok().products.map { it.name })
        assertEquals(listOf("Agua"), run { api.listProducts(false) }.ok().products.map { it.name })
    }

    @Test
    fun patchProduct_merges_fields_404s_and_validates() {
        val p = product("Agua", 100)
        val patched = run { api.patchProduct(p.id, ProductPatchRequest(available = false)) }.ok(200)
        assertFalse(patched.available)
        assertEquals("Agua", patched.name)
        assertEquals(100L, patched.priceCents)
        val renamed = run { api.patchProduct(p.id, ProductPatchRequest(name = "Agua 1L", priceCents = 150)) }.ok()
        assertEquals("Agua 1L", renamed.name)
        assertEquals(150L, renamed.priceCents)
        assertFalse(renamed.available)
        run { api.patchProduct(p.id, ProductPatchRequest(name = "")) }.fails(422, "validation_error")
        run { api.patchProduct(p.id, ProductPatchRequest(priceCents = -5)) }.fails(422, "validation_error")
        run { api.patchProduct("ghost", ProductPatchRequest(available = true)) }.fails(404, "not_found")
        assertEquals("Agua 1L", run { api.listProducts() }.ok().products.single().name)
    }

    // ---- expenses --------------------------------------------------------

    @Test
    fun expenses_create_validates_and_lists_oldest_first() {
        val a = run { api.createExpense(ExpenseCreateRequest("Hielo", 2.5, 4_000)) }.ok(201)
        assertEquals("Hielo", a.description)
        assertEquals(2.5, a.qty, 0.0)
        assertEquals("2026-10-15T15:00:00Z", a.date)
        now += 1_000
        val b = run { api.createExpense(ExpenseCreateRequest("Limones", 1.0, 0)) }.ok(201)
        run { api.createExpense(ExpenseCreateRequest("", 1.0, 10)) }.fails(422, "validation_error")
        run { api.createExpense(ExpenseCreateRequest("x", 0.0, 10)) }.fails(422, "validation_error")
        run { api.createExpense(ExpenseCreateRequest("x", 1.0, -1)) }.fails(422, "validation_error")
        assertEquals(listOf(a.id, b.id), run { api.listExpenses() }.ok(200).expenses.map { it.id })
    }

    // ---- suppliers -------------------------------------------------------

    @Test
    fun suppliers_crud_ordering_limits_and_404() {
        val z = run { api.createSupplier(SupplierCreateRequest("  Zeta ", "123", "n")) }.ok(201)
        assertEquals("Zeta", z.name)
        val a = run { api.createSupplier(SupplierCreateRequest("Alfa")) }.ok(201)
        assertEquals("", a.phone)
        assertEquals(listOf("Alfa", "Zeta"), run { api.listSuppliers() }.ok(200).suppliers.map { it.name })

        run { api.createSupplier(SupplierCreateRequest("   ")) }.fails(422, "validation_error")
        run { api.createSupplier(SupplierCreateRequest("x".repeat(81))) }.fails(422, "validation_error")
        run { api.createSupplier(SupplierCreateRequest("ok", "1".repeat(41))) }.fails(422, "validation_error")
        run { api.createSupplier(SupplierCreateRequest("ok", "", "n".repeat(201))) }.fails(422, "validation_error")
        assertEquals("x".repeat(80), run { api.createSupplier(SupplierCreateRequest("x".repeat(80))) }.ok().name)

        val patched = run { api.patchSupplier(z.id, SupplierPatchRequest(name = " Beta ", note = "nuevo")) }.ok(200)
        assertEquals("Beta", patched.name)
        assertEquals("123", patched.phone)
        assertEquals("nuevo", patched.note)
        run { api.patchSupplier(z.id, SupplierPatchRequest(name = " ")) }.fails(422, "validation_error")
        run { api.patchSupplier("ghost", SupplierPatchRequest(name = "x")) }.fails(404, "not_found")

        run { api.deleteSupplier(a.id) }.ok(204)
        run { api.deleteSupplier(a.id) }.fails(404, "not_found")
        assertEquals(listOf("Beta"), run { api.listSuppliers() }.ok().suppliers.map { it.name }.filter { it == "Beta" })
    }

    // ---- branding --------------------------------------------------------

    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3)

    private fun dataUrl(mime: String, bytes: ByteArray) =
        "data:$mime;base64," + Base64.getEncoder().encodeToString(bytes)

    private fun patch(vararg pairs: Pair<String, kotlinx.serialization.json.JsonElement>): JsonObject =
        buildJsonObject { pairs.forEach { (k, v) -> put(k, v) } }

    @Test
    fun branding_defaults_equal_the_hub_defaults() {
        val b = run { api.getBranding() }.ok(200)
        assertEquals("mitt", b.shopName)
        assertEquals("#1C1F24", b.primary)
        assertEquals("#E8A33D", b.accent)
        assertEquals("#121417", b.background)
        assertFalse(b.hasLogo)
        assertEquals("", b.updatedAt)
    }

    @Test
    fun branding_patch_merges_absent_keys_and_stamps_updatedAt() {
        val got = run { api.patchBranding(patch("shop_name" to JsonPrimitive("Bar Luna"))) }.ok(200)
        assertEquals("Bar Luna", got.shopName)
        assertEquals("#E8A33D", got.accent)
        assertEquals("2026-10-15T15:00:00Z", got.updatedAt)
        val second = run { api.patchBranding(patch("accent" to JsonPrimitive("#abcdef"))) }.ok()
        assertEquals("Bar Luna", second.shopName)
        assertEquals("#abcdef", second.accent)
        assertEquals("Bar Luna", run { api.getBranding() }.ok().shopName)
        // An explicit null on a text field keeps the stored value, like the hub's pointer fields.
        val kept = run { api.patchBranding(patch("shop_name" to JsonNull)) }.ok()
        assertEquals("Bar Luna", kept.shopName)
    }

    @Test
    fun branding_logo_set_keep_and_clear() {
        val set = run { api.patchBranding(patch("logo_data_url" to JsonPrimitive(dataUrl("image/png", png)))) }.ok()
        assertTrue(set.hasLogo)
        val kept = run { api.patchBranding(patch("primary" to JsonPrimitive("#000000"))) }.ok()
        assertTrue("absent logo key keeps the logo", kept.hasLogo)
        val cleared = run { api.patchBranding(patch("logo_data_url" to JsonNull)) }.ok()
        assertFalse(cleared.hasLogo)
        assertEquals("#000000", cleared.primary)
    }

    @Test
    fun branding_validation_matches_the_hub() {
        fun p(vararg pairs: Pair<String, kotlinx.serialization.json.JsonElement>) = run { api.patchBranding(patch(*pairs)) }
        p("shop_name" to JsonPrimitive("   ")).fails(422, "validation_error")
        p("shop_name" to JsonPrimitive("x".repeat(61))).fails(422, "validation_error")
        p("primary" to JsonPrimitive("red")).fails(422, "validation_error")
        p("accent" to JsonPrimitive("#12345")).fails(422, "validation_error")
        p("background" to JsonPrimitive("#GGGGGG")).fails(422, "validation_error")
        p("logo_data_url" to JsonPrimitive("http://x/y.png")).fails(422, "validation_error")
        p("logo_data_url" to JsonPrimitive("data:image/png,abc")).fails(422, "validation_error")
        p("logo_data_url" to JsonPrimitive("data:image/gif;base64,AAAA")).fails(422, "validation_error")
        p("logo_data_url" to JsonPrimitive("data:image/png;base64,@@@@")).fails(422, "validation_error")
        p("logo_data_url" to JsonPrimitive("data:image/png;base64,")).fails(422, "validation_error")
        p("logo_data_url" to JsonPrimitive(7)).fails(422, "validation_error")
        // Bytes that are not a PNG under a png mime, and scripted SVG.
        p("logo_data_url" to JsonPrimitive(dataUrl("image/png", byteArrayOf(1, 2, 3)))).fails(422, "validation_error")
        p("logo_data_url" to JsonPrimitive(dataUrl("image/svg+xml", "<svg><script>x</script></svg>".toByteArray())))
            .fails(422, "validation_error")
        p("logo_data_url" to JsonPrimitive(dataUrl("image/jpeg", png))).fails(422, "validation_error")
        // Oversized logo is 413.
        val big = ByteArray(512 * 1024 + 1).also { System.arraycopy(png, 0, it, 0, png.size) }
        p("logo_data_url" to JsonPrimitive(dataUrl("image/png", big))).fails(413, "too_large")
        // A rejected patch changes nothing.
        val b = run { api.getBranding() }.ok()
        assertEquals("mitt", b.shopName)
        assertEquals("", b.updatedAt)
        // SVG with xml prolog is accepted.
        val svg = "<?xml version=\"1.0\"?>\n<svg xmlns=\"x\"/>".toByteArray()
        assertTrue(p("logo_data_url" to JsonPrimitive(dataUrl("image/svg+xml", svg))).ok().hasLogo)
    }

    @Test
    fun branding_wrong_type_for_text_field_is_400() {
        run { api.patchBranding(patch("shop_name" to JsonPrimitive(5))) }.fails(400, "bad_request")
    }

    // ---- existing repo on the local api ---------------------------------

    @Test
    fun existing_SalesRepo_works_unchanged_on_the_local_api() {
        val localApi = LocalMittApi(InMemoryLocalStore(), clock = { now }, zone = zone)
        val repo = SalesRepo(apiProvider = { localApi })
        runBlocking {
            val mesa = repo.createTable("Mesa 1")
            val beer = localApi.createProduct(ProductCreateRequest("Cerveza", 4_000)).body()!!
            val tab = repo.openTab(mesa.id)
            assertTrue(repo.listTables().single().occupied)
            repo.addItem(tab.id, beer.id, 2)
            val merged = repo.addItem(tab.id, beer.id, 1)
            assertEquals(12_000L, merged.lines.sumOf { it.unitPriceCents * it.qty })
            assertEquals(1, repo.openTabs().size)
            val sale = repo.closeTabNow(tab.id)
            assertEquals(12_000L, sale.totalCents)
            val today = repo.today()
            assertEquals(1, today.count)
            assertEquals(12_000L, today.totalCents)
            assertEquals(listOf(sale.id), repo.recent().map { it.id })
            assertTrue(repo.openTabs().isEmpty())
            repo.deleteTable(mesa.id)
            assertTrue(repo.listTables().isEmpty())
            val sup = repo.createSupplier("Distri", "555", "")
            assertEquals(listOf("Distri"), repo.listSuppliers().map { it.name })
            repo.deleteSupplier(sup.id)
            try {
                repo.deleteSupplier(sup.id)
                fail("expected ApiException")
            } catch (e: ApiException) {
                assertEquals(404, e.status)
            }
        }
    }
}
