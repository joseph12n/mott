package dev.mott.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

// Cached catalogue product. Prices are in cents.
@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey val id: String,
    val name: String,
    val priceCents: Long,
    val available: Boolean,
    val updatedAt: Long,
)
