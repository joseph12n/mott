package dev.mott.app

import dev.mott.app.domain.Product
import dev.mott.app.ui.computeTablesKpis
import dev.mott.app.ui.mittMoneyLabel
import dev.mott.app.ui.order.OrderUiState
import dev.mott.app.ui.order.TableRef
import org.junit.Assert.assertEquals
import org.junit.Test

class UiTest {

    private fun state() = OrderUiState(
        tables = listOf(
            TableRef("t1", "MESA 1", occupied = true),
            TableRef("t2", "MESA 2", occupied = true),
            TableRef("t3", "MESA 3", occupied = false),
        ),
        products = listOf(
            Product("p1", "Fernet", 1250, available = true),
            Product("p2", "Coca-Cola", 400, available = true),
            Product("p3", "Papas fritas", 700, available = false),
        ),
        lines = mapOf("p1" to 2),
        runningTotalCents = 2500L,
    )

    @Test
    fun kpis_countOpenTablesFromOccupancyFlags() {
        assertEquals(2, computeTablesKpis(state()).openCount)
    }

    @Test
    fun kpis_countAvailableProductsOnly() {
        assertEquals(2, computeTablesKpis(state()).availableCount)
    }

    @Test
    fun kpis_passDraftTotalThrough() {
        assertEquals(2500L, computeTablesKpis(state()).draftTotalCents)
    }

    @Test
    fun kpis_emptyStateIsAllZeros() {
        val kpis = computeTablesKpis(OrderUiState())
        assertEquals(0, kpis.openCount)
        assertEquals(0L, kpis.draftTotalCents)
        assertEquals(0, kpis.availableCount)
    }

    @Test
    fun kpis_freeTablesAreNotOpen() {
        val onlyFree = OrderUiState(
            tables = listOf(TableRef("t1", "MESA 1", occupied = false)),
        )
        assertEquals(0, computeTablesKpis(onlyFree).openCount)
    }

    @Test
    fun moneyLabel_reusesFormatterWithPrefix() {
        assertEquals("$ 12.50", mittMoneyLabel(1250L))
        assertEquals("$ 0.00", mittMoneyLabel(0L))
    }
}
