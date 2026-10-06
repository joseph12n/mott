package dev.mott.app.data

import dev.mott.app.data.local.PendingOpEntity
import dev.mott.app.data.remote.AddItemRequest
import dev.mott.app.data.remote.ExpenseCreateRequest
import dev.mott.app.data.remote.MittApi
import dev.mott.app.data.remote.OpenTabRequest
import dev.mott.app.data.remote.ProductCreateRequest
import java.io.IOException

// Outcome of one drain pass over the pending queue.
data class SyncReport(
    val synced: Int = 0,
    val dropped: Int = 0,
    val deferred: Int = 0,
    val exhausted: Int = 0,
)

// FIFO drain of the offline op queue against the mitt hub.
//
// Per op, oldest first:
// - 2xx -> the op is done, removed from the queue.
// - 4xx -> the payload is poison (domain rejected, never retryable),
//   removed and counted as dropped so one bad op never wedges the queue.
// - 5xx or IO failure -> attempts are bumped and the drain stops at that
//   op, keeping order: later ops stay queued for the next pass.
// - attempts reaching maxAttempts -> the op is left in place and reported
//   as exhausted. Ops already exhausted at drain start are skipped without
//   blocking later ones (dead-letter skip), so a dead op cannot wedge the
//   queue forever.
class SyncManager(
    private val queue: OpQueue,
    private val api: MittApi,
    private val maxAttempts: Int = 5,
) {
    suspend fun drainOnce(): SyncReport {
        var synced = 0
        var dropped = 0
        var deferred = 0
        var exhausted = 0

        val pending = queue.peekAll()
        for (index in pending.indices) {
            val op = pending[index]
            if (op.attempts >= maxAttempts) {
                exhausted++
                continue
            }
            when (send(op)) {
                Disposition.Done -> {
                    queue.remove(op.autoId)
                    synced++
                }
                Disposition.Poison -> {
                    queue.remove(op.autoId)
                    dropped++
                }
                Disposition.Retry -> {
                    queue.bumpAttempts(op.autoId)
                    if (op.attempts + 1 >= maxAttempts) exhausted++ else deferred++
                    // Stop at the first retryable failure; everything after
                    // stays queued in order for the next pass.
                    for (rest in pending.drop(index + 1)) {
                        if (rest.attempts >= maxAttempts) exhausted++ else deferred++
                    }
                    break
                }
            }
        }
        return SyncReport(synced = synced, dropped = dropped, deferred = deferred, exhausted = exhausted)
    }

    private enum class Disposition {
        Done,
        Poison,
        Retry,
    }

    private suspend fun send(op: PendingOpEntity): Disposition {
        return try {
            val code = dispatch(op) ?: return Disposition.Poison
            when {
                code in 200..299 -> Disposition.Done
                code in 400..499 -> Disposition.Poison
                else -> Disposition.Retry
            }
        } catch (_: IOException) {
            Disposition.Retry
        }
    }

    // Runs the endpoint call(s) for one op. Returns the HTTP status of the
    // decisive call, or null when the op type or payload is unusable
    // (poison: drop it instead of retrying forever).
    private suspend fun dispatch(op: PendingOpEntity): Int? {
        return when (op.opType) {
            OpTypes.UPSERT_PRODUCT -> {
                val payload = runCatching { PendingQueue.decode<ProductPayload>(op.payloadJson) }.getOrNull()
                    ?: return null
                api.createProduct(
                    ProductCreateRequest(
                        name = payload.name,
                        priceCents = payload.priceCents,
                        available = payload.available,
                    ),
                ).code()
            }
            OpTypes.SAVE_TAB -> {
                val payload = runCatching { PendingQueue.decode<TabPayload>(op.payloadJson) }.getOrNull()
                    ?: return null
                val opened = api.openTab(OpenTabRequest(tableId = payload.tableId))
                if (!opened.isSuccessful) return opened.code()
                val tabId = opened.body()?.id ?: payload.id
                for (line in payload.lines) {
                    val added = api.addItem(tabId, AddItemRequest(productId = line.productId, qty = line.qty))
                    if (!added.isSuccessful) return added.code()
                }
                opened.code()
            }
            OpTypes.CLOSE_TAB -> {
                val payload = runCatching { PendingQueue.decode<TabPayload>(op.payloadJson) }.getOrNull()
                    ?: return null
                api.closeTab(payload.id).code()
            }
            OpTypes.ADD_EXPENSE -> {
                val payload = runCatching { PendingQueue.decode<ExpensePayload>(op.payloadJson) }.getOrNull()
                    ?: return null
                api.createExpense(
                    ExpenseCreateRequest(
                        description = payload.description,
                        qty = payload.qty,
                        costCents = payload.costCents,
                    ),
                ).code()
            }
            else -> null
        }
    }
}
