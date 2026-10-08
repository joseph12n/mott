package dev.mott.app

import dev.mott.app.data.ApiException
import dev.mott.app.data.ExpenseItem
import dev.mott.app.data.ExpensesRepo
import dev.mott.app.data.PendingQueue
import dev.mott.app.data.local.PendingOpDao
import dev.mott.app.data.local.PendingOpEntity
import dev.mott.app.data.remote.ApiClient
import dev.mott.app.data.remote.MittApi
import dev.mott.app.ui.LoadFailureReason
import dev.mott.app.ui.LoadState
import dev.mott.app.ui.expenseLineTotal
import dev.mott.app.ui.expenseSubline
import dev.mott.app.ui.expensesTotal
import dev.mott.app.ui.formatExpenseQty
import dev.mott.app.ui.loadExpensesState
import dev.mott.app.ui.mapExpenseSaveFailure
import dev.mott.app.ui.shouldShowExpensesEmpty
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.util.concurrent.TimeUnit

// DAO fake whose enqueue explodes: proves the ExpensesRepo add path lets
// cancellation (and only the enqueue outcome, never a crash) through.
class ThrowingEnqueueDao(
    private val failure: Throwable,
) : PendingOpDao {
    override suspend fun enqueue(op: PendingOpEntity): Long = throw failure

    override suspend fun listPending(): List<PendingOpEntity> = emptyList()

    override suspend fun delete(id: Long) = Unit

    override suspend fun incrementAttempts(id: Long) = Unit
}

// T6 Gastos parity: line totals multiply qty by unit cost (mitt PC Gastos
// reduction), the Sin-gastos empty state never renders together with the
// failure card (R3-003), and CancellationException is rethrown, never
// classified (R3-002). JVM-pure, no Compose.
class ExpensesStateTest {
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

    private fun item(id: String = "e1", qty: Double = 1.0, costCents: Long = 1500L, date: String = "2026-10-07") =
        ExpenseItem(id = id, description = "Hielo", qty = qty, costCents = costCents, date = date)

    @Test
    fun `expense line total multiplies qty by unit cost`() {
        assertEquals(1500L, expenseLineTotal(item(qty = 1.0, costCents = 1500L)))
        assertEquals(3000L, expenseLineTotal(item(qty = 2.0, costCents = 1500L)))
        assertEquals(2250L, expenseLineTotal(item(qty = 1.5, costCents = 1500L)))
    }

    @Test
    fun `expenses total sums line totals`() {
        val items = listOf(item("e1", qty = 2.0, costCents = 1500L), item("e2", qty = 1.0, costCents = 500L))

        assertEquals(3500L, expensesTotal(items))
        assertEquals(0L, expensesTotal(emptyList()))
    }

    @Test
    fun `expense qty formats whole numbers without decimals`() {
        assertEquals("2", formatExpenseQty(2.0))
        assertEquals("1", formatExpenseQty(1.0))
        assertEquals("1.5", formatExpenseQty(1.5))
    }

    @Test
    fun `expense subline mirrors the web qty by unit copy`() {
        assertEquals("2026-10-07 · 2 × $ 15.00", expenseSubline(item(qty = 2.0, costCents = 1500L)))
        assertEquals("1 × $ 15.00", expenseSubline(item(date = "")))
    }

    @Test
    fun `empty gastos state renders only without failure`() {
        // R3-003: the Sin-gastos empty state must not render together with
        // the failure card.
        assertTrue(shouldShowExpensesEmpty(emptyList(), null))
        assertFalse(shouldShowExpensesEmpty(emptyList(), LoadFailureReason.TOKEN_INVALIDO))
        assertFalse(shouldShowExpensesEmpty(emptyList(), LoadFailureReason.SIN_SERVIDOR))
        assertFalse(shouldShowExpensesEmpty(emptyList(), LoadFailureReason.ERROR_INESPERADO))
        assertFalse(shouldShowExpensesEmpty(listOf(item()), null))
        assertFalse(shouldShowExpensesEmpty(listOf(item()), LoadFailureReason.SIN_SERVIDOR))
    }

