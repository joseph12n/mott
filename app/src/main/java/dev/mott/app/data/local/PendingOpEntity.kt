package dev.mott.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

// Offline operation waiting for sync. Consumed FIFO by autoId.
// opType is one of UPSERT_PRODUCT, SAVE_TAB, CLOSE_TAB, ADD_EXPENSE.
@Entity(tableName = "pending_ops")
data class PendingOpEntity(
    @PrimaryKey(autoGenerate = true) val autoId: Long = 0,
    val opType: String,
    val payloadJson: String,
    val createdAt: Long,
    val attempts: Int = 0,
)
