package dev.mott.app

import dev.mott.app.data.standalone.JsonFileLocalStore
import dev.mott.app.data.standalone.LocalMittApi
import dev.mott.app.data.standalone.StoredBranding
import dev.mott.app.data.standalone.StoredExpense
import dev.mott.app.data.standalone.StoredLine
import dev.mott.app.data.standalone.StoredProduct
import dev.mott.app.data.standalone.StoredSale
import dev.mott.app.data.standalone.StoredSupplier
import dev.mott.app.data.standalone.StoredTab
import dev.mott.app.data.standalone.StoredTable
import dev.mott.app.data.remote.AddItemRequest
import dev.mott.app.data.remote.OpenTabRequest
import dev.mott.app.data.remote.ProductCreateRequest
import dev.mott.app.data.remote.TableCreateRequest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

// T6a: the phone's own database on the filesystem. Whole-state round trips,
// crash-tolerant loading and the append-only sales ledger.
class JsonFileLocalStoreTest {

    private fun tempDir(): File =
        Files.createTempDirectory("mott-standalone").toFile().also { it.deleteOnExit() }

    private fun deleteRecursively(file: File) {
        file.listFiles()?.forEach { deleteRecursively(it) }
        file.delete()
    }

    @Test
    fun `round trips every entity across a fresh instance`() = runBlocking {
        val dir = tempDir()
        try {
            val store = JsonFileLocalStore(dir)
            store.saveProduct(StoredProduct("p1", "Fernet", 1500, available = true))
            store.saveProduct(StoredProduct("p2", "Papas", 700, available = false))
            store.saveTable(StoredTable("t1", "Mesa 1"))
            store.saveTab(
                StoredTab(
                    id = "tab1",
                    tableId = "t1",
                    open = true,
                    openedAt = 100L,
                    lines = listOf(StoredLine("p1", "Fernet", 1500, 2)),
                ),
            )
            store.addExpense(StoredExpense("e1", "Hielo", 2.5, 900, 50L))
            store.saveSupplier(StoredSupplier("s1", "Distribuidora", "555", "nota"))
            store.saveBranding(StoredBranding("Bar", "#0E7A5A", "#F2A33A", "#EBF0E9", "data:image/png;base64,AAA", 7L))
            store.saveSale(StoredSale("sale1", "t1", listOf(StoredLine("p1", "Fernet", 1500, 2)), 3000, 200L))

            val reopened = JsonFileLocalStore(dir)
            assertEquals(listOf("p1", "p2"), reopened.listProducts().map { it.id })
            assertFalse(reopened.getProduct("p2")!!.available)
            assertEquals("Mesa 1", reopened.getTable("t1")!!.label)
            val tab = reopened.getOpenTabByTable("t1")
            assertNotNull(tab)
            assertEquals(listOf(StoredLine("p1", "Fernet", 1500, 2)), tab!!.lines)
            assertEquals(1, reopened.listOpenTabs().size)
            assertEquals(listOf("e1"), reopened.listExpenses().map { it.id })
            assertEquals(2.5, reopened.listExpenses().first().qty, 0.0)
            assertEquals("Distribuidora", reopened.getSupplier("s1")!!.name)
            assertEquals("Bar", reopened.getBranding()!!.shopName)
            assertEquals("data:image/png;base64,AAA", reopened.getBranding()!!.logoDataUrl)
            assertEquals(listOf("sale1"), reopened.listSales(10).map { it.id })
        } finally {
            deleteRecursively(dir)
        }
    }

    @Test
    fun `atomic write leaves no temp file behind`() = runBlocking {
        val dir = tempDir()
        try {
            val store = JsonFileLocalStore(dir)
            store.saveProduct(StoredProduct("p1", "Fernet", 1500, available = true))
            assertTrue(File(dir, JsonFileLocalStore.STATE_FILE).isFile)
            assertFalse(File(dir, JsonFileLocalStore.STATE_FILE + JsonFileLocalStore.TMP_SUFFIX).exists())
        } finally {
            deleteRecursively(dir)
        }
    }

