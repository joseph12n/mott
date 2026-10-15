package dev.mott.app.data.standalone

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// Thread-safe in-memory LocalStore for JVM tests. Insertion-ordered maps give
// the tie-break order the interface documents.
class InMemoryLocalStore : LocalStore {
    private val lock = Mutex()
    private val products = LinkedHashMap<String, StoredProduct>()
    private val tables = LinkedHashMap<String, StoredTable>()
    private val tabs = LinkedHashMap<String, StoredTab>()
    private val sales = LinkedHashMap<String, StoredSale>()
    private val expenses = ArrayList<StoredExpense>()
    private val suppliers = LinkedHashMap<String, StoredSupplier>()
    private var branding: StoredBranding? = null

    override suspend fun listProducts() = lock.withLock { products.values.toList() }
    override suspend fun getProduct(id: String) = lock.withLock { products[id] }
    override suspend fun saveProduct(product: StoredProduct) = lock.withLock { products[product.id] = product }

    override suspend fun listTables() = lock.withLock { tables.values.toList() }
    override suspend fun getTable(id: String) = lock.withLock { tables[id] }
    override suspend fun saveTable(table: StoredTable) = lock.withLock { tables[table.id] = table }
    override suspend fun deleteTable(id: String) = lock.withLock { tables.remove(id) != null }

    override suspend fun listOpenTabs() = lock.withLock {
        tabs.values.filter { it.open }.sortedBy { it.openedAt }
    }

    override suspend fun getOpenTabByTable(tableId: String) = lock.withLock {
        tabs.values.filter { it.open && it.tableId == tableId }.maxByOrNull { it.openedAt }
    }

    override suspend fun saveTab(tab: StoredTab) = lock.withLock { tabs[tab.id] = tab }

    override suspend fun saveSale(sale: StoredSale) = lock.withLock { sales[sale.id] = sale }

    override suspend fun listSales(limit: Int) = lock.withLock {
        sales.values.reversed().sortedByDescending { it.closedAt }.take(limit)
    }

    override suspend fun listSalesBetween(startInclusive: Long, endExclusive: Long) = lock.withLock {
        sales.values.filter { it.closedAt >= startInclusive && it.closedAt < endExclusive }
    }

    override suspend fun listExpenses() = lock.withLock { expenses.sortedBy { it.date } }
    override suspend fun addExpense(expense: StoredExpense) = lock.withLock { expenses.add(expense); Unit }

    override suspend fun listSuppliers() = lock.withLock { suppliers.values.toList() }
    override suspend fun getSupplier(id: String) = lock.withLock { suppliers[id] }
    override suspend fun saveSupplier(supplier: StoredSupplier) = lock.withLock { suppliers[supplier.id] = supplier }
    override suspend fun deleteSupplier(id: String) = lock.withLock { suppliers.remove(id) != null }

    override suspend fun getBranding() = lock.withLock { branding }
    override suspend fun saveBranding(branding: StoredBranding) = lock.withLock { this.branding = branding }
}
