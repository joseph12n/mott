package dev.mott.app

import dev.mott.app.data.ExpensePayload
import dev.mott.app.data.OpQueue
import dev.mott.app.data.OpTypes
import dev.mott.app.data.OrderLinePayload
import dev.mott.app.data.PendingQueue
import dev.mott.app.data.ProductPayload
import dev.mott.app.data.SyncManager
import dev.mott.app.data.TabPayload
import dev.mott.app.data.local.PendingOpEntity
import dev.mott.app.data.remote.ApiClient
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.concurrent.TimeUnit

// JVM-pure sync tests: an in-memory OpQueue fake plus MockWebServer, no
// Room and no Android framework involved.
class FakeOpQueue(initial: List<PendingOpEntity> = emptyList()) : OpQueue {
    private val rows = initial.toMutableList()

    override suspend fun peekAll(): List<PendingOpEntity> = rows.sortedBy { it.autoId }

    override suspend fun remove(id: Long) {
        rows.removeAll { it.autoId == id }
    }

    override suspend fun bumpAttempts(id: Long) {
        rows.replaceAll { if (it.autoId == id) it.copy(attempts = it.attempts + 1) else it }
    }
}

class SyncTest {
    private val server = MockWebServer()

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun productOp(id: Long, attempts: Int = 0) = PendingOpEntity(
        autoId = id,
        opType = OpTypes.UPSERT_PRODUCT,
        payloadJson = PendingQueue.encode(ProductPayload(id = "p1", name = "Fernet", priceCents = 1500, available = true)),
        createdAt = 10L,
        attempts = attempts,
    )

    private fun tabOp(id: Long, type: String, attempts: Int = 0) = PendingOpEntity(
        autoId = id,
        opType = type,
        payloadJson = PendingQueue.encode(
            TabPayload(
                id = "t1",
                tableId = "mesa-1",
                lines = listOf(OrderLinePayload(productId = "p1", name = "Fernet", unitPriceCents = 1500, qty = 2)),
                isClosed = false,
            ),
        ),
        createdAt = 10L,
        attempts = attempts,
    )

    private fun expenseOp(id: Long) = PendingOpEntity(
        autoId = id,
        opType = OpTypes.ADD_EXPENSE,
        payloadJson = PendingQueue.encode(
            ExpensePayload(id = "e1", description = "Hielo", qty = 2.5, costCents = 3000, dateEpochMs = 10L),
        ),
        createdAt = 10L,
    )

    private fun json(code: Int, body: String) {
        server.enqueue(
            MockResponse().setResponseCode(code).setBody(body).addHeader("Content-Type", "application/json"),
        )
    }

    private fun api(token: String? = "pair-token") =
        ApiClient.build(server.url("/").toString(), token, logger = false)

    @Test
    fun `protected calls carry the bearer token`() = runBlocking {
        json(200, """{"products":[]}""")

        api("tok123").listProducts()

        val request = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("Bearer tok123", request.getHeader("Authorization"))
    }

    @Test
    fun `health skips the auth header`() = runBlocking {
        json(200, """{"status":"ok"}""")

        val response = api("tok123").health()

        val request = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertNull(request.getHeader("Authorization"))
        assertEquals("ok", response.body()!!.status)
    }

    @Test
    fun `null token sends no auth header`() = runBlocking {
        json(200, """{"products":[]}""")

        api(null).listProducts()

        val request = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertNull(request.getHeader("Authorization"))
    }

    @Test
    fun `base url without trailing slash works`() = runBlocking {
        json(200, """{"status":"ok"}""")
        val bare = server.url("/").toString().trimEnd('/')

        val response = ApiClient.build(bare, null, logger = false).health()

        assertEquals("ok", response.body()!!.status)
    }

