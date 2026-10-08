package dev.mott.app

import dev.mott.app.data.ExpenseItem
import dev.mott.app.data.Sale
import dev.mott.app.domain.OrderLine
import dev.mott.app.ui.PANEL_HOUR_END
import dev.mott.app.ui.PANEL_HOUR_START
import dev.mott.app.ui.averageTicketCents
import dev.mott.app.ui.bucketSalesByHour
import dev.mott.app.ui.expensesTodayTotal
import dev.mott.app.ui.formatSaleHour
import dev.mott.app.ui.hourlySalesTotal
import dev.mott.app.ui.netCents
import dev.mott.app.ui.panelCtas
import dev.mott.app.ui.salesByTable
import dev.mott.app.ui.topProducts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

// T4 Panel parity stats: JVM-pure coverage for the hourly bucketing,
// top-5, per-table and net math behind PanelSection. Timezones are passed
// explicitly so the suite is deterministic on any CI machine.
class PanelStatsTest {
    private val utc = ZoneId.of("UTC")
    private val bsAs = ZoneId.of("America/Argentina/Buenos_Aires")

    private fun line(name: String, qty: Int, price: Long = 100L) =
        OrderLine(productId = "p-$name", name = name, unitPriceCents = price, qty = qty)

    private fun sale(
        id: String,
        closedAt: String,
        totalCents: Long = 1000L,
        tableId: String = "t1",
        items: List<OrderLine> = emptyList(),
    ) = Sale(id = id, tableId = tableId, items = items, totalCents = totalCents, closedAt = closedAt)

    private fun expense(id: String, date: String, costCents: Long = 500L) =
        ExpenseItem(id = id, description = "d-$id", costCents = costCents, date = date)

    // Hourly bucketing from UTC instants into local hours.

    @Test
    fun `hour buckets always cover 11h to 22h`() {
        val buckets = bucketSalesByHour(emptyList(), utc)

        assertEquals(12, buckets.size)
        assertEquals(PANEL_HOUR_START, 11)
        assertEquals(PANEL_HOUR_END, 22)
        assertEquals((11..22).toList(), buckets.map { it.hour })
        assertEquals("11h", buckets.first().label)
        assertEquals("22h", buckets.last().label)
        assertTrue(buckets.all { it.totalCents == 0L })
    }

    @Test
    fun `utc instant lands in the correct local hour`() {
        // 14:00Z is 11:00 on the bar wall clock (UTC-3).
        val buckets = bucketSalesByHour(
            listOf(sale("s1", "2026-10-07T14:00:00Z", totalCents = 1200L)),
            bsAs,
        )

        assertEquals(1200L, buckets.single { it.hour == 11 }.totalCents)
        assertEquals(1200L, hourlySalesTotal(buckets))
    }

    @Test
    fun `hour bucketing respects range boundaries`() {
        val sales = listOf(
            sale("in-11", "2026-10-07T11:00:00Z", totalCents = 100L),
            sale("in-22", "2026-10-07T22:59:00Z", totalCents = 200L),
            sale("out-10", "2026-10-07T10:59:00Z", totalCents = 400L),
            sale("out-23", "2026-10-07T23:00:00Z", totalCents = 800L),
        )
        val buckets = bucketSalesByHour(sales, utc)

        assertEquals(100L, buckets.single { it.hour == 11 }.totalCents)
        assertEquals(200L, buckets.single { it.hour == 22 }.totalCents)
        assertEquals(300L, hourlySalesTotal(buckets))
    }

    @Test
    fun `hour bucketing sums same-hour sales and parses offsets`() {
        val buckets = bucketSalesByHour(
            listOf(
                sale("s1", "2026-10-07T20:11:00Z", totalCents = 1250L),
                sale("s2", "2026-10-07T20:45:00+00:00", totalCents = 1650L),
            ),
            utc,
        )

        assertEquals(2900L, buckets.single { it.hour == 20 }.totalCents)
    }

    @Test
    fun `hour bucketing fail-soft on malformed timestamps`() {
        val buckets = bucketSalesByHour(
            listOf(
                sale("bad", "nonsense", totalCents = 999L),
                sale("empty", "", totalCents = 111L),
                sale("ok", "2026-10-07T15:00:00Z", totalCents = 100L),
            ),
            utc,
        )

        assertEquals(12, buckets.size)
        assertEquals(100L, hourlySalesTotal(buckets))
        assertEquals(100L, buckets.single { it.hour == 15 }.totalCents)
    }

    @Test
    fun `hourly total of empty sales is zero`() {
        assertEquals(0L, hourlySalesTotal(bucketSalesByHour(emptyList(), utc)))
    }

    // Top-5 products by ordered quantity.

