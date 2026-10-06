package dev.mott.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        ProductEntity::class,
        TabEntity::class,
        OrderLineEntity::class,
        ExpenseEntity::class,
        PendingOpEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class MottDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao

    abstract fun tabDao(): TabDao

    abstract fun expenseDao(): ExpenseDao

    abstract fun pendingOpDao(): PendingOpDao
}
