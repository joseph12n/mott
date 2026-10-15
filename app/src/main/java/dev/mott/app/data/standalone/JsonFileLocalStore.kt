package dev.mott.app.data.standalone

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

// File-backed LocalStore for standalone mode: the phone's own database with
// no Room schema to migrate and no Android framework to stub, so the whole
// persistence path is testable on the JVM.
//
// Layout under `dir`:
//   state.json   — products, tables, open tabs (with lines), expenses,
//                  suppliers and branding, rewritten wholesale.
//   sales.jsonl  — one closed sale per line, append-only (the sales ledger
//                  grows for the life of the install, so it never gets
//                  rewritten just to add a row).
//
// Durability rules: every mutation updates memory first, then writes state
// atomically (temp file + rename). A crash mid-write therefore leaves the
// previous good state.json plus a stale .tmp, which the loader prefers when
// state.json is missing. Unreadable JSON never crashes the app: the file is
// kept aside as *.corrupt and the store starts empty. Reads never touch the
// disk after the single load; a truncated trailing line in sales.jsonl
// (crash during append) is skipped.
//
// Closed tabs are not persisted as tabs: a closed tab lives on as its sale
// (same id), and the app only ever reads OPEN tabs, so the tab table would
// be dead weight.
//
// Note for a future Room backend: saveTab must stay atomic with its lines,
// and one LocalMittApi instance owns one store (its own Mutex serializes
// read-modify-write sequences).
class JsonFileLocalStore(
    private val dir: File,
) : LocalStore {
    private val memory = InMemoryLocalStore()
    private val mutex = Mutex()

    @Volatile
    private var loaded = false

    // Sales keep their append order so the ledger can be rewritten without
    // reshuffling it (newest-first ordering lives in the memory store).
    private val saleOrder = ArrayList<String>()
    private val salesById = HashMap<String, StoredSale>()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private fun stateFile() = File(dir, STATE_FILE)

    private suspend fun ensureLoaded() {
        if (loaded) return
        mutex.withLock {
            if (loaded) return
            loadIntoMemory()
            loaded = true
        }
    }

    private suspend fun loadIntoMemory() {
        runCatching { dir.mkdirs() }
        val state = readState()
        for (product in state.products) memory.saveProduct(product)
        for (table in state.tables) memory.saveTable(table)
        for (tab in state.tabs) memory.saveTab(tab)
        for (expense in state.expenses) memory.addExpense(expense)
        for (supplier in state.suppliers) memory.saveSupplier(supplier)
        state.branding?.let { memory.saveBranding(it) }
        for (sale in readSales()) {
            memory.saveSale(sale)
            if (!salesById.containsKey(sale.id)) saleOrder += sale.id
            salesById[sale.id] = sale
        }
    }

    // The state file, or the temp file a crashed write left behind, or an
    // empty state. A decode failure parks the bad file as *.corrupt.
    private fun readState(): StateFile {
        val target = stateFile()
        val tmp = tmpFor(target)
        val file = when {
            target.isFile -> target
            tmp.isFile -> tmp
            else -> return StateFile()
        }
        val text = runCatching { file.readText() }.getOrNull() ?: return StateFile()
        return runCatching { json.decodeFromString(StateFile.serializer(), text) }.getOrElse {
            runCatching { file.renameTo(File(dir, file.name + CORRUPT_SUFFIX)) }
            StateFile()
        }
    }

    private fun readSales(): List<StoredSale> {
        val file = File(dir, SALES_FILE)
        if (!file.isFile) return emptyList()
        val out = ArrayList<StoredSale>()
        runCatching {
            file.forEachLine { line ->
                if (line.isBlank()) return@forEachLine
                runCatching { json.decodeFromString(StoredSale.serializer(), line) }
                    .getOrNull()
                    ?.let { out += it }
            }
        }
        return out
    }

    private suspend fun persistState() {
        val state = StateFile(
            products = memory.listProducts(),
            tables = memory.listTables(),
            tabs = memory.listOpenTabs(),
            expenses = memory.listExpenses(),
            suppliers = memory.listSuppliers(),
            branding = memory.getBranding(),
        )
        writeAtomically(stateFile(), json.encodeToString(StateFile.serializer(), state))
    }

    private suspend fun persistSale(sale: StoredSale) {
        val known = salesById.containsKey(sale.id)
        salesById[sale.id] = sale
        if (!known) {
            saleOrder += sale.id
            // Append-only fast path; a retried close with the same id takes
            // the rewrite branch below instead of duplicating the row.
            runCatching {
                File(dir, SALES_FILE)
                    .appendText(json.encodeToString(StoredSale.serializer(), sale) + "\n")
            }
        } else {
            val text = buildString {
                for (id in saleOrder) {
                    val row = salesById[id] ?: continue
                    append(json.encodeToString(StoredSale.serializer(), row))
                    append('\n')
                }
            }
            writeAtomically(File(dir, SALES_FILE), text)
        }
    }

    private fun tmpFor(target: File) = File(dir, target.name + TMP_SUFFIX)

    // Temp file + atomic rename: a reader (or a crash) never sees a partial
    // file, and the temp is gone once the rename lands.
    private fun writeAtomically(target: File, text: String) {
        runCatching { dir.mkdirs() }
        val tmp = tmpFor(target)
        tmp.writeText(text)
        try {
            Files.move(
                tmp.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: Exception) {
            // Filesystem without atomic move support: keep the rename, just
            // without the atomicity guarantee.
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    override suspend fun listProducts(): List<StoredProduct> {
        ensureLoaded()
        return memory.listProducts()
    }

    override suspend fun getProduct(id: String): StoredProduct? {
        ensureLoaded()
        return memory.getProduct(id)
    }

    override suspend fun saveProduct(product: StoredProduct) {
        ensureLoaded()
        mutex.withLock {
            memory.saveProduct(product)
            persistState()
        }
    }

    override suspend fun listTables(): List<StoredTable> {
        ensureLoaded()
        return memory.listTables()
    }

    override suspend fun getTable(id: String): StoredTable? {
        ensureLoaded()
        return memory.getTable(id)
    }

    override suspend fun saveTable(table: StoredTable) {
        ensureLoaded()
        mutex.withLock {
            memory.saveTable(table)
            persistState()
        }
    }

    override suspend fun deleteTable(id: String): Boolean {
        ensureLoaded()
        return mutex.withLock {
            val deleted = memory.deleteTable(id)
            if (deleted) persistState()
            deleted
        }
    }

    override suspend fun listOpenTabs(): List<StoredTab> {
        ensureLoaded()
        return memory.listOpenTabs()
    }

    override suspend fun getOpenTabByTable(tableId: String): StoredTab? {
        ensureLoaded()
        return memory.getOpenTabByTable(tableId)
    }

    override suspend fun saveTab(tab: StoredTab) {
        ensureLoaded()
        mutex.withLock {
            memory.saveTab(tab)
            persistState()
        }
    }

    override suspend fun saveSale(sale: StoredSale) {
        ensureLoaded()
        mutex.withLock {
            memory.saveSale(sale)
            persistSale(sale)
        }
    }

    override suspend fun listSales(limit: Int): List<StoredSale> {
        ensureLoaded()
        return memory.listSales(limit)
    }

    override suspend fun listSalesBetween(startInclusive: Long, endExclusive: Long): List<StoredSale> {
        ensureLoaded()
        return memory.listSalesBetween(startInclusive, endExclusive)
    }

    override suspend fun listExpenses(): List<StoredExpense> {
        ensureLoaded()
        return memory.listExpenses()
    }

    override suspend fun addExpense(expense: StoredExpense) {
        ensureLoaded()
        mutex.withLock {
            memory.addExpense(expense)
            persistState()
        }
    }

    override suspend fun listSuppliers(): List<StoredSupplier> {
        ensureLoaded()
        return memory.listSuppliers()
    }

    override suspend fun getSupplier(id: String): StoredSupplier? {
        ensureLoaded()
        return memory.getSupplier(id)
    }

    override suspend fun saveSupplier(supplier: StoredSupplier) {
        ensureLoaded()
        mutex.withLock {
            memory.saveSupplier(supplier)
            persistState()
        }
    }

    override suspend fun deleteSupplier(id: String): Boolean {
        ensureLoaded()
        return mutex.withLock {
            val deleted = memory.deleteSupplier(id)
            if (deleted) persistState()
            deleted
        }
    }

    override suspend fun getBranding(): StoredBranding? {
        ensureLoaded()
        return memory.getBranding()
    }

    override suspend fun saveBranding(branding: StoredBranding) {
        ensureLoaded()
        mutex.withLock {
            memory.saveBranding(branding)
            persistState()
        }
    }

    companion object {
        const val STATE_FILE = "state.json"
        const val SALES_FILE = "sales.jsonl"
        const val TMP_SUFFIX = ".tmp"
        const val CORRUPT_SUFFIX = ".corrupt"
    }
}

// Everything except the sales ledger, which has its own append-only file.
@Serializable
private data class StateFile(
    val products: List<StoredProduct> = emptyList(),
    val tables: List<StoredTable> = emptyList(),
    val tabs: List<StoredTab> = emptyList(),
    val expenses: List<StoredExpense> = emptyList(),
    val suppliers: List<StoredSupplier> = emptyList(),
    val branding: StoredBranding? = null,
)
