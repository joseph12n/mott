package dev.mott.app

import dev.mott.app.data.OpQueue
import dev.mott.app.data.local.PendingOpEntity
import dev.mott.app.ui.ConnectionKpis
import dev.mott.app.ui.LoadFailureReason
import dev.mott.app.ui.computeConnectionKpis
import dev.mott.app.ui.connectionCopyLabel
import dev.mott.app.ui.connectionPillText
import dev.mott.app.ui.connectionSteps
import dev.mott.app.ui.order.FakeOrderCatalog
import dev.mott.app.ui.order.OrderCatalog
import dev.mott.app.ui.pendingQueueDepth
import dev.mott.app.ui.showRePair
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// T9 Conexion parity: mobile-side KPI helpers, status/copy mapping and the
// token_invalido re-pair shortcut contract. JVM-pure, no Compose.
private class ConnectionFakeQueue(private val ops: List<PendingOpEntity>) : OpQueue {
    override suspend fun peekAll(): List<PendingOpEntity> = ops
    override suspend fun remove(id: Long) = Unit
    override suspend fun bumpAttempts(id: Long) = Unit
}

private fun op(type: String) = PendingOpEntity(opType = type, payloadJson = "{}", createdAt = 0L)

class ConnectionStatsTest {
    @Test
    fun kpisCountTablesAndProductsFromCatalog() {
        val kpis = computeConnectionKpis(FakeOrderCatalog(), pendingCount = 3)
        assertEquals(ConnectionKpis(tableCount = 6, productCount = 6, pendingCount = 3), kpis)
    }

    @Test
    fun kpisEmptyCatalogStaysZero() {
        val empty = object : OrderCatalog {
            override fun listTables() = emptyList<dev.mott.app.ui.order.TableRef>()
            override fun listProducts() = emptyList<dev.mott.app.domain.Product>()
        }
        assertEquals(ConnectionKpis(0, 0, 0), computeConnectionKpis(empty, pendingCount = 0))
    }

    @Test
    fun kpisUnknownPendingStaysNull() {
        val kpis = computeConnectionKpis(FakeOrderCatalog(), pendingCount = null)
        assertEquals(null, kpis.pendingCount)
    }

    @Test
    fun queueDepthMirrorsPendingOps() = runBlocking {
        val queue = ConnectionFakeQueue(listOf(op("SAVE_TAB"), op("CLOSE_TAB"), op("ADD_EXPENSE")))
        assertEquals(3, pendingQueueDepth(queue))
    }

    @Test
    fun queueDepthEmptyIsZero() = runBlocking {
        assertEquals(0, pendingQueueDepth(ConnectionFakeQueue(emptyList())))
    }

    @Test
    fun copyLabelTogglesOnFeedback() {
        assertEquals("Copiar dirección", connectionCopyLabel(copied = false))
        assertEquals("Copiado", connectionCopyLabel(copied = true))
    }

    @Test
    fun pillTextDefaultsToActiveServer() {
        assertEquals("Servidor activo", connectionPillText(null))
    }

    @Test
    fun pillTextNamesEachFailure() {
        assertEquals("Token inválido", connectionPillText(LoadFailureReason.TOKEN_INVALIDO))
        assertEquals("Sin servidor", connectionPillText(LoadFailureReason.SIN_SERVIDOR))
        assertEquals("Error inesperado", connectionPillText(LoadFailureReason.ERROR_INESPERADO))
    }

    @Test
    fun rePairShortcutOnlyOnTokenInvalido() {
        assertTrue(LoadFailureReason.TOKEN_INVALIDO.showRePair())
        assertFalse(LoadFailureReason.SIN_SERVIDOR.showRePair())
        assertFalse(LoadFailureReason.ERROR_INESPERADO.showRePair())
    }

    @Test
    fun guideHasThreeMobileSteps() {
        val steps = connectionSteps()
        assertEquals(3, steps.size)
        assertTrue(steps.all { it.isNotBlank() })
    }
}
