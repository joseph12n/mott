package dev.mott.app.data

import dev.mott.app.data.remote.MittApi
import dev.mott.app.data.remote.ProductDto
import dev.mott.app.data.remote.TableDto
import dev.mott.app.domain.Product
import dev.mott.app.ui.order.OrderCatalog
import dev.mott.app.ui.order.TableRef

// Hub-backed OrderCatalog: the mitt hub is the single source of truth for
// tables and products. Sync getters serve the last-loaded memory state
// (preloaded from the cache), while refresh() pulls fresh data; the
// ViewModel calls refresh() on Tables entry when online.
class ApiOrderCatalog(
    private val apiProvider: () -> MittApi?,
    private val cache: CatalogCache,
    preloadCache: Boolean = true,
) : OrderCatalog {
    private var tables: List<TableRef> = emptyList()
    private var products: List<Product> = emptyList()

    init {
        if (preloadCache) {
            cache.load()?.let { applySnapshot(it.tables, it.products) }
        }
    }

    override fun listTables(): List<TableRef> = tables

    override fun listProducts(): List<Product> = products

    // Pulls GET /api/tables plus GET /api/products with no available
    // filter, so unavailable products stay listed (visibly disabled, same
    // rule as web). True on fresh hub data; false when unpaired, offline,
    // or failing — the last-loaded (cached) state is kept untouched.
    suspend fun refresh(): Boolean {
        val api = apiProvider() ?: return false
        try {
            val tablesResponse = api.listTables()
            val productsResponse = api.listProducts()
            if (!tablesResponse.isSuccessful || !productsResponse.isSuccessful) return false
            val tableDtos = tablesResponse.body()?.tables ?: return false
            val productDtos = productsResponse.body()?.products ?: return false
            applySnapshot(tableDtos, productDtos)
            cache.save(tableDtos, productDtos)
            return true
        } catch (_: Exception) {
            return false
        }
    }

    private fun applySnapshot(tableDtos: List<TableDto>, productDtos: List<ProductDto>) {
        tables = tableDtos.map { TableRef(id = it.id, label = it.label, occupied = it.occupied) }
        products = productDtos.map {
            Product(id = it.id, name = it.name, priceCents = it.priceCents, available = it.available)
        }
    }
}
