package dev.mott.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface PendingOpDao {
    @Insert
    suspend fun enqueue(op: PendingOpEntity): Long

    @Query("SELECT * FROM pending_ops ORDER BY autoId ASC")
    suspend fun listPending(): List<PendingOpEntity>

    @Query("DELETE FROM pending_ops WHERE autoId = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE pending_ops SET attempts = attempts + 1 WHERE autoId = :id")
    suspend fun incrementAttempts(id: Long)
}