    @Test
    fun `load expenses maps 401 to token_invalido`() = runBlocking {
        val api = start()
        json(401, """{"error":{"code":"unauthorized","message":"bad token"}}""")
        val repo = ExpensesRepo(apiProvider = { api }, queue = PendingQueue(SectionPendingDao()))

        val state = loadExpensesState(repo::list)

        assertEquals(LoadState.Failed(LoadFailureReason.TOKEN_INVALIDO), state)
    }

    @Test
    fun `load expenses maps io to sin_servidor`() = runBlocking {
        val state = loadExpensesState(fetch = { throw IOException("wifi down") })

        assertEquals(LoadState.Failed(LoadFailureReason.SIN_SERVIDOR), state)
    }

    @Test
    fun `load expenses rethrows cancellation`() = runBlocking {
        try {
            loadExpensesState(fetch = { throw CancellationException("teardown") })
        } catch (e: CancellationException) {
            return@runBlocking
        }
        fail("expected CancellationException to propagate")
    }

    @Test
    fun `load expenses ready serves qty from the wire`() = runBlocking {
        val api = start()
        json(200, """{"expenses":[{"id":"e1","description":"Hielo","qty":2.0,"cost_cents":1500,"date":"2026-10-07"}]}""")
        val repo = ExpensesRepo(apiProvider = { api }, queue = PendingQueue(SectionPendingDao()))

        val state = loadExpensesState(repo::list)

        assertTrue(state is LoadState.Ready)
        val items = (state as LoadState.Ready).data
        assertEquals(2.0, items.single().qty, 0.0)
        assertEquals(3000L, expensesTotal(items))
        val request = server!!.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("/api/expenses", request.path)
    }

    @Test
    fun `save maps validation failures to revisar`() {
        assertEquals("REVISAR CONCEPTO Y MONTO", mapExpenseSaveFailure(IllegalArgumentException("blank")))
        // A 422 is bad input, not a sick server: retry would never help.
        assertEquals("REVISAR CONCEPTO Y MONTO", mapExpenseSaveFailure(ApiException(422, "validation_error")))
    }

    @Test
    fun `save maps transport and server failures honestly`() {
        assertEquals("SIN CONEXIÓN, SE REINTENTA", mapExpenseSaveFailure(IOException("wifi down")))
        assertEquals("ERROR DEL SERVIDOR, SE REINTENTA", mapExpenseSaveFailure(ApiException(500, "boom")))
        assertEquals("ERROR INESPERADO, SE REINTENTA", mapExpenseSaveFailure(RuntimeException("boom")))
    }

    @Test
    fun `save rethrows cancellation`() {
        try {
            mapExpenseSaveFailure(CancellationException("teardown"))
        } catch (e: CancellationException) {
            return
        }
        fail("expected CancellationException to propagate")
    }

    @Test
    fun `expense add cancellation propagates from the queue`() = runBlocking {
        val repo = ExpensesRepo(
            apiProvider = { null },
            queue = PendingQueue(ThrowingEnqueueDao(CancellationException("teardown"))),
            isOnline = { true },
        )

        try {
            repo.add("Hielo", qty = 1.0, costCents = 1500L)
        } catch (e: CancellationException) {
            return@runBlocking
        }
        fail("expected CancellationException to propagate")
    }

    @Test
    fun `expense add enqueue failure reports not saved`() = runBlocking {
        val repo = ExpensesRepo(
            apiProvider = { null },
            queue = PendingQueue(ThrowingEnqueueDao(RuntimeException("disk full"))),
            isOnline = { true },
        )

        // Same rule as the ANOTAR path: an op that never reached the queue
        // is NOT pending, so the UI never promises a sync that will never
        // happen.
        assertEquals("ERROR: NO GUARDADO", repo.add("Hielo", qty = 1.0, costCents = 1500L))
    }
}
