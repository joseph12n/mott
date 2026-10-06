package dev.mott.app

import dev.mott.app.data.ExpensePayload
import dev.mott.app.data.OpTypes
import dev.mott.app.data.OrderLinePayload
import dev.mott.app.data.PendingQueue
import dev.mott.app.data.ProductPayload
import dev.mott.app.data.TabPayload
import dev.mott.app.data.local.OrderLineEntity
import dev.mott.app.data.local.PendingOpDao
import dev.mott.app.data.local.PendingOpEntity
import dev.mott.app.data.local.TabDao
import dev.mott.app.data.local.TabEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// JVM-pure tests: fakes implement the DAO contracts in memory, no Room involved.
class FakePendingOpDao : PendingOpDao {
    private val rows = mutableListOf<PendingOpEntity>()
    private var nextId = 1L

    override suspend fun enqueue(op: PendingOpEntity): Long {
        val id = nextId++
        rows += op.copy(autoId = id)
        return id
    }

    override suspend fun listPending(): List<PendingOpEntity> = rows.sortedBy { it.autoId }

    override suspend fun delete(id: Long) {
        rows.removeAll { it.autoId == id }
    }

    override suspend fun incrementAttempts(id: Long) {
        rows.replaceAll { if (it.autoId == id) it.copy(attempts = it.attempts + 1) else it }
    }
}

// Uses the real TabDao.upsertWithLines default body against in-memory maps,
// so the replace-lines semantics under test are the production ones.
class FakeTabDao : TabDao() {
    val tabs = mutableMapOf<String, TabEntity>()
    val lines = mutableMapOf<String, MutableList<OrderLineEntity>>()

    override suspend fun upsertTab(tab: TabEntity) {
        tabs[tab.id] = tab
    }

    override suspend fun deleteLines(tabId: String) {
        lines.remove(tabId)
    }

    override suspend fun insertLines(newLines: List<OrderLineEntity>) {
        for (line in newLines) {
            lines.getOrPut(line.tabId) { mutableListOf() }.removeAll { it.productId == line.productId }
            lines.getOrPut(line.tabId) { mutableListOf() } += line
        }
    }

    override suspend fun getOpenByTable(tableId: String): TabEntity? =
        tabs.values.firstOrNull { it.tableId == tableId && !it.isClosed }

    override suspend fun listOpen(): List<TabEntity> =
        tabs.values.filter { !it.isClosed }.sortedBy { it.openedAt }

    override suspend fun linesForTab(tabId: String): List<OrderLineEntity> =
        lines[tabId].orEmpty().toList()
}

class QueueTest {
    private fun queue(clock: () -> Long = { 1_700_000_000_000L }) = PendingQueue(FakePendingOpDao(), clock)

    @Test
    fun `enqueue preserves FIFO order`() = runBlocking {
        val q = queue()
        q.enqueue(OpTypes.SAVE_TAB, "{}")
        q.enqueue(OpTypes.ADD_EXPENSE, "{}")
        q.enqueue(OpTypes.UPSERT_PRODUCT, "{}")

        val ops = q.peekAll()
        assertEquals(listOf(OpTypes.SAVE_TAB, OpTypes.ADD_EXPENSE, OpTypes.UPSERT_PRODUCT), ops.map { it.opType })
        assertTrue(ops[0].autoId < ops[1].autoId && ops[1].autoId < ops[2].autoId)
    }

    @Test
    fun `remove drops only the given op`() = runBlocking {
        val q = queue()
        val first = q.enqueue(OpTypes.SAVE_TAB, "{}")
        q.enqueue(OpTypes.CLOSE_TAB, "{}")

        q.remove(first)

        val ops = q.peekAll()
        assertEquals(1, ops.size)
        assertEquals(OpTypes.CLOSE_TAB, ops[0].opType)
    }

    @Test
    fun `bumpAttempts increments without touching payload`() = runBlocking {
        val q = queue()
        val id = q.enqueue(OpTypes.ADD_EXPENSE, """{"id":"e1"}""")

        q.bumpAttempts(id)
        q.bumpAttempts(id)

        val ops = q.peekAll()
        assertEquals(1, ops.size)
        assertEquals(2, ops[0].attempts)
        assertEquals(OpTypes.ADD_EXPENSE, ops[0].opType)
        assertEquals("""{"id":"e1"}""", ops[0].payloadJson)
    }

    @Test
    fun `product payload roundtrips`() {
        val original = ProductPayload(id = "p1", name = "Fernet", priceCents = 1500, available = true)
        val decoded: ProductPayload = PendingQueue.decode(PendingQueue.encode(original))
        assertEquals(original, decoded)
    }

    @Test
    fun `tab payload with lines roundtrips`() {
        val original = TabPayload(
            id = "t1",
            tableId = "mesa-3",
            lines = listOf(
                OrderLinePayload(productId = "p1", name = "Fernet", unitPriceCents = 1500, qty = 2),
                OrderLinePayload(productId = "p2", name = "Coca", unitPriceCents = 800, qty = 1),
            ),
            isClosed = false,
        )
        val decoded: TabPayload = PendingQueue.decode(PendingQueue.encode(original))
        assertEquals(original, decoded)
    }

    @Test
    fun `expense payload roundtrips`() {
        val original = ExpensePayload(
            id = "e9",
            description = "Hielo",
            qty = 2.5,
            costCents = 3000,
            dateEpochMs = 1_700_000_000_000L,
        )
        val decoded: ExpensePayload = PendingQueue.decode(PendingQueue.encode(original))
        assertEquals(original, decoded)
    }

    @Test
    fun `upsertWithLines replaces previous lines`() = runBlocking {
        val dao = FakeTabDao()
        val tab = TabEntity(id = "t1", tableId = "mesa-3", isClosed = false, openedAt = 10L, updatedAt = 10L)

        dao.upsertWithLines(
            tab,
            listOf(
                OrderLineEntity(tabId = "t1", productId = "p1", name = "Fernet", unitPriceCents = 1500, qty = 2),
                OrderLineEntity(tabId = "t1", productId = "p2", name = "Coca", unitPriceCents = 800, qty = 1),
            ),
        )
        assertEquals(2, dao.linesForTab("t1").size)

        dao.upsertWithLines(
            tab.copy(updatedAt = 20L),
            listOf(
                OrderLineEntity(tabId = "t1", productId = "p3", name = "Agua", unitPriceCents = 500, qty = 3),
            ),
        )

        val lines = dao.linesForTab("t1")
        assertEquals(1, lines.size)
        assertEquals("p3", lines[0].productId)
        assertEquals(20L, dao.getOpenByTable("mesa-3")?.updatedAt)
    }

    @Test
    fun `upsertWithLines with empty lines clears the tab`() = runBlocking {
        val dao = FakeTabDao()
        val tab = TabEntity(id = "t1", tableId = "mesa-3", isClosed = false, openedAt = 10L, updatedAt = 10L)

        dao.upsertWithLines(
            tab,
            listOf(OrderLineEntity(tabId = "t1", productId = "p1", name = "Fernet", unitPriceCents = 1500, qty = 1)),
        )
        dao.upsertWithLines(tab, emptyList())

        assertTrue(dao.linesForTab("t1").isEmpty())
        assertEquals(tab, dao.getOpenByTable("mesa-3"))
    }
}
