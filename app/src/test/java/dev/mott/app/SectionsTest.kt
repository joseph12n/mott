package dev.mott.app

import dev.mott.app.data.ApiException
import dev.mott.app.data.ExpensePayload
import dev.mott.app.data.ExpensesRepo
import dev.mott.app.data.OpTypes
import dev.mott.app.data.PendingQueue
import dev.mott.app.data.Sale
import dev.mott.app.data.SyncReport
import dev.mott.app.data.TabPayload
import dev.mott.app.data.TodayResult
import dev.mott.app.data.local.PendingOpDao
import dev.mott.app.data.local.PendingOpEntity
import dev.mott.app.data.remote.ApiClient
import dev.mott.app.data.remote.MittApi
import dev.mott.app.domain.OrderLine
import dev.mott.app.domain.Tab
import dev.mott.app.ui.AppSection
import dev.mott.app.ui.bucketSalesByDay
import dev.mott.app.ui.computePanelKpis
import dev.mott.app.ui.openTabFor
import dev.mott.app.ui.openTabTotal
import dev.mott.app.ui.order.FakeOrderCatalog
import dev.mott.app.ui.order.OrderViewModel
import dev.mott.app.ui.parseAmountToCents
import dev.mott.app.ui.sectionForRoute
import dev.mott.app.ui.startSection
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.TimeUnit

// G2: unified sections over the Figma language (JVM-pure, no Robolectric).
class SectionPendingDao : PendingOpDao {
    val ops = mutableListOf<PendingOpEntity>()
    private var nextId = 1L

    override suspend fun enqueue(op: PendingOpEntity): Long {
        val id = nextId++
        ops += op.copy(autoId = id)
        return id
    }

    override suspend fun listPending(): List<PendingOpEntity> = ops.toList()

    override suspend fun delete(id: Long) {
        ops.removeAll { it.autoId == id }
    }

    override suspend fun incrementAttempts(id: Long) = Unit
}

class SectionsTest {
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

    private fun sale(id: String, totalCents: Long, closedAt: String, tableId: String = "t1") = Sale(
        id = id,
        tableId = tableId,
        items = emptyList(),
        totalCents = totalCents,
        closedAt = closedAt,
    )

    private fun tab(tableId: String, vararg cents: Long) = Tab(
        id = "tab-$tableId",
        tableId = tableId,
        lines = cents.mapIndexed { i, c ->
            OrderLine(productId = "p$i", name = "N$i", unitPriceCents = c, qty = 1)
        },
    )

    // Navigation state machine: pairing gate plus route back-mapping.

    @Test
    fun `unpaired starts on conexion pairing entry`() {
        assertEquals(AppSection.CONEXION, startSection(paired = false))
    }

    @Test
    fun `paired starts on panel`() {
        assertEquals(AppSection.PANEL, startSection(paired = true))
    }

    @Test
    fun `every section round-trips through its route`() {
        for (section in AppSection.entries) {
            assertEquals(section, sectionForRoute(section.route))
        }
    }

    @Test
    fun `unknown and null routes fall back to panel`() {
        assertEquals(AppSection.PANEL, sectionForRoute("products"))
        assertEquals(AppSection.PANEL, sectionForRoute(null))
    }

    @Test
    fun `seven spanish-labeled sections exist in master order`() {
        assertEquals(
            listOf("Panel", "Mesas", "Catálogo", "Proveedores", "Gastos", "Conexión", "Personalizar"),
            AppSection.entries.map { it.label },
        )
    }

    // Panel KPIs: sales side from today, operate side from open tabs.

    @Test
    fun `panel kpis combine today aggregate with open tabs`() {
        val kpis = computePanelKpis(
            TodayResult(date = "2026-10-07", count = 3, totalCents = 4500L),
            listOf(tab("t1", 1000L), tab("t2", 500L, 250L)),
        )
        assertEquals(4500L, kpis.salesTotalCents)
        assertEquals(3, kpis.salesCount)
        assertEquals(2, kpis.openCount)
        assertEquals(1750L, kpis.inProgressCents)
    }

