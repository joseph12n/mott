package dev.mott.app.ui.order

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.mott.app.data.ApiOrderCatalog
import dev.mott.app.data.OpTypes
import dev.mott.app.data.OrderLinePayload
import dev.mott.app.data.PendingQueue
import dev.mott.app.data.SyncReport
import dev.mott.app.data.TabPayload
import dev.mott.app.domain.ClosedTab
import dev.mott.app.domain.OrderLine
import dev.mott.app.domain.Product
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

// Table reference for the order flow. Occupancy is display-only here;
// M6 resolves conflicts against the synced tab state.
data class TableRef(
    val id: String,
    val label: String,
    val occupied: Boolean,
)

// Source of tables and products. M6 replaces the fake with Room + API data.
interface OrderCatalog {
    fun listTables(): List<TableRef>
    fun listProducts(): List<Product>
}

// In-memory catalog for previews and JVM tests.
class FakeOrderCatalog : OrderCatalog {
    override fun listTables(): List<TableRef> = listOf(
        TableRef("t1", "MESA 1", occupied = false),
        TableRef("t2", "MESA 2", occupied = true),
        TableRef("t3", "MESA 3", occupied = false),
        TableRef("t4", "MESA 4", occupied = true),
        TableRef("t5", "MESA 5", occupied = false),
        TableRef("t6", "BARRA", occupied = false),
    )

    override fun listProducts(): List<Product> = listOf(
        Product("p1", "Fernet", 1250, available = true),
        Product("p2", "Coca-Cola", 400, available = true),
        Product("p3", "Cerveza", 600, available = true),
        Product("p4", "Vino tinto", 900, available = true),
        Product("p5", "Agua", 300, available = true),
        Product("p6", "Papas fritas", 700, available = false),
    )
}

data class OrderUiState(
    val tables: List<TableRef> = emptyList(),
    val selectedTableId: String? = null,
    val products: List<Product> = emptyList(),
    val lines: Map<String, Int> = emptyMap(),
    val runningTotalCents: Long = 0L,
    val isOffline: Boolean = false,
    val error: String? = null,
    val lastOrder: ClosedTab? = null,
    // Outcome of the last ANOTAR wiring: SINCRONIZADO when the queue
    // drained clean, PENDIENTE (n) while n ops still wait for the hub.
    val lastSync: String? = null,
) {
    val selectedTable: TableRef? get() = tables.find { it.id == selectedTableId }
}

// Queue + drain boundary for the ANOTAR path. The ViewModel maps the
// commit payload to a SAVE_TAB op itself so the mapping stays JVM-pure;
// production (AppContainer) and tests both implement this interface.
interface OrderSync {
    suspend fun enqueue(opType: String, payloadJson: String)
    fun isOnline(): Boolean
    suspend fun drainOnce(): SyncReport
    suspend fun pendingCount(): Int
}

