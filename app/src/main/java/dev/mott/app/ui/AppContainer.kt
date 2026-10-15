package dev.mott.app.ui

import android.content.Context
import androidx.room.Room
import dev.mott.app.data.ApiOrderCatalog
import dev.mott.app.data.Capabilities
import dev.mott.app.data.AppMode
import dev.mott.app.data.AppModeStore
import dev.mott.app.data.capabilitiesFor
import dev.mott.app.data.CatalogCache
import dev.mott.app.data.ExpensesRepo
import dev.mott.app.data.OpTypes
import dev.mott.app.data.PairingStore
import dev.mott.app.data.PendingQueue
import dev.mott.app.data.SalesRepo
import dev.mott.app.data.SyncManager
import dev.mott.app.data.SyncReport
import dev.mott.app.data.local.MottDatabase
import dev.mott.app.data.remote.ApiClient
import dev.mott.app.data.remote.MittApi
import dev.mott.app.data.standalone.ApiSelector
import dev.mott.app.data.standalone.JsonFileLocalStore
import dev.mott.app.data.standalone.LocalMittApi
import dev.mott.app.data.standalone.LocalStore
import dev.mott.app.net.NetStatus
import dev.mott.app.ui.order.OrderCatalog
import dev.mott.app.ui.order.OrderSync
import java.io.File

// Manual service locator, no DI framework. Built once in MainActivity with
// the application context and passed down.
//
// Since standalone mode the PC is optional: the container hands every repo
// exactly ONE api, chosen by AppMode (see ApiSelector).
//   MOBILE -> LocalMittApi over the phone's own file store: no hub, no Wi-Fi,
//             full catalog control.
//   HUB    -> the Retrofit client for the paired hub, or null while unpaired.
//   UNSET  -> null (nothing built), which is what the first-run mode chooser
//             resolves.
// Room keeps its original job (the pending-op queue of paired installs) and
// is untouched by standalone mode; the local catalog/sales live in
// state.json + sales.jsonl under filesDir.
class AppContainer(appContext: Context) : AppServices {
    private val context: Context = appContext.applicationContext

    val database: MottDatabase by lazy {
        Room.databaseBuilder(context, MottDatabase::class.java, "mott.db").build()
    }

    val pairingStore: PairingStore by lazy { PairingStore(context) }

    val appModeStore: AppModeStore by lazy { AppModeStore(context) }

    val queue: PendingQueue by lazy { PendingQueue(database.pendingOpDao()) }

    val catalogCache: CatalogCache by lazy { CatalogCache(context) }

    // The phone's own database for MOBILE mode.
    private val localDir: File by lazy { File(context.filesDir, LOCAL_DIR_NAME) }

    val localStore: LocalStore by lazy { JsonFileLocalStore(localDir) }

    val localApi: LocalMittApi by lazy { LocalMittApi(localStore) }

    // Effective mode: the stored choice, or HUB for installs that paired
    // before modes existed (nothing is persisted by that fallback).
    override fun mode(): AppMode = appModeStore.effective(pairingStore.get() != null)

    // What the phone may administer in this mode: full catalog control in
    // MOBILE, operating only (PC owns the catalog) in HUB.
    override fun capabilities(): Capabilities = capabilitiesFor(mode())

    // Whether a request can reach its backend. In MOBILE mode the api is
    // local, so connectivity is irrelevant (and a bar with no Wi-Fi still
    // works); in HUB mode this stays the network check.
    override fun reachable(): Boolean = when (mode()) {
        AppMode.MOBILE -> true
        AppMode.HUB, AppMode.UNSET -> NetStatus.isOnline(context) && pairingStore.get() != null
    }

    // Lazily built hub client: null until paired, so every repo
    // fail-softs to cache/empty before pairing without branching.
    private fun remoteApi(): MittApi? = pairingStore.get()?.let { pairing ->
        ApiClient.build(pairing.baseUrl, pairing.token, logger = false)
    }

    private val apiSelector: ApiSelector by lazy {
        ApiSelector(
            modeProvider = { mode() },
            local = { localApi },
            remoteProvider = { remoteApi() },
        )
    }

    private fun api(): MittApi? = apiSelector.select()

    // Production catalog: the backend the mode points at is the single
    // source of truth, the cache covers offline, and the fake never appears
    // in this path. The api resolves lazily per refresh so pairing (or a
    // mode switch) after first start just works.
    private val orderCatalogInstance by lazy {
        ApiOrderCatalog(apiProvider = ::api, cache = catalogCache)
    }

    fun orderCatalog(): OrderCatalog = orderCatalogInstance

    // Panel + Mesas operate data: today totals, recent sales, open tabs.
    // Fail-soft by construction (see SalesRepo).
    private val salesRepoInstance by lazy {
        SalesRepo(apiProvider = ::api, isOnline = ::reachable)
    }

    fun salesRepo(): SalesRepo = salesRepoInstance

    // Proveedores reuse the same seam (SalesRepo hosts the supplier CRUD),
    // so a section never builds its own client from PairingStore.
    override fun suppliers(): SalesRepo = salesRepoInstance

    // Catalogo admin (create product, toggle availability) against whichever
    // catalog is authoritative for the mode.
    override fun catalogAdmin(): CatalogAdminRepo = CatalogAdminRepo(
        apiProvider = ::api,
        isOnline = ::reachable,
    )

    // Branding read/write api for Personalizar (null when the mode has no
    // backend yet, e.g. UNSET).
    override fun brandingApi(): MittApi? = api()

    // The phone's own logo (data URL) in MOBILE; HUB keeps fetching raw
    // bytes from /api/branding/logo as before.
    override suspend fun localLogoDataUrl(): String? = when (mode()) {
        AppMode.MOBILE -> localStore.getBranding()?.logoDataUrl
        AppMode.HUB, AppMode.UNSET -> null
    }

    // Gastos data: list with cached fallback plus outbox-backed adds
    // drained through the shared SyncManager FIFO against the same api.
    private val expensesRepoInstance by lazy {
        ExpensesRepo(
            apiProvider = ::api,
            queue = queue,
            isOnline = ::reachable,
            drain = { drainReport() },
        )
    }

    fun expensesRepo(): ExpensesRepo = expensesRepoInstance

    // Production OrderSync: enqueue SAVE_TAB ops, drain when the backend is
    // reachable. In MOBILE mode the drain applies them to the local api, so
    // orders commit with no hub and no network.
    fun orderSync(): OrderSync = object : OrderSync {
        override suspend fun enqueue(opType: String, payloadJson: String) {
            queue.enqueue(opType, payloadJson)
        }

        override fun isOnline(): Boolean = reachable()

        override suspend fun drainOnce(): SyncReport = drainReport()

        override suspend fun pendingCount(): Int = queue.peekAll().size
    }

    // Drains the outbox against whichever api the mode selects. No api (HUB
    // while unpaired, or UNSET) leaves the ops queued instead of dropping
    // them.
    private suspend fun drainReport(): SyncReport {
        val api = api() ?: return SyncReport()
        return SyncManager(queue, api).drainOnce()
    }

    companion object {
        // Shared so ViewModel and tests map commit payloads identically.
        const val SAVE_TAB_OP = OpTypes.SAVE_TAB

        // Folder under filesDir holding state.json and sales.jsonl.
        const val LOCAL_DIR_NAME = "standalone"
    }
}