    @Test
    fun `panel kpis empty inputs stay zero`() {
        val kpis = computePanelKpis(TodayResult.empty(), emptyList())
        assertEquals(0L, kpis.salesTotalCents)
        assertEquals(0, kpis.salesCount)
        assertEquals(0, kpis.openCount)
        assertEquals(0L, kpis.inProgressCents)
    }

    // Chart bucketing: recent sales into per-day totals.

    @Test
    fun `bucketing empty sales yields empty chart`() {
        assertTrue(bucketSalesByDay(emptyList()).isEmpty())
        assertTrue(bucketSalesByDay(emptyList(), days = 0).isEmpty())
    }

    @Test
    fun `bucketing sums same-day sales into one bar`() {
        val buckets = bucketSalesByDay(
            listOf(
                sale("s2", 1650L, "2026-10-07T21:02:00Z"),
                sale("s1", 1250L, "2026-10-07T20:11:00Z"),
            ),
        )
        assertEquals(1, buckets.size)
        assertEquals("2026-10-07", buckets.single().day)
        assertEquals("10-07", buckets.single().label)
        assertEquals(2900L, buckets.single().totalCents)
    }

    @Test
    fun `bucketing orders oldest first and caps to last n`() {
        val sales = (1..9).map { day ->
            sale("s$day", 100L * day, "2026-10-%02dT20:00:00Z".format(day))
        }
        val buckets = bucketSalesByDay(sales, days = 7)
        assertEquals(7, buckets.size)
        assertEquals("2026-10-03", buckets.first().day)
        assertEquals("2026-10-09", buckets.last().day)
        assertEquals(900L, buckets.last().totalCents)
    }

    @Test
    fun `bucketing never crashes on malformed timestamps`() {
        val buckets = bucketSalesByDay(listOf(sale("s1", 100L, "nonsense")))
        assertEquals(1, buckets.size)
        assertEquals(100L, buckets.single().totalCents)
    }

    // Mesas open-tab lookups.

    @Test
    fun `open tab total sums lines and misses to zero`() {
        val tabs = listOf(tab("t1", 1000L, 500L))
        assertEquals(1500L, openTabTotal(tabs, "t1"))
        assertEquals(0L, openTabTotal(tabs, "t9"))
        assertEquals(0L, openTabTotal(emptyList(), "t1"))
    }

    @Test
    fun `open tab lookup returns null without a tab`() {
        assertNull(openTabFor(emptyList(), "t1"))
        assertEquals("tab-t1", openTabFor(listOf(tab("t1", 100L)), "t1")?.id)
    }

    // Gastos amount parsing.

    @Test
    fun `amount parsing accepts dot and comma decimals`() {
        assertEquals(1250L, parseAmountToCents("12.50"))
        assertEquals(1250L, parseAmountToCents("12,50"))
        assertEquals(1200L, parseAmountToCents("12"))
    }

    @Test
    fun `amount parsing rejects blank garbage and non-positive`() {
        assertNull(parseAmountToCents(""))
        assertNull(parseAmountToCents("  "))
        assertNull(parseAmountToCents("abc"))
        assertNull(parseAmountToCents("0"))
        assertNull(parseAmountToCents("-3"))
        assertNull(parseAmountToCents("0,00"))
    }

    // Mesas close-tab pipeline through the existing OrderSync seam.

    @Test
    fun `close tab enqueues close op and syncs when online`() = runBlocking {
        val sync = RecordingOrderSync(online = true)
        val vm = OrderViewModel(FakeOrderCatalog(), sync = sync)

        assertTrue(vm.closeTabAndSync(tabId = "tab9", tableId = "t9"))

        assertEquals(listOf(OpTypes.CLOSE_TAB), sync.history.map { it.opType })
        val payload = PendingQueue.decode<TabPayload>(sync.history.single().payloadJson)
        assertEquals("tab9", payload.id)
        assertEquals("t9", payload.tableId)
        assertTrue(payload.isClosed)
    }