// Holds the 3-tap order flow state. Intent functions are pure state
// transitions; commit only validates and builds the ClosedTab payload.
// commitAndSync hands the payload to the offline queue and drains it when
// online; submit is the UI entry point running that pipeline in
// viewModelScope. Tests drive the suspend commitAndSync directly with a
// fake OrderSync and an injected scope-free path (no Main dispatcher).
class OrderViewModel(
    private val catalog: OrderCatalog = FakeOrderCatalog(),
    isOffline: Boolean = false,
    private val sync: OrderSync? = null,
    private val workScope: CoroutineScope? = null,
) : ViewModel() {
    private var productById: Map<String, Product> =
        catalog.listProducts().associateBy { it.id }

    private val _state = MutableStateFlow(
        OrderUiState(
            tables = catalog.listTables(),
            products = productById.values.toList(),
            isOffline = isOffline,
        ),
    )
    val state: StateFlow<OrderUiState> = _state.asStateFlow()

    fun selectTable(tableId: String) {
        _state.update {
            it.copy(selectedTableId = tableId, error = null, lastOrder = null, lastSync = null)
        }
    }

    fun toggleLine(productId: String) {
        val product = productById[productId] ?: return
        if (!product.available) {
            _state.update { it.copy(error = "Producto no disponible") }
            return
        }
        _state.update { current ->
            val next = current.lines.toMutableMap()
            if (next.containsKey(productId)) {
                next.remove(productId)
            } else {
                next[productId] = 1
            }
            current.copy(
                lines = next,
                runningTotalCents = totalOf(next),
                error = null,
                lastOrder = null,
                lastSync = null,
            )
        }
    }

    fun increment(productId: String) {
        if (productById[productId]?.available != true) return
        _state.update { current ->
            val next = current.lines.toMutableMap()
            next[productId] = (next[productId] ?: 0) + 1
            current.copy(
                lines = next,
                runningTotalCents = totalOf(next),
                error = null,
                lastOrder = null,
                lastSync = null,
            )
        }
    }

    fun decrement(productId: String) {
        _state.update { current ->
            val next = current.lines.toMutableMap()
            val qty = (next[productId] ?: 0) - 1
            if (qty <= 0) {
                next.remove(productId)
            } else {
                next[productId] = qty
            }
            current.copy(
                lines = next,
                runningTotalCents = totalOf(next),
                error = null,
                lastOrder = null,
                lastSync = null,
            )
        }
    }

    fun setOffline(offline: Boolean) {
        _state.update { it.copy(isOffline = offline) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun commit(): ClosedTab? {
        val current = _state.value
        val tableId = current.selectedTableId
        if (tableId == null) {
            _state.update { it.copy(error = "Elegir una mesa para continuar") }
            return null
        }
        if (current.lines.isEmpty()) {
            _state.update { it.copy(error = "Agregar al menos un producto") }
            return null
        }
        val orderLines = current.lines.mapNotNull { (productId, qty) ->
            val product = productById[productId] ?: return@mapNotNull null
            OrderLine(
                productId = productId,
                name = product.name,
                unitPriceCents = product.priceCents,
                qty = qty,
            )
        }
        if (orderLines.isEmpty()) {
            _state.update { it.copy(error = "Agregar al menos un producto") }
            return null
        }
        val payload = ClosedTab(
            id = UUID.randomUUID().toString(),
            tableId = tableId,
            lines = orderLines,
            totalCents = orderLines.sumOf { it.lineTotal },
            closedAtEpochMs = System.currentTimeMillis(),
        )
        _state.update { it.copy(error = null, lastOrder = payload) }
        return payload
    }

    fun startNewOrder() {
        _state.update {
            it.copy(
                selectedTableId = null,
                lines = emptyMap(),
                runningTotalCents = 0L,
                error = null,
                lastOrder = null,
                lastSync = null,
            )
        }
    }

    // ANOTAR pipeline: validate + build the payload (commit), enqueue it as
    // SAVE_TAB, then drain when online. Suspend version for tests; submit()
    // below is the UI entry point running it in viewModelScope. Enqueue runs
    // in its own runCatching: if the op never reaches the queue (disk/encode
    // failure) the order is NOT pending, so lastSync reports ERROR instead of
    // promising a sync that will never happen. Drain + count run in a second
    // runCatching: a non-IO failure there (e.g. serialization on a
    // captive-portal body) reports PENDIENTE instead of escaping into
    // viewModelScope and crashing the process; a count failure reports bare
    // PENDIENTE rather than a fabricated number.
    suspend fun commitAndSync(): ClosedTab? {
        val payload = commit() ?: return null
        val queue = sync ?: return payload
        val tab = TabPayload(
            id = payload.id,
            tableId = payload.tableId,
            lines = payload.lines.map { line ->
                OrderLinePayload(
                    productId = line.productId,
                    name = line.name,
                    unitPriceCents = line.unitPriceCents,
                    qty = line.qty,
                )
            },
            isClosed = false,
        )
        val enqueued = runCatching {
            queue.enqueue(OpTypes.SAVE_TAB, PendingQueue.encode(tab))
        }.isSuccess
        if (!enqueued) {
            _state.update { it.copy(lastSync = "ERROR: NO GUARDADO") }
            return payload
        }
        val pending = runCatching {
            if (queue.isOnline()) {
                queue.drainOnce()
            }
            queue.pendingCount()
        }.getOrNull()
        _state.update {
            it.copy(
                lastSync = when {
                    pending == null -> "PENDIENTE"
                    pending == 0 -> "SINCRONIZADO"
                    else -> "PENDIENTE ($pending)"
                },
            )
        }
        return payload
    }

    // UI entry for the ANOTAR button. Falls back to viewModelScope in
    // production; tests inject workScope or call commitAndSync directly.
    fun submit() {
        (workScope ?: viewModelScope).launch { commitAndSync() }
    }

    // CERRAR MESA pipeline for an occupied table: enqueues the CLOSE_TAB op
    // against the hub tab id and drains when online. True when nothing is
    // left pending; offline enqueues and reports false so the UI can show
    // PENDIENTE instead of pretending the table closed. Enqueue runs on its
    // own: a lost CLOSE_TAB op returns false so the UI keeps the table open
    // for retry instead of pretending it closed. Any later queue/drain
    // failure reports false instead of throwing (same rule as ANOTAR).
    suspend fun closeTabAndSync(tabId: String, tableId: String): Boolean {
        val queue = sync ?: return false
        val tab = TabPayload(id = tabId, tableId = tableId, lines = emptyList(), isClosed = true)
        val enqueued = runCatching {
            queue.enqueue(OpTypes.CLOSE_TAB, PendingQueue.encode(tab))
        }.isSuccess
        if (!enqueued) return false
        return runCatching {
            if (!queue.isOnline()) return@runCatching false
            queue.drainOnce()
            queue.pendingCount() == 0
        }.getOrDefault(false)
    }

    // UI entry for the CERRAR button. Falls back to viewModelScope in
    // production; tests call closeTabAndSync directly.
    fun closeTab(tabId: String, tableId: String, onDone: (Boolean) -> Unit = {}) {
        (workScope ?: viewModelScope).launch { onDone(closeTabAndSync(tabId, tableId)) }
    }

    // Hub catalog pull for the Tables screen entry (LaunchedEffect once).
    // No-op for the fake (previews/tests); when offline the last-loaded
    // cached state keeps serving and no request goes out. Order math below
    // is untouched: lines keep their qty, only prices re-resolve.
    suspend fun loadCatalog() {
        val remote = catalog as? ApiOrderCatalog ?: return
        if (sync?.isOnline() == false) return
        if (!remote.refresh()) return
        productById = remote.listProducts().associateBy { it.id }
        _state.update { current ->
            current.copy(
                tables = remote.listTables(),
                products = remote.listProducts(),
                runningTotalCents = totalOf(current.lines),
            )
        }
    }

    private fun totalOf(lines: Map<String, Int>): Long =
        lines.entries.sumOf { (productId, qty) ->
            (productById[productId]?.priceCents ?: 0L) * qty
        }
}
