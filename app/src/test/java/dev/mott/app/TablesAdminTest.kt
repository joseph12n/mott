package dev.mott.app

import dev.mott.app.data.ApiException
import dev.mott.app.data.SalesRepo
import dev.mott.app.data.remote.ApiClient
import dev.mott.app.data.remote.MittApi
import dev.mott.app.data.remote.TableCreateRequest
import dev.mott.app.ui.addItemInlineCopy
import dev.mott.app.ui.classifyLoadFailure
import dev.mott.app.ui.closeInlineCopy
import dev.mott.app.ui.freeTables
import dev.mott.app.ui.LoadFailureReason
import dev.mott.app.ui.openTabInlineCopy
import dev.mott.app.ui.openTables
import dev.mott.app.ui.order.TableRef
import dev.mott.app.ui.tableCreateInlineCopy
import dev.mott.app.ui.tableDeleteInlineCopy
import dev.mott.app.ui.validateAdminPick
import dev.mott.app.ui.validateTableLabel
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.util.concurrent.TimeUnit

// T5 Mesas parity: table admin plus direct tab mutations over the mitt
// tables/tabs wire (MockWebServer + memory only for HTTP, pure asserts
// for the copy/validation helpers). No Compose UI tests.
class TablesAdminTest {
    private var server: MockWebServer? = null

    @After
    fun tearDown() {
        runCatching { server?.shutdown() }
        server = null
    }

    private fun json(code: Int, body: String) {
        server!!.enqueue(
            MockResponse().setResponseCode(code).setBody(body).addHeader("Content-Type", "application/json"),
        )
    }

    private fun empty(code: Int) {
        server!!.enqueue(MockResponse().setResponseCode(code))
    }

    private fun start(): MittApi {
        val s = MockWebServer()
        s.start()
        server = s
        return ApiClient.build(s.url("/").toString(), "pair-token", logger = false)
    }

    private fun repo(api: MittApi, online: Boolean = true) =
        SalesRepo(apiProvider = { api }, isOnline = { online })

    private fun takeRequest() = server!!.takeRequest(5, TimeUnit.SECONDS)!!

    // -- pure validation -------------------------------------------------

    @Test
    fun `label blank is rejected with honest copy`() {
        assertEquals("Escriba el nombre de la mesa.", validateTableLabel(""))
        assertEquals("Escriba el nombre de la mesa.", validateTableLabel("   "))
    }

    @Test
    fun `label longer than 40 chars is rejected`() {
        assertEquals(
            "El nombre debe tener entre 1 y 40 caracteres.",
            validateTableLabel("M".repeat(41)),
        )
    }

    @Test
    fun `label within 1 to 40 chars is accepted trimmed`() {
        assertNull(validateTableLabel("MESA 1"))
        assertNull(validateTableLabel("M".repeat(40)))
        assertNull(validateTableLabel("  MESA 2  "))
    }

    @Test
    fun `admin pick needs a product and a positive qty`() {
        assertEquals("Elija un producto y una cantidad válida.", validateAdminPick("", 1))
        assertEquals("Elija un producto y una cantidad válida.", validateAdminPick("p1", 0))
        assertEquals("Elija un producto y una cantidad válida.", validateAdminPick("p1", -2))
        assertNull(validateAdminPick("p1", 2))
    }

    @Test
    fun `free and open tables partition the list`() {
        val tables = listOf(
            TableRef("t1", "MESA 1", occupied = true),
            TableRef("t2", "MESA 2", occupied = false),
        )
        assertEquals(listOf("t2"), freeTables(tables).map { it.id })
        assertEquals(listOf("t1"), openTables(tables).map { it.id })
    }

    // -- inline named-error copy ------------------------------------------

    @Test
    fun `delete 409 names the occupied table`() {
        assertEquals("La mesa está ocupada", tableDeleteInlineCopy(409))
    }

    @Test
    fun `delete 404 names the missing table`() {
        assertEquals("La mesa ya no existe.", tableDeleteInlineCopy(404))
    }

    @Test
    fun `delete transport failures fall back to the loader contract`() {
        assertNull(tableDeleteInlineCopy(500))
        assertNull(tableDeleteInlineCopy(401))
    }

    @Test
    fun `create 422 names the label rule`() {
        assertEquals(
            "El nombre debe tener entre 1 y 40 caracteres.",
            tableCreateInlineCopy(422),
        )
        assertNull(tableCreateInlineCopy(500))
    }

    @Test
    fun `open 422 names the unknown table`() {
        assertEquals(
            "Mesa desconocida. Actualizá las mesas e intentá de nuevo.",
            openTabInlineCopy(422),
        )
        assertNull(openTabInlineCopy(500))
    }

