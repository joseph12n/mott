package dev.mott.app

import dev.mott.app.data.OpTypes
import dev.mott.app.data.PendingQueue
import dev.mott.app.data.SyncReport
import dev.mott.app.data.TabPayload
import dev.mott.app.ui.order.FakeOrderCatalog
import dev.mott.app.ui.order.OrderSync
import dev.mott.app.ui.order.OrderViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

// JVM-pure wiring test: commit -> SAVE_TAB op in the queue with the right
// table and lines, then lastSync reflects synced vs deferred.
class RecordingOrderSync(
    var online: Boolean = true,
    var autoDrain: Boolean = true,
) : OrderSync {
    data class Enqueued(val opType: String, val payloadJson: String)

    val enqueued = mutableListOf<Enqueued>()
    val history = mutableListOf<Enqueued>()
    var drainCalls = 0

    override suspend fun enqueue(opType: String, payloadJson: String) {
        val op = Enqueued(opType, payloadJson)
        enqueued += op
        history += op
    }

    override fun isOnline(): Boolean = online

    override suspend fun drainOnce(): SyncReport {
        drainCalls++
        return if (autoDrain) {
            enqueued.clear()
            SyncReport(synced = 1)
        } else {
            SyncReport(deferred = enqueued.size)
        }
    }

    override suspend fun pendingCount(): Int = enqueued.size
}

class WiringTest {

    private fun orderWithLines(sync: OrderSync): OrderViewModel {
        val vm = OrderViewModel(FakeOrderCatalog(), sync = sync)
        vm.selectTable("t1")
        vm.increment("p1")
        vm.increment("p1")
        vm.increment("p2")
        return vm
    }

    @Test
    fun `commit enqueues SAVE_TAB decoding to the right table and lines`() = runBlocking {
        val sync = RecordingOrderSync(online = true)
        val vm = orderWithLines(sync)

        val payload = vm.commitAndSync()

        assertNotNull(payload)
        assertEquals(1, sync.history.size)
        assertEquals(OpTypes.SAVE_TAB, sync.history.single().opType)
        val tab: TabPayload = PendingQueue.decode(sync.history.single().payloadJson)
        assertEquals(payload!!.tableId, tab.tableId)
        assertEquals(payload.id, tab.id)
        assertEquals(
            mapOf("p1" to 2, "p2" to 1),
            tab.lines.associate { it.productId to it.qty },
        )
    }

    @Test
    fun `online drain marks SINCRONIZADO`() = runBlocking {
        val sync = RecordingOrderSync(online = true)
        val vm = orderWithLines(sync)

        vm.commitAndSync()

        assertEquals(1, sync.drainCalls)
        assertEquals("SINCRONIZADO", vm.state.value.lastSync)
    }

    @Test
    fun `offline enqueue marks PENDIENTE without draining`() = runBlocking {
        val sync = RecordingOrderSync(online = false)
        val vm = orderWithLines(sync)

        vm.commitAndSync()

        assertEquals(0, sync.drainCalls)
        assertEquals(1, sync.enqueued.size)
        assertTrue(vm.state.value.lastSync!!.startsWith("PENDIENTE"))
    }

    @Test
    fun `failed drain keeps PENDIENTE`() = runBlocking {
        val sync = RecordingOrderSync(online = true, autoDrain = false)
        val vm = orderWithLines(sync)

        vm.commitAndSync()

        assertEquals(1, sync.drainCalls)
        assertTrue(vm.state.value.lastSync!!.startsWith("PENDIENTE"))
    }

    @Test
    fun `commit without sync keeps old behavior and null lastSync`() = runBlocking {
        val vm = OrderViewModel(FakeOrderCatalog())
        vm.selectTable("t1")
        vm.increment("p1")

        val payload = vm.commitAndSync()

        assertNotNull(payload)
        assertEquals(null, vm.state.value.lastSync)
    }
}
