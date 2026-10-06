package dev.mott.app.ui

import android.content.Context
import androidx.room.Room
import dev.mott.app.data.OpTypes
import dev.mott.app.data.PairingStore
import dev.mott.app.data.PendingQueue
import dev.mott.app.data.SyncManager
import dev.mott.app.data.SyncReport
import dev.mott.app.data.local.MottDatabase
import dev.mott.app.data.remote.ApiClient
import dev.mott.app.net.NetStatus
import dev.mott.app.ui.order.OrderSync

// Manual service locator, no DI framework. Built once in MainActivity with
// the application context and passed down; Room needs the context for
// databaseBuilder, everything else derives lazily from the database.
class AppContainer(appContext: Context) {
    private val context: Context = appContext.applicationContext

    val database: MottDatabase by lazy {
        Room.databaseBuilder(context, MottDatabase::class.java, "mott.db").build()
    }

    val pairingStore: PairingStore by lazy { PairingStore(context) }

    val queue: PendingQueue by lazy { PendingQueue(database.pendingOpDao()) }

    // Production OrderSync: enqueue SAVE_TAB ops, drain when the hub is
    // reachable. Without a saved pairing the drain is a no-op report.
    fun orderSync(): OrderSync = object : OrderSync {
        override suspend fun enqueue(opType: String, payloadJson: String) {
            queue.enqueue(opType, payloadJson)
        }

        override fun isOnline(): Boolean = NetStatus.isOnline(context)

        override suspend fun drainOnce(): SyncReport {
            val pairing = pairingStore.get() ?: return SyncReport()
            val api = ApiClient.build(pairing.baseUrl, pairing.token, logger = false)
            return SyncManager(queue, api).drainOnce()
        }

        override suspend fun pendingCount(): Int = queue.peekAll().size
    }

    companion object {
        // Shared so ViewModel and tests map commit payloads identically.
        const val SAVE_TAB_OP = OpTypes.SAVE_TAB
    }
}
