package dev.mott.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

// Cached bar expense. Cost is in cents.
@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey val id: String,
    val description: String,
    val qty: Double,
    val costCents: Long,
    val dateEpochMs: Long,
)