    @Test
    fun `close tab stays pending when offline`() = runBlocking {
        val sync = RecordingOrderSync(online = true, autoDrain = false)
        val vm = OrderViewModel(FakeOrderCatalog(), sync = sync)

        // Drain keeps the op queued: the table is not closed yet.
        assertEquals(false, vm.closeTabAndSync(tabId = "tab9", tableId = "t9"))
        assertEquals(1, sync.enqueued.size)
    }

    // Expenses repo over the mitt wire (MockWebServer + memory only).

    @Test
    fun `expenses list maps mitt wire`() = runBlocking {
        val api = start()
        json(200, """{"expenses":[{"id":"e1","description":"Hielo","qty":2.0,"cost_cents":1500,"date":"2026-10-07"}]}""")
        val repo = ExpensesRepo(apiProvider = { api }, queue = PendingQueue(SectionPendingDao()))

        val items = repo.list()

        assertEquals(listOf("e1"), items.map { it.id })
        assertEquals("Hielo", items.single().description)
        assertEquals(1500L, items.single().costCents)
        val request = server!!.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("/api/expenses", request.path)
    }

    @Test
    fun `expenses unauthorized throws typed error`() = runBlocking {
        val api = start()
        json(401, """{"error":{"code":"unauthorized","message":"bad token"}}""")
        val repo = ExpensesRepo(apiProvider = { api }, queue = PendingQueue(SectionPendingDao()))

        try {
            repo.list()
        } catch (e: ApiException) {
            assertEquals(401, e.status)
            return@runBlocking
        }
        fail("expected ApiException(401)")
    }

    @Test
    fun `expenses unreachable serves last good cache then empty`() = runBlocking {
        val api = start()
        json(200, """{"expenses":[{"id":"e1","description":"Hielo","qty":1.0,"cost_cents":1500,"date":"2026-10-07"}]}""")
        val repo = ExpensesRepo(apiProvider = { api }, queue = PendingQueue(SectionPendingDao()))
        assertEquals(1, repo.list().size)

        server!!.shutdown()
        server = null

        assertEquals(listOf("e1"), repo.list().map { it.id })

        val fresh = ExpensesRepo(apiProvider = { api }, queue = PendingQueue(SectionPendingDao()))
        assertTrue(fresh.list().isEmpty())
    }

    @Test
    fun `expenses unpaired serves empty without http`() = runBlocking {
        val repo = ExpensesRepo(apiProvider = { null }, queue = PendingQueue(SectionPendingDao()))
        assertTrue(repo.list().isEmpty())
    }

    @Test
    fun `expenses add syncs when online`() = runBlocking {
        val dao = SectionPendingDao()
        val repo = ExpensesRepo(
            apiProvider = { null },
            queue = PendingQueue(dao),
            isOnline = { true },
            drain = {
                dao.ops.clear()
                SyncReport(synced = 1)
            },
        )

        assertEquals("SINCRONIZADO", repo.add("Hielo", qty = 1.0, costCents = 1500L))

        assertEquals(0, dao.ops.size)
    }

    @Test
    fun `expenses add stays pending when offline`() = runBlocking {
        val dao = SectionPendingDao()
        val repo = ExpensesRepo(
            apiProvider = { null },
            queue = PendingQueue(dao),
            isOnline = { false },
        )

        assertEquals("PENDIENTE (1)", repo.add("Hielo", qty = 1.0, costCents = 1500L))

        val payload = PendingQueue.decode<ExpensePayload>(dao.ops.single().payloadJson)
        assertEquals(OpTypes.ADD_EXPENSE, dao.ops.single().opType)
        assertEquals("Hielo", payload.description)
        assertEquals(1500L, payload.costCents)
    }
}
