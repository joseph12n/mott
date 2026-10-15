package dev.mott.app.data.standalone

// Persistence seam under LocalMittApi (standalone mode: the phone is its own
// hub). Pure Kotlin, suspend, one simple operation per method with no
// cross-method transactions: a Room-backed implementation maps each method to
// one DAO call. Read-modify-write sequences are serialized by LocalMittApi, and
// a tab and its lines always travel together through saveTab. Times are epoch
// millis; the api layer owns every wire format.

data class StoredProduct(
    val id: String,
    val name: String,
    val priceCents: Long,
    val available: Boolean,
)

data class StoredTable(
    val id: String,
    val label: String,
)

// One order line with the unit price snapshotted when it was first ordered.
data class StoredLine(
    val productId: String,
    val name: String,
    val unitPriceCents: Long,
    val qty: Int,
)

data class StoredTab(
    val id: String,
    val tableId: String,
    val open: Boolean,
    val openedAt: Long,
    val lines: List<StoredLine> = emptyList(),
)

// Immutable snapshot of a closed tab; its id mirrors the tab id.
data class StoredSale(
    val id: String,
    val tableId: String,
    val lines: List<StoredLine>,
    val totalCents: Long,
    val closedAt: Long,
)

data class StoredExpense(
    val id: String,
    val description: String,
    val qty: Double,
    val costCents: Long,
    val date: Long,
)

data class StoredSupplier(
    val id: String,
    val name: String,
    val phone: String,
    val note: String,
)

// Singleton row. logoDataUrl keeps the validated "data:<mime>;base64,..." text
// so the store needs no blob handling; null means no logo.
data class StoredBranding(
    val shopName: String,
    val primary: String,
    val accent: String,
    val background: String,
    val logoDataUrl: String?,
    val updatedAt: Long,
)

interface LocalStore {
    // Products, any order (the api sorts by name).
    suspend fun listProducts(): List<StoredProduct>
    suspend fun getProduct(id: String): StoredProduct?
    suspend fun saveProduct(product: StoredProduct)

    // Tables, any order (the api sorts by label).
    suspend fun listTables(): List<StoredTable>
    suspend fun getTable(id: String): StoredTable?
    suspend fun saveTable(table: StoredTable)

    // Returns false when the id did not exist.
    suspend fun deleteTable(id: String): Boolean

    // Open tabs only, oldest first (openedAt ascending, insertion order on ties).
    suspend fun listOpenTabs(): List<StoredTab>
    suspend fun getOpenTabByTable(tableId: String): StoredTab?

    // Upsert by id, lines included; a closed tab (open = false) leaves the open list.
    suspend fun saveTab(tab: StoredTab)

    // Upsert by id (retrying a close must not duplicate the sale).
    suspend fun saveSale(sale: StoredSale)

    // Newest first (closedAt descending, later insertion first on ties).
    suspend fun listSales(limit: Int): List<StoredSale>

    // Sales with startInclusive <= closedAt < endExclusive (epoch millis).
    suspend fun listSalesBetween(startInclusive: Long, endExclusive: Long): List<StoredSale>

    // Oldest first (date ascending, insertion order on ties).
    suspend fun listExpenses(): List<StoredExpense>
    suspend fun addExpense(expense: StoredExpense)

    // Suppliers, any order (the api sorts by name).
    suspend fun listSuppliers(): List<StoredSupplier>
    suspend fun getSupplier(id: String): StoredSupplier?
    suspend fun saveSupplier(supplier: StoredSupplier)

    // Returns false when the id did not exist.
    suspend fun deleteSupplier(id: String): Boolean

    // null until the first successful patch; the api then serves defaults.
    suspend fun getBranding(): StoredBranding?
    suspend fun saveBranding(branding: StoredBranding)
}
