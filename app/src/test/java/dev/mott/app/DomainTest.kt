package dev.mott.app

import dev.mott.app.domain.Expense
import dev.mott.app.domain.OrderLine
import dev.mott.app.domain.Product
import dev.mott.app.domain.Tab
import dev.mott.app.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DomainTest {

    // --- Product validation ---

    @Test
    fun product_validPasses() {
        Product("p1", "Fernet", 1250, true).validate()
    }

    @Test
    fun product_validationFailures() {
        val cases = listOf(
            "blank name" to Product("p1", "   ", 100, true),
            "empty name" to Product("p1", "", 100, true),
            "negative price" to Product("p1", "Fernet", -1, true),
        )
        for ((name, product) in cases) {
            assertThrows("product case: $name", IllegalArgumentException::class.java) {
                product.validate()
            }
        }
    }

    // --- OrderLine validation + totals ---

    @Test
    fun orderLine_lineTotalMultipliesPriceByQty() {
        val cases = listOf(
            Triple(1250L, 2, 2500L),
            Triple(5L, 3, 15L),
            Triple(0L, 7, 0L),
        )
        for ((price, qty, expected) in cases) {
            assertEquals(
                "lineTotal $price x $qty",
                expected,
                OrderLine("p1", "Fernet", price, qty).lineTotal,
            )
        }
    }

    @Test
    fun orderLine_validationFailures() {
        val cases = listOf(
            "zero qty" to OrderLine("p1", "Fernet", 100, 0),
            "negative qty" to OrderLine("p1", "Fernet", 100, -2),
            "negative price" to OrderLine("p1", "Fernet", -1, 1),
        )
        for ((name, line) in cases) {
            assertThrows("orderLine case: $name", IllegalArgumentException::class.java) {
                line.validate()
            }
        }
    }

    // --- Tab totals + merging ---

    @Test
    fun tab_totalCentsSumsLines() {
        val tab = Tab("t1", "mesa-1")
            .addLine(OrderLine("p1", "Fernet", 1250, 2), productAvailable = true)
            .addLine(OrderLine("p2", "Coca", 400, 1), productAvailable = true)
        assertEquals(2900L, tab.totalCents())
    }

    @Test
    fun tab_totalCentsFormatsThroughMoney() {
        val tab = Tab("t1", "mesa-1")
            .addLine(OrderLine("p1", "Fernet", 1250, 2), productAvailable = true)
        assertEquals("25.00", Money.formatCents(tab.totalCents()))
    }

    @Test
    fun tab_addLineMergesSameProductQty() {
        val tab = Tab("t1", "mesa-1")
            .addLine(OrderLine("p1", "Fernet", 1250, 1), productAvailable = true)
            .addLine(OrderLine("p1", "Fernet", 1250, 2), productAvailable = true)
        assertEquals(1, tab.lines.size)
        assertEquals(3, tab.lines.single().qty)
        assertEquals(3750L, tab.totalCents())
    }

    @Test
    fun tab_addLineKeepsDistinctProductsSeparate() {
        val tab = Tab("t1", "mesa-1")
            .addLine(OrderLine("p1", "Fernet", 1250, 1), productAvailable = true)
            .addLine(OrderLine("p2", "Coca", 400, 1), productAvailable = true)
        assertEquals(2, tab.lines.size)
    }

    @Test
    fun tab_addLineRejections() {
        val open = Tab("t1", "mesa-1")
        val line = OrderLine("p1", "Fernet", 1250, 1)
        val badQty = OrderLine("p1", "Fernet", 1250, 0)
        val closed = open.copy(isClosed = true)

        assertThrows(IllegalArgumentException::class.java) {
            open.addLine(line, productAvailable = false)
        }
        assertThrows(IllegalArgumentException::class.java) {
            closed.addLine(line, productAvailable = true)
        }
        assertThrows(IllegalArgumentException::class.java) {
            open.addLine(badQty, productAvailable = true)
        }
    }

    // --- Close snapshot ---

    @Test
    fun tab_closeSnapshotsTotalAndLines() {
        val tab = Tab("t1", "mesa-1")
            .addLine(OrderLine("p1", "Fernet", 1250, 2), productAvailable = true)
        val closed = tab.close(closedAtEpochMs = 1_700_000_000_000L)
        assertEquals("t1", closed.id)
        assertEquals("mesa-1", closed.tableId)
        assertEquals(2500L, closed.totalCents)
        assertEquals(1_700_000_000_000L, closed.closedAtEpochMs)
        assertEquals(tab.lines, closed.lines)
    }

    @Test
    fun tab_closeSnapshotIsImmutable() {
        val tab = Tab("t1", "mesa-1")
            .addLine(OrderLine("p1", "Fernet", 1250, 2), productAvailable = true)
        val closed = tab.close(1_700_000_000_000L)
        // The source tab is untouched by close and stays usable; the snapshot is frozen.
        val extended = tab.addLine(OrderLine("p2", "Coca", 400, 1), productAvailable = true)
        assertEquals(2500L, closed.totalCents)
        assertEquals(1, closed.lines.size)
        assertEquals(2900L, extended.totalCents())
    }

    @Test
    fun tab_closeRejectsAlreadyClosed() {
        val closed = Tab("t1", "mesa-1", isClosed = true)
        assertThrows(IllegalArgumentException::class.java) { closed.close(1L) }
    }

    // --- Expense validation ---

    @Test
    fun expense_validPasses() {
        Expense("e1", "Hielo", 2.5, 800, 1_700_000_000_000L).validate()
    }

    @Test
    fun expense_validationFailures() {
        val cases = listOf(
            "blank description" to Expense("e1", "  ", 1.0, 800, 0L),
            "zero qty" to Expense("e1", "Hielo", 0.0, 800, 0L),
            "negative qty" to Expense("e1", "Hielo", -1.5, 800, 0L),
            "negative cost" to Expense("e1", "Hielo", 1.0, -1, 0L),
        )
        for ((name, expense) in cases) {
            assertThrows("expense case: $name", IllegalArgumentException::class.java) {
                expense.validate()
            }
        }
    }
}
