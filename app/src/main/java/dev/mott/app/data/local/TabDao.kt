package dev.mott.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert

@Dao
abstract class TabDao {
    @Upsert
    abstract suspend fun upsertTab(tab: TabEntity)

    @Query("DELETE FROM order_lines WHERE tabId = :tabId")
    abstract suspend fun deleteLines(tabId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertLines(lines: List<OrderLineEntity>)

    // Saves the tab header and replaces its lines atomically.
    @Transaction
    open suspend fun upsertWithLines(tab: TabEntity, lines: List<OrderLineEntity>) {
        upsertTab(tab)
        deleteLines(tab.id)
        if (lines.isNotEmpty()) {
            insertLines(lines)
        }
    }

    @Query("SELECT * FROM tabs WHERE tableId = :tableId AND isClosed = 0 LIMIT 1")
    abstract suspend fun getOpenByTable(tableId: String): TabEntity?

    @Query("SELECT * FROM tabs WHERE isClosed = 0 ORDER BY openedAt")
    abstract suspend fun listOpen(): List<TabEntity>

    @Query("SELECT * FROM order_lines WHERE tabId = :tabId")
    abstract suspend fun linesForTab(tabId: String): List<OrderLineEntity>
}
