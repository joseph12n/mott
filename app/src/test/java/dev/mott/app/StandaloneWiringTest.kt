package dev.mott.app

import dev.mott.app.data.AppMode
import dev.mott.app.data.OpTypes
import dev.mott.app.data.OrderLinePayload
import dev.mott.app.data.PendingQueue
import dev.mott.app.data.SyncManager
import dev.mott.app.data.TabPayload
import dev.mott.app.data.remote.MittApi
import dev.mott.app.data.remote.ProductCreateRequest
import dev.mott.app.data.remote.TableCreateRequest
import dev.mott.app.data.standalone.ApiSelector
import dev.mott.app.data.standalone.InMemoryLocalStore
import dev.mott.app.data.standalone.LocalMittApi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

// T6a: ONE api chosen by mode, and the MOBILE drain applying queued ops to
// the phone's own LocalMittApi (no pairing, no network).
class StandaloneWiringTest {
    private val local = LocalMittApi(InMemoryLocalStore())
    private val remote = LocalMittApi(InMemoryLocalStore()) // stands in for the Retrofit client

    private fun selector(mode: AppMode, remoteApi: MittApi?) =
        ApiSelector(modeProvider = { mode }, local = { local }, remoteProvider = { remoteApi })

    @Test
    fun `MOBILE selects local even with no remote`() {
        assertSame(local, selector(AppMode.MOBILE, null).select())
        assertSame(local, selector(AppMode.MOBILE, remote).select())
    }

    @Test
    fun `HUB with pairing selects remote`() {
        assertSame(remote, selector(AppMode.HUB, remote).select())
    }

    @Test
    fun `HUB without pairing selects null`() {
        assertNull(selector(AppMode.HUB, null).select())
    }

    @Test
    fun `UNSET selects null and never builds either api`() {
        val sel = ApiSelector(
            modeProvider = { AppMode.UNSET },
            local = { error("local must not be built") },
            remoteProvider = { error("remote must not be built") },
        )
        assertNull(sel.select())
    }

    @Test
    fun `mode is read on every select`() {
        var mode = AppMode.HUB
        val sel = ApiSelector({ mode }, { local }, { remote })
        assertSame(remote, sel.select())
        mode = AppMode.MOBILE
        assertSame(local, sel.select())
    }

    @Test
    fun `SyncManager drained against LocalMittApi applies a SAVE_TAB op`() = runBlocking {
        val table = local.createTable(TableCreateRequest("Mesa 1")).body()!!
        val product = local.createProduct(ProductCreateRequest("Fernet", 1500)).body()!!
        val queue = PendingQueue(FakePendingOpDao())
        queue.enqueue(
            OpTypes.SAVE_TAB,
            PendingQueue.encode(
                TabPayload(
                    id = "t1",
                    tableId = table.id,
                    lines = listOf(OrderLinePayload(product.id, "Fernet", 1500, 2)),
                    isClosed = false,
                ),
            ),
        )

        val report = SyncManager(queue, local).drainOnce()

        assertEquals(1, report.synced)
        assertEquals(0, queue.peekAll().size)
        val tabs = local.openTabs().body()!!.tabs
        assertEquals(1, tabs.size)
        assertEquals(table.id, tabs[0].tableId)
        assertEquals(listOf(product.id to 2), tabs[0].items.map { it.productId to it.qty })
        assertEquals(3000L, tabs[0].totalCents)
    }
}
