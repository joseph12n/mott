package dev.mott.app

import android.content.SharedPreferences
import dev.mott.app.data.ApiOrderCatalog
import dev.mott.app.data.CatalogCache
import dev.mott.app.data.remote.ApiClient
import dev.mott.app.data.remote.ProductDto
import dev.mott.app.data.remote.TableDto
import dev.mott.app.ui.order.OrderViewModel
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

// HashMap-backed SharedPreferences fake: the interface has no Android
// framework calls behind it, so CatalogCache stays JVM-pure in tests.
class FakePrefs : SharedPreferences {
    private val values = mutableMapOf<String, Any?>()

    override fun getAll(): Map<String, *> = values.toMap()

    override fun getString(key: String, defValue: String?): String? =
        (values[key] as? String) ?: defValue

    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
        @Suppress("UNCHECKED_CAST") ((values[key] as? MutableSet<String>) ?: defValues)

    override fun getInt(key: String, defValue: Int): Int =
        (values[key] as? Int) ?: defValue

    override fun getLong(key: String, defValue: Long): Long =
        (values[key] as? Long) ?: defValue

    override fun getFloat(key: String, defValue: Float): Float =
        (values[key] as? Float) ?: defValue

    override fun getBoolean(key: String, defValue: Boolean): Boolean =
        (values[key] as? Boolean) ?: defValue

    override fun contains(key: String): Boolean = values.containsKey(key)

    override fun edit(): SharedPreferences.Editor = FakeEditor(values)

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) = Unit
}

class FakeEditor(
    private val values: MutableMap<String, Any?>,
) : SharedPreferences.Editor {
    override fun putString(key: String, value: String?): SharedPreferences.Editor {
        values[key] = value
        return this
    }

    override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor {
        this.values[key] = values
        return this
    }

    override fun putInt(key: String, value: Int): SharedPreferences.Editor {
        values[key] = value
        return this
    }

    override fun putLong(key: String, value: Long): SharedPreferences.Editor {
        values[key] = value
        return this
    }

    override fun putFloat(key: String, value: Float): SharedPreferences.Editor {
        values[key] = value
        return this
    }

    override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
        values[key] = value
        return this
    }

    override fun remove(key: String): SharedPreferences.Editor {
        values.remove(key)
        return this
    }

    override fun clear(): SharedPreferences.Editor {
        values.clear()
        return this
    }

    override fun commit(): Boolean = true

    override fun apply() = Unit
}

// H3: the hub is the single source of truth for tables + products, with a
// prefs snapshot covering offline. MockWebServer + fakes only.
class CatalogTest {
    private val server = MockWebServer()

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun json(code: Int, body: String) {
        server.enqueue(
            MockResponse().setResponseCode(code).setBody(body).addHeader("Content-Type", "application/json"),
        )
    }

    private fun catalog(
        prefs: SharedPreferences = FakePrefs(),
        api: dev.mott.app.data.remote.MittApi = ApiClient.build(server.url("/").toString(), "pair-token", logger = false),
    ) = ApiOrderCatalog(apiProvider = { api }, cache = CatalogCache(prefs))

    @Test
    fun `mitt wire tables map with occupied flags`() = runBlocking {
        json(200, MITT_WIRE_TABLES_JSON)
        json(200, MITT_WIRE_PRODUCTS_JSON)
        val remote = catalog()

        assertTrue(remote.refresh())

        val tables = remote.listTables()
        assertEquals(2, tables.size)
        assertEquals("t1", tables[0].id)
        assertEquals("MESA 1", tables[0].label)
        assertFalse(tables[0].occupied)
        assertEquals("t2", tables[1].id)
        assertTrue(tables[1].occupied)

        val tableRequest = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("/api/tables", tableRequest.path)
        assertEquals("Bearer pair-token", tableRequest.getHeader("Authorization"))
        // Products pull asks for everything: no available filter, so
        // unavailable rows stay listed (visibly disabled, same rule as web).
        val productsRequest = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("/api/products", productsRequest.path)
    }

    @Test
    fun `unavailable product stays listed and flagged`() = runBlocking {
        json(200, MITT_WIRE_TABLES_JSON)
        json(200, MITT_WIRE_PRODUCTS_JSON)
        val remote = catalog()
        assertTrue(remote.refresh())

        val products = remote.listProducts()
        assertEquals(listOf("p1", "p6"), products.map { it.id })
        assertTrue(products.single { it.id == "p1" }.available)
        assertFalse(products.single { it.id == "p6" }.available)

        val vm = OrderViewModel(remote)
        vm.selectTable("t1")
        vm.toggleLine("p6")
        assertTrue(vm.state.value.lines.isEmpty())
        assertNotNull(vm.state.value.error)
        vm.increment("p1")
        assertEquals(mapOf("p1" to 1), vm.state.value.lines)
    }

