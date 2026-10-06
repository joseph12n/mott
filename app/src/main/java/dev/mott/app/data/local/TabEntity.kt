package dev.mott.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

// Cached tab header. Lines live in OrderLineEntity keyed by tabId.
@Entity(tableName = "tabs")
data class TabEntity(
    @PrimaryKey val id: String,
    val tableId: String,
    val isClosed: Boolean,
    val openedAt: Long,
    val updatedAt: Long,
)
