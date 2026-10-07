package dev.mott.app

import dev.mott.app.data.ApiException
import dev.mott.app.data.SalesRepo
import dev.mott.app.data.remote.ApiClient
import dev.mott.app.data.remote.MittApi
import dev.mott.app.data.remote.TodayResponse
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.TimeUnit

// G1: Panel data over the mitt sales wire (MockWebServer + memory only).
class SalesTest {
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

    private fun start(): MittApi {
        val s = MockWebServer()
        s.start()
        server = s
        return ApiClient.build(s.url("/").toString(), "pair-token", logger = false)
    }

    private fun repo(api: MittApi) = SalesRepo(apiProvider = { api })

    @Test
    fun `today maps mitt wire exactly`() = runBlocking {
        val api = start()
        json(200, """{"date":"2026-10-07","count":3,"total_cents":4500}""")

        val today: dev.mott.app.data.TodayResult = repo(api).today()

        assertEquals("2026-10-07", today.date)
        assertEquals(3, today.count)
        assertEquals(4500L, today.totalCents)
        val request = server!!.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("/api/sales/today", request.path)
        assertEquals("Bearer pair-token", request.getHeader("Authorization"))
    }

    @Test
    fun `today deserializes through the shared converter`() {
        val parsed = ApiClient.json.decodeFromString<TodayResponse>(
            TodayResponse.serializer(),
            """{"date":"2026-10-07","count":3,"total_cents":4500}""",
        )
        assertEquals("2026-10-07", parsed.date)
        assertEquals(3, parsed.count)
        assertEquals(4500L, parsed.totalCents)
    }

    @Test
    fun `recent keeps hub order and sends the limit`() = runBlocking {
        val api = start()
        json(200, MITT_WIRE_SALES_JSON)

        val sales = repo(api).recent(20)

        // Hub order is newest first; the client preserves it, never re-sorts.
        assertEquals(listOf("s2", "s1"), sales.map { it.id })
        assertEquals(400L + 1250L, sales.single { it.id == "s2" }.totalCents)
        assertEquals("t2", sales.single { it.id == "s1" }.tableId)
        assertEquals(listOf("p1"), sales.single { it.id == "s1" }.items.map { it.productId })
        val request = server!!.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("/api/sales?limit=20", request.path)
    }

    @Test
    fun `open tabs map onto domain tab with lines`() = runBlocking {
        val api = start()
        json(200, MITT_WIRE_OPEN_TABS_JSON)

        val tabs = repo(api).openTabs()

        assertEquals(listOf("tab9"), tabs.map { it.id })
        assertEquals("t9", tabs.single().tableId)
        assertEquals(listOf("p1"), tabs.single().lines.map { it.productId })
        assertEquals(2500L, tabs.single().totalCents())
    }

    @Test
    fun `unauthorized throws typed error on every getter`() = runBlocking {
        val api = start()
        val failing = repo(api)
        repeat(3) { json(401, """{"error":{"code":"unauthorized","message":"bad token"}}""") }

        assertApiException(401) { failing.today() }
        assertApiException(401) { failing.recent() }
        assertApiException(401) { failing.openTabs() }
    }

    @Test
    fun `unreachable hub serves last good cache`() = runBlocking {
        val api = start()
        val cached = repo(api)
        json(200, """{"date":"2026-10-07","count":3,"total_cents":4500}""")
        json(200, MITT_WIRE_SALES_JSON)
        json(200, MITT_WIRE_OPEN_TABS_JSON)
        assertEquals(3, cached.today().count)
        assertEquals(2, cached.recent(20).size)
        assertEquals(1, cached.openTabs().size)

        server!!.shutdown()
        server = null

        assertEquals(3, cached.today().count)
        assertEquals(listOf("s2", "s1"), cached.recent(20).map { it.id })
        assertEquals(listOf("tab9"), cached.openTabs().map { it.id })
    }

    @Test
    fun `unreachable hub with empty cache returns explicit empty`() = runBlocking {
        val api = start()
        server!!.shutdown()
        server = null
        val fresh = repo(api)

        val today = fresh.today()
        assertEquals("", today.date)
        assertEquals(0, today.count)
        assertEquals(0L, today.totalCents)
        assertTrue(fresh.recent().isEmpty())
        assertTrue(fresh.openTabs().isEmpty())
    }

    @Test
    fun `unpaired serves empty without http`() = runBlocking {
        val offline = SalesRepo(apiProvider = { null })

        val today = offline.today()
        assertEquals(0, today.count)
        assertTrue(offline.recent().isEmpty())
        assertTrue(offline.openTabs().isEmpty())
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

    companion object {
        // Realistic GET /api/sales body (mitt wire: newest first, same sale
        // shape as closing a tab, DUMMY content).
        const val MITT_WIRE_SALES_JSON =
            """{"sales":[{"id":"s2","table_id":"t1","items":[{"product_id":"p1","name":"Fernet","unit_price_cents":1250,"qty":1,"line_total_cents":1250},{"product_id":"p2","name":"Coca","unit_price_cents":400,"qty":1,"line_total_cents":400}],"total_cents":1650,"closed_at":"2026-10-07T21:02:00Z"},{"id":"s1","table_id":"t2","items":[{"product_id":"p1","name":"Fernet","unit_price_cents":1250,"qty":1,"line_total_cents":1250}],"total_cents":1250,"closed_at":"2026-10-07T20:11:00Z"}]}"""
        // Realistic GET /api/tabs/open body (mitt wire, DUMMY content).
        const val MITT_WIRE_OPEN_TABS_JSON =
            """{"tabs":[{"id":"tab9","table_id":"t9","status":"open","opened_at":"2026-10-07T20:00:00Z","items":[{"product_id":"p1","name":"Fernet","unit_price_cents":1250,"qty":2,"line_total_cents":2500}],"total_cents":2500}]}"""
    }
}
