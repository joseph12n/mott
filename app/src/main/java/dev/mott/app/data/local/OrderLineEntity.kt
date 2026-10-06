package dev.mott.app.data.local

import androidx.room.Entity

// One cached line on a tab. Replaced wholesale on every tab save.
@Entity(tableName = "order_lines", primaryKeys = ["tabId", "productId"])
data class OrderLineEntity(
    val tabId: String,
    val productId: String,
    val name: String,
    val unitPriceCents: Long,
    val qty: Int,
)