    @Test
    fun `corrupt state parks the file and starts empty`() = runBlocking {
        val dir = tempDir()
        try {
            File(dir, JsonFileLocalStore.STATE_FILE).writeText("{ this is not json")
            val store = JsonFileLocalStore(dir)
            assertEquals(emptyList<StoredProduct>(), store.listProducts())
            assertNull(store.getBranding())
            assertTrue(File(dir, JsonFileLocalStore.STATE_FILE + JsonFileLocalStore.CORRUPT_SUFFIX).isFile)
        } finally {
            deleteRecursively(dir)
        }
    }

    @Test
    fun `truncated trailing sales line is ignored`() = runBlocking {
        val dir = tempDir()
        try {
            val store = JsonFileLocalStore(dir)
            store.saveSale(StoredSale("sale1", "t1", emptyList(), 100, 1L))
            store.saveSale(StoredSale("sale2", "t2", emptyList(), 200, 2L))
            val ledger = File(dir, JsonFileLocalStore.SALES_FILE)
            val lines = ledger.readLines()
            assertEquals(2, lines.size)
            // A crash during append leaves half a line.
            ledger.writeText(lines[0] + "\n" + lines[1].take(lines[1].length / 2))

            val reopened = JsonFileLocalStore(dir)
            assertEquals(listOf("sale1"), reopened.listSales(10).map { it.id })
        } finally {
            deleteRecursively(dir)
        }
    }

    @Test
    fun `sale upsert by id does not duplicate the row`() = runBlocking {
        val dir = tempDir()
        try {
            val store = JsonFileLocalStore(dir)
            store.saveSale(StoredSale("sale1", "t1", emptyList(), 100, 1L))
            store.saveSale(StoredSale("sale2", "t2", emptyList(), 200, 2L))
            // Retried close: same id, same payload.
            store.saveSale(StoredSale("sale1", "t1", emptyList(), 100, 1L))

            val reopened = JsonFileLocalStore(dir)
            assertEquals(listOf("sale2", "sale1"), reopened.listSales(10).map { it.id })
            assertEquals(2, File(dir, JsonFileLocalStore.SALES_FILE).readLines().filter { it.isNotBlank() }.size)
        } finally {
            deleteRecursively(dir)
        }
    }

    @Test
    fun `five hundred appends reload newest first`() = runBlocking {
        val dir = tempDir()
        try {
            val store = JsonFileLocalStore(dir)
            for (i in 1..500) {
                store.saveSale(StoredSale("sale$i", "t$i", emptyList(), i.toLong(), i.toLong()))
            }

            val reopened = JsonFileLocalStore(dir)
            val sales = reopened.listSales(500)
            assertEquals(500, sales.size)
            assertEquals("sale500", sales.first().id)
            assertEquals("sale1", sales.last().id)
            assertEquals(25, reopened.listSales(25).size)
        } finally {
            deleteRecursively(dir)
        }
    }

    @Test
    fun `api over the file store survives a restart`() = runBlocking {
        val dir = tempDir()
        try {
            val first = LocalMittApi(JsonFileLocalStore(dir))
            val table = first.createTable(TableCreateRequest("Mesa 1")).body()!!
            val product = first.createProduct(ProductCreateRequest("Fernet", 1500)).body()!!
            val tab = first.openTab(OpenTabRequest(table.id)).body()!!
            first.addItem(tab.id, AddItemRequest(product.id, 2))

            first.closeTab(tab.id)

            val second = LocalMittApi(JsonFileLocalStore(dir))
            val sales = second.listSales(null).body()!!.sales
            assertEquals(1, sales.size)
            assertEquals(3000L, sales.first().totalCents)
            assertEquals(false, second.listTables().body()!!.tables.first().occupied)
            assertEquals(1, second.listProducts().body()!!.products.size)
            assertEquals(3000L, second.salesToday().body()!!.totalCents)
        } finally {
            deleteRecursively(dir)
        }
    }
}