    @Test
    fun `add item 422 names the pick rule`() {
        assertEquals(
            "Elija un producto y una cantidad válida.",
            addItemInlineCopy(422),
        )
        assertNull(addItemInlineCopy(500))
    }

    @Test
    fun `close 404 names the missing tab`() {
        assertEquals("La cuenta ya no existe.", closeInlineCopy(404))
        assertNull(closeInlineCopy(500))
    }

    // -- create ------------------------------------------------------------

    @Test
    fun `create posts the label and maps the table dto`() = runBlocking {
        val api = start()
        json(201, """{"id":"t7","label":"MESA 7","occupied":false}""")

        val created = repo(api).createTable("MESA 7")

        assertEquals(TableRef("t7", "MESA 7", occupied = false), created)
        val request = takeRequest()
        assertEquals("/api/tables", request.path)
        assertEquals("POST", request.method)
        assertEquals("""{"label":"MESA 7"}""", request.body.readUtf8())
    }

    @Test
    fun `create serializes through the shared converter`() {
        val parsed = ApiClient.json.decodeFromString(
            TableCreateRequest.serializer(),
            """{"label":"MESA 7"}""",
        )
        assertEquals("MESA 7", parsed.label)
    }

    @Test
    fun `create 422 throws typed error for the inline copy`() = runBlocking {
        val api = start()
        json(422, """{"error":{"code":"validation_error","message":"label must be 1..40"}}""")

        try {
            repo(api).createTable("M".repeat(41))
            fail("expected ApiException(422)")
        } catch (e: ApiException) {
            assertEquals(422, e.status)
            assertEquals(
                "El nombre debe tener entre 1 y 40 caracteres.",
                tableCreateInlineCopy(e.status),
            )
        }
    }

    // -- delete ------------------------------------------------------------

    @Test
    fun `delete sends DELETE and succeeds on 204`() = runBlocking {
        val api = start()
        empty(204)

        repo(api).deleteTable("t7")

        val request = takeRequest()
        assertEquals("/api/tables/t7", request.path)
        assertEquals("DELETE", request.method)
    }

    @Test
    fun `delete 409 throws typed error and the table is kept`() = runBlocking {
        val api = start()
        val failing = repo(api)
        json(409, """{"error":{"code":"table_occupied","message":"table has an open tab"}}""")
        // The list still carries the table: the UI only drops it on success.
        json(200, """{"tables":[{"id":"t1","label":"MESA 1","occupied":true}]}""")

        try {
            failing.deleteTable("t1")
            fail("expected ApiException(409)")
        } catch (e: ApiException) {
            assertEquals(409, e.status)
            assertEquals("La mesa está ocupada", tableDeleteInlineCopy(e.status))
        }
        assertEquals(listOf("t1"), failing.listTables().map { it.id })
    }

    @Test
    fun `delete 404 throws typed error with missing copy`() = runBlocking {
        val api = start()
        json(404, """{"error":{"code":"not_found","message":"unknown table"}}""")

        try {
            repo(api).deleteTable("ghost")
            fail("expected ApiException(404)")
        } catch (e: ApiException) {
            assertEquals(404, e.status)
            assertEquals("La mesa ya no existe.", tableDeleteInlineCopy(e.status))
        }
    }

    // -- list --------------------------------------------------------------

    @Test
    fun `list maps tables with occupancy flags`() = runBlocking {
        val api = start()
        json(200, """{"tables":[{"id":"t1","label":"MESA 1","occupied":true},{"id":"t2","label":"MESA 2","occupied":false}]}""")

        val tables = repo(api).listTables()

        assertEquals(
            listOf(
                TableRef("t1", "MESA 1", occupied = true),
                TableRef("t2", "MESA 2", occupied = false),
            ),
            tables,
        )
        assertEquals("/api/tables", takeRequest().path)
    }

    // -- open tab (idempotent) -----------------------------------------------

    @Test
    fun `open posts table id and maps the existing tab on 200`() = runBlocking {
        val api = start()
        json(200, SalesTest.MITT_WIRE_OPEN_TABS_TAB_JSON)

        val tab = repo(api).openTab("t9")

        assertEquals("tab9", tab.id)
        assertEquals("t9", tab.tableId)
        assertEquals(listOf("p1"), tab.lines.map { it.productId })
        assertEquals(2500L, tab.totalCents())
        val request = takeRequest()
        assertEquals("/api/tabs", request.path)
        assertEquals("POST", request.method)
        assertEquals("""{"table_id":"t9"}""", request.body.readUtf8())
    }