    @Test
    fun `top products order by qty desc and cap at five`() {
        val sales = (1..7).map { i ->
            sale("s$i", "2026-10-07T15:00:00Z", items = listOf(line("prod$i", qty = i)))
        }

        val top = topProducts(sales)

        assertEquals(5, top.size)
        assertEquals(listOf("prod7", "prod6", "prod5", "prod4", "prod3"), top.map { it.name })
        assertEquals(7, top.first().qty)
    }

    @Test
    fun `top products sum qty across sales and break ties by name`() {
        val sales = listOf(
            sale("s1", "2026-10-07T15:00:00Z", items = listOf(line("b-fernet", 2), line("a-coca", 1))),
            sale("s2", "2026-10-07T16:00:00Z", items = listOf(line("a-coca", 1))),
        )

        val top = topProducts(sales)

        assertEquals(2, top.size)
        // Tie at qty 2: alphabetical first wins, deterministic for the UI.
        assertEquals(listOf("a-coca", "b-fernet"), top.map { it.name })
    }

    @Test
    fun `top products empty without items`() {
        assertTrue(topProducts(emptyList()).isEmpty())
        assertTrue(topProducts(listOf(sale("s1", "2026-10-07T15:00:00Z"))).isEmpty())
    }

    // Per-table totals.

    @Test
    fun `sales by table sum totals and counts sorted desc`() {
        val sales = listOf(
            sale("s1", "2026-10-07T15:00:00Z", totalCents = 100L, tableId = "t1"),
            sale("s2", "2026-10-07T16:00:00Z", totalCents = 500L, tableId = "t2"),
            sale("s3", "2026-10-07T17:00:00Z", totalCents = 200L, tableId = "t1"),
        )

        val byTable = salesByTable(sales) { id -> mapOf("t1" to "MESA 1", "t2" to "MESA 2")[id] ?: id }

        assertEquals(listOf("MESA 2", "MESA 1"), byTable.map { it.label })
        assertEquals(500L, byTable.first().totalCents)
        assertEquals(1, byTable.first().count)
        assertEquals(300L, byTable.last().totalCents)
        assertEquals(2, byTable.last().count)
    }

    @Test
    fun `sales by table falls back to raw id without labels`() {
        val byTable = salesByTable(listOf(sale("s1", "2026-10-07T15:00:00Z", tableId = "tx")))

        assertEquals("tx", byTable.single().label)
    }

    @Test
    fun `sales by table empty without sales`() {
        assertTrue(salesByTable(emptyList()).isEmpty())
    }

    // Net math, ticket average, expenses-today filter.

    @Test
    fun `net subtracts gastos and supplier payments with pagos zero by default`() {
        assertEquals(700L, netCents(salesTotalCents = 1000L, expensesCents = 300L))
        assertEquals(500L, netCents(salesTotalCents = 1000L, expensesCents = 300L, supplierPaidCents = 200L))
        assertEquals(0L, netCents(salesTotalCents = 0L, expensesCents = 0L))
    }

    @Test
    fun `average ticket rounds like the web panel and stays zero without orders`() {
        assertEquals(0L, averageTicketCents(totalCents = 0L, count = 0))
        assertEquals(1500L, averageTicketCents(totalCents = 4500L, count = 3))
        assertEquals(1501L, averageTicketCents(totalCents = 3001L, count = 2))
    }

    @Test
    fun `expenses today match the local calendar day and skip garbage`() {
        // 02:00Z Oct 8 is still Oct 7 on the bar wall clock (UTC-3).
        val items = listOf(
            expense("e1", "2026-10-07T20:00:00Z", costCents = 400L),
            expense("e2", "2026-10-08T02:00:00Z", costCents = 600L),
            expense("e3", "2026-10-08T04:00:00Z", costCents = 999L),
            expense("e4", "nonsense", costCents = 50L),
        )

        val total = expensesTodayTotal(items, today = LocalDate.of(2026, 10, 7), zone = bsAs)

        assertEquals(1000L, total)
    }

    @Test
    fun `expenses today empty without matches`() {
        assertEquals(0L, expensesTodayTotal(emptyList(), today = LocalDate.of(2026, 10, 7), zone = utc))
    }

    // Recent-order hour label and CTA callback contract.

    @Test
    fun `sale hour formats local hh-mm and degrades to dash`() {
        assertEquals("11:00", formatSaleHour("2026-10-07T14:00:00Z", bsAs))
        assertEquals("—", formatSaleHour("nonsense", utc))
        assertEquals("—", formatSaleHour("", utc))
    }

    @Test
    fun `panel ctas route each button to its own callback`() {
        var connectCalls = 0
        var suppliersCalls = 0
        val ctas = panelCtas(
            onConnectMobile = { connectCalls++ },
            onViewSuppliers = { suppliersCalls++ },
        )

        ctas.onConnectMobile()
        ctas.onConnectMobile()

        assertEquals(2, connectCalls)
        assertEquals(0, suppliersCalls)

        ctas.onViewSuppliers()

        assertEquals(1, suppliersCalls)
    }
}