    @Test
    fun `drain happy path removes every op`() = runBlocking {
        val queue = FakeOpQueue(listOf(productOp(1), tabOp(2, OpTypes.SAVE_TAB), tabOp(3, OpTypes.CLOSE_TAB), expenseOp(4)))
        json(201, """{"id":"p1","name":"Fernet","price_cents":1500,"available":true}""")
        json(201, """{"id":"t1","table_id":"mesa-1","status":"open","opened_at":"2026-01-01T00:00:00Z","items":[],"total_cents":0}""")
        json(200, """{"id":"t1","table_id":"mesa-1","status":"open","opened_at":"2026-01-01T00:00:00Z","items":[],"total_cents":3000}""")
        json(200, """{"id":"s1","table_id":"mesa-1","items":[],"total_cents":3000,"closed_at":"2026-01-01T01:00:00Z"}""")
        json(201, """{"id":"e1","description":"Hielo","qty":2.5,"cost_cents":3000,"date":"2026-01-01T00:00:00Z"}""")

        val report = SyncManager(queue, api()).drainOnce()

        assertEquals(0, queue.peekAll().size)
        assertEquals(4, report.synced)
        assertEquals(0, report.dropped)
        assertEquals(0, report.deferred)
        assertEquals(0, report.exhausted)
        assertEquals(5, server.requestCount)
    }

    @Test
    fun `server error stops the drain and bumps attempts`() = runBlocking {
        val queue = FakeOpQueue(listOf(productOp(1), expenseOp(2)))
        json(500, """{"error":{"code":"boom","message":"try later"}}""")

        val report = SyncManager(queue, api()).drainOnce()

        assertEquals(listOf(1L, 2L), queue.peekAll().map { it.autoId })
        assertEquals(1, queue.peekAll().first { it.autoId == 1L }.attempts)
        assertEquals(0, queue.peekAll().first { it.autoId == 2L }.attempts)
        assertEquals(0, report.synced)
        // Both the failed op and the unvisited op behind it stay queued
        // with attempts left, so both count as deferred.
        assertEquals(2, report.deferred)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `client error drops the poison op and continues`() = runBlocking {
        val queue = FakeOpQueue(listOf(productOp(1), expenseOp(2)))
        json(422, """{"error":{"code":"invalid","message":"bad price"}}""")
        json(201, """{"id":"e1","description":"Hielo","qty":2.5,"cost_cents":3000,"date":"2026-01-01T00:00:00Z"}""")

        val report = SyncManager(queue, api()).drainOnce()

        assertEquals(0, queue.peekAll().size)
        assertEquals(1, report.synced)
        assertEquals(1, report.dropped)
        assertEquals(0, report.deferred)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `hitting max attempts leaves the op and reports exhausted`() = runBlocking {
        val queue = FakeOpQueue(listOf(productOp(1, attempts = 4)))
        json(500, """{"error":{"code":"boom","message":"try later"}}""")

        val report = SyncManager(queue, api(), maxAttempts = 5).drainOnce()

        assertEquals(1, queue.peekAll().size)
        assertEquals(5, queue.peekAll()[0].attempts)
        assertEquals(1, report.exhausted)
        assertEquals(0, report.deferred)
    }

    @Test
    fun `already exhausted ops are skipped without blocking later ones`() = runBlocking {
        val queue = FakeOpQueue(listOf(productOp(1, attempts = 5), expenseOp(2)))
        json(201, """{"id":"e1","description":"Hielo","qty":2.5,"cost_cents":3000,"date":"2026-01-01T00:00:00Z"}""")

        val report = SyncManager(queue, api(), maxAttempts = 5).drainOnce()

        assertEquals(listOf(1L), queue.peekAll().map { it.autoId })
        assertEquals(1, report.synced)
        assertEquals(1, report.exhausted)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `unknown op type is dropped without any http call`() = runBlocking {
        val queue = FakeOpQueue(
            listOf(PendingOpEntity(autoId = 1, opType = "NOPE", payloadJson = "{}", createdAt = 10L)),
        )

        val report = SyncManager(queue, api()).drainOnce()

        assertEquals(0, queue.peekAll().size)
        assertEquals(1, report.dropped)
        assertEquals(0, server.requestCount)
    }
}