    @Test
    fun `cache save load roundtrip keeps snapshot`() {
        val prefs = FakePrefs()
        val cache = CatalogCache(prefs)
        assertNull(cache.load())

        val tables = listOf(TableDto("t1", "MESA 1", occupied = false), TableDto("t2", "MESA 2", occupied = true))
        val products = listOf(
            ProductDto("p1", "Fernet", priceCents = 1250, available = true),
            ProductDto("p6", "Papas fritas", priceCents = 700, available = false),
        )
        cache.save(tables, products, nowMs = 42L)

        val snapshot = cache.load()
        assertNotNull(snapshot)
        assertEquals(tables, snapshot!!.tables)
        assertEquals(products, snapshot.products)
        assertEquals(42L, snapshot.savedAt)
    }

    @Test
    fun `cache load returns null when empty or corrupt`() {
        assertNull(CatalogCache(FakePrefs()).load())

        val prefs = FakePrefs()
        prefs.edit().putString(CatalogCache.KEY_TABLES, "{not json").putString(CatalogCache.KEY_PRODUCTS, "[]").apply()
        assertNull(CatalogCache(prefs).load())
    }

    @Test
    fun `offline fallback serves the cached snapshot without http`() = runBlocking {
        val prefs = FakePrefs()
        CatalogCache(prefs).save(
            tables = listOf(TableDto("t9", "TERRAZA", occupied = true)),
            products = listOf(ProductDto("p1", "Fernet", priceCents = 1250, available = true)),
        )
        val vm = OrderViewModel(catalog(prefs), sync = RecordingOrderSync(online = false))

        // Preloaded from cache at construction, no hub call while offline.
        assertEquals(listOf("TERRAZA"), vm.state.value.tables.map { it.label })
        vm.loadCatalog()

        assertEquals(0, server.requestCount)
        assertEquals(listOf("TERRAZA"), vm.state.value.tables.map { it.label })
        assertTrue(vm.state.value.tables.single().occupied)
        assertEquals(listOf("p1"), vm.state.value.products.map { it.id })
    }

    @Test
    fun `failed refresh keeps the cached state`() = runBlocking {
        val prefs = FakePrefs()
        CatalogCache(prefs).save(
            tables = listOf(TableDto("t9", "TERRAZA", occupied = false)),
            products = listOf(ProductDto("p1", "Fernet", priceCents = 1250, available = true)),
        )
        json(500, """{"error":{"code":"boom","message":"try later"}}""")
        json(500, """{"error":{"code":"boom","message":"try later"}}""")
        val remote = catalog(prefs)

        assertFalse(remote.refresh())

        assertEquals(listOf("TERRAZA"), remote.listTables().map { it.label })
        assertEquals(listOf("p1"), remote.listProducts().map { it.id })
    }

    @Test
    fun `online loadCatalog publishes fresh hub data`() = runBlocking {
        json(200, MITT_WIRE_TABLES_JSON)
        json(200, MITT_WIRE_PRODUCTS_JSON)
        val vm = OrderViewModel(catalog(), sync = RecordingOrderSync(online = true))
        assertTrue(vm.state.value.tables.isEmpty())

        vm.loadCatalog()

        assertEquals(listOf("MESA 1", "MESA 2"), vm.state.value.tables.map { it.label })
        assertEquals(listOf("p1", "p6"), vm.state.value.products.map { it.id })
    }

    @Test
    fun `no pairing means refresh fails closed on empty state`() = runBlocking {
        val remote = ApiOrderCatalog(apiProvider = { null }, cache = CatalogCache(FakePrefs()))

        assertFalse(remote.refresh())

        assertTrue(remote.listTables().isEmpty())
        assertTrue(remote.listProducts().isEmpty())
    }

    @Test
    fun `default catalog keeps presets for previews and tests`() {
        val vm = OrderViewModel()

        assertEquals(
            listOf("MESA 1", "MESA 2", "MESA 3", "MESA 4", "MESA 5", "BARRA"),
            vm.state.value.tables.map { it.label },
        )
        assertTrue(vm.state.value.products.any { !it.available })
    }

    companion object {
        // Realistic GET /api/tables body (mitt wire, DUMMY content).
        const val MITT_WIRE_TABLES_JSON =
            """{"tables":[{"id":"t1","label":"MESA 1","occupied":false},{"id":"t2","label":"MESA 2","occupied":true}]}"""
        // Realistic GET /api/products body, unfiltered (mitt wire, DUMMY content).
        const val MITT_WIRE_PRODUCTS_JSON =
            """{"products":[{"id":"p1","name":"Fernet","price_cents":1250,"available":true},{"id":"p6","name":"Papas fritas","price_cents":700,"available":false}]}"""
    }
}
