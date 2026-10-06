package dev.mott.app.data

import dev.mott.app.data.local.PendingOpDao
import dev.mott.app.data.local.PendingOpEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

// Offline operation types stored in PendingOpEntity.opType.
object OpTypes {
    const val UPSERT_PRODUCT = "UPSERT_PRODUCT"
    const val SAVE_TAB = "SAVE_TAB"
    const val CLOSE_TAB = "CLOSE_TAB"
    const val ADD_EXPENSE = "ADD_EXPENSE"
}

// Sync payload mirroring the catalogue product fields.
@Serializable
data class ProductPayload(
    val id: String,
    val name: String,
    val priceCents: Long,
    val available: Boolean,
)

// Sync payload mirroring one order line.
@Serializable
data class OrderLinePayload(
    val productId: String,
    val name: String,
    val unitPriceCents: Long,
    val qty: Int,
)

// Sync payload mirroring a tab with its lines.
@Serializable
data class TabPayload(
    val id: String,
    val tableId: String,
    val lines: List<OrderLinePayload>,
    val isClosed: Boolean,
)

// Sync payload mirroring a bar expense.
@Serializable
data class ExpensePayload(
    val id: String,
    val description: String,
    val qty: Double,
    val costCents: Long,
    val dateEpochMs: Long,
)

// Minimal queue contract the sync layer depends on. PendingQueue is the
// Room-backed implementation; tests substitute an in-memory fake so sync
// stays JVM-pure without a database.
interface OpQueue {
    suspend fun peekAll(): List<PendingOpEntity>

    suspend fun remove(id: Long)

    suspend fun bumpAttempts(id: Long)
}

// Thin FIFO queue over PendingOpDao. Callers encode payloads with
// encode() and decode them back after peeking.
class PendingQueue(
    private val dao: PendingOpDao,
    private val clock: () -> Long = System::currentTimeMillis,
) : OpQueue {
    suspend fun enqueue(type: String, payloadJson: String): Long {
        val op = PendingOpEntity(opType = type, payloadJson = payloadJson, createdAt = clock())
        return dao.enqueue(op)
    }

    override suspend fun peekAll(): List<PendingOpEntity> = dao.listPending()

    override suspend fun remove(id: Long) = dao.delete(id)

    override suspend fun bumpAttempts(id: Long) = dao.incrementAttempts(id)

    companion object {
        val json: Json = Json { ignoreUnknownKeys = true }

        inline fun <reified T> encode(value: T): String = json.encodeToString(serializer(), value)

        inline fun <reified T> decode(raw: String): T = json.decodeFromString(serializer(), raw)
    }
}