    @Test
    fun `open 422 throws typed error for the inline copy`() = runBlocking {
        val api = start()
        json(422, """{"error":{"code":"unknown_table","message":"unknown table"}}""")

        try {
            repo(api).openTab("ghost")
            fail("expected ApiException(422)")
        } catch (e: ApiException) {
            assertEquals(422, e.status)
            assertEquals(
                "Mesa desconocida. Actualizá las mesas e intentá de nuevo.",
                openTabInlineCopy(e.status),
            )
        }
    }

    // -- add item (hub merges same-product lines) ------------------------------

    @Test
    fun `add item posts product and qty and maps the merged tab`() = runBlocking {
        val api = start()
        json(
            200,
            """{"id":"tab9","table_id":"t9","status":"open","opened_at":"2026-10-07T20:00:00Z","items":[{"product_id":"p1","name":"Fernet","unit_price_cents":1250,"qty":3,"line_total_cents":3750}],"total_cents":3750}""",
        )

        val tab = repo(api).addItem("tab9", "p1", 2)

        assertEquals(3, tab.lines.single().qty)
        assertEquals(3750L, tab.totalCents())
        val request = takeRequest()
        assertEquals("/api/tabs/tab9/items", request.path)
        assertEquals("POST", request.method)
        assertEquals("""{"product_id":"p1","qty":2}""", request.body.readUtf8())
    }

    @Test
    fun `add item 422 throws typed error for the inline copy`() = runBlocking {
        val api = start()
        json(422, """{"error":{"code":"validation_error","message":"bad qty"}}""")

        try {
            repo(api).addItem("tab9", "p1", 0)
            fail("expected ApiException(422)")
        } catch (e: ApiException) {
            assertEquals(422, e.status)
            assertEquals(
                "Elija un producto y una cantidad válida.",
                addItemInlineCopy(e.status),
            )
        }
    }

    // -- close (no payment method per hub reduction) -----------------------------

    @Test
    fun `close posts without payment and maps the sale`() = runBlocking {
        val api = start()
        json(
            200,
            """{"id":"s9","table_id":"t9","items":[{"product_id":"p1","name":"Fernet","unit_price_cents":1250,"qty":2,"line_total_cents":2500}],"total_cents":2500,"closed_at":"2026-10-07T21:02:00Z"}""",
        )

        val sale = repo(api).closeTabNow("tab9")

        assertEquals("s9", sale.id)
        assertEquals("t9", sale.tableId)
        assertEquals(2500L, sale.totalCents)
        val request = takeRequest()
        assertEquals("/api/tabs/tab9/close", request.path)
        assertEquals("POST", request.method)
        assertTrue(!request.body.readUtf8().contains("payment"))
    }

    @Test
    fun `close 404 throws typed error with missing copy`() = runBlocking {
        val api = start()
        json(404, """{"error":{"code":"not_found","message":"unknown tab"}}""")

        try {
            repo(api).closeTabNow("ghost")
            fail("expected ApiException(404)")
        } catch (e: ApiException) {
            assertEquals(404, e.status)
            assertEquals("La cuenta ya no existe.", closeInlineCopy(e.status))
        }
    }

    // -- offline guard ---------------------------------------------------------

    @Test
    fun `admin mutations while offline throw io for sin_servidor`() = runBlocking {
        val api = start()
        val offline = repo(api, online = false)

        assertIo { offline.createTable("MESA 7") }
        assertIo { offline.deleteTable("t1") }
        assertIo { offline.listTables() }
        assertIo { offline.openTab("t1") }
        assertIo { offline.addItem("tab1", "p1", 1) }
        assertIo { offline.closeTabNow("tab1") }
        assertEquals(0, server!!.requestCount)
        assertEquals(LoadFailureReason.SIN_SERVIDOR, classifyLoadFailure(IOException("offline")))
    }

    @Test
    fun `admin auth failures classify as token_invalido`() = runBlocking {
        val api = start()
        val failing = repo(api)
        repeat(3) { json(401, """{"error":{"code":"unauthorized","message":"bad token"}}""") }

        assertApiException(401) { failing.createTable("MESA 7") }
        assertApiException(401) { failing.deleteTable("t1") }
        assertApiException(401) { failing.openTab("t1") }
    }

    private suspend fun assertApiException(status: Int, call: suspend () -> Unit) {
        try {
            call()
        } catch (e: ApiException) {
            assertEquals(status, e.status)
            return
        }
        fail("expected ApiException($status)")
    }

    private suspend fun assertIo(call: suspend () -> Unit) {
        try {
            call()
        } catch (e: IOException) {
            return
        }
        fail("expected IOException while offline")
    }
}
