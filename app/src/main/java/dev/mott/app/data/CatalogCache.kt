package dev.mott.app.data

import android.content.Context
import android.content.SharedPreferences
import dev.mott.app.data.remote.ProductDto
import dev.mott.app.data.remote.TableDto
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val catalogJson = Json { ignoreUnknownKeys = true }

// Last-known hub catalog snapshot (tables + products with availability).
// Plain prefs by design: Room owns the op queue; the catalog is a small
// replace-whole snapshot, so SharedPreferences JSON is enough.
@Serializable
data class CatalogSnapshot(
    val tables: List<TableDto> = emptyList(),
    val products: List<ProductDto> = emptyList(),
    val savedAt: Long = 0L,
)

class CatalogCache(
    private val prefs: SharedPreferences,
) {
    constructor(context: Context) : this(
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
    )

    fun save(
        tables: List<TableDto>,
        products: List<ProductDto>,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        prefs.edit()
            .putString(KEY_TABLES, catalogJson.encodeToString(ListSerializer(TableDto.serializer()), tables))
            .putString(KEY_PRODUCTS, catalogJson.encodeToString(ListSerializer(ProductDto.serializer()), products))
            .putLong(KEY_SAVED_AT, nowMs)
            .apply()
    }

    // Null when nothing was ever saved (or the payload no longer decodes),
    // so callers serve the empty catalog instead of crashing.
    fun load(): CatalogSnapshot? {
        val tablesRaw = prefs.getString(KEY_TABLES, null) ?: return null
        val productsRaw = prefs.getString(KEY_PRODUCTS, null) ?: return null
        return runCatching {
            CatalogSnapshot(
                tables = catalogJson.decodeFromString<List<TableDto>>(tablesRaw),
                products = catalogJson.decodeFromString<List<ProductDto>>(productsRaw),
                savedAt = prefs.getLong(KEY_SAVED_AT, 0L),
            )
        }.getOrNull()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        const val PREFS_NAME = "catalog"
        const val KEY_TABLES = "tables_json"
        const val KEY_PRODUCTS = "products_json"
        const val KEY_SAVED_AT = "saved_at"
    }
}
