package dev.mott.app.ui

import dev.mott.app.data.ExpenseItem
import dev.mott.app.data.Sale
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// T4 Panel parity stats: JVM-pure math behind PanelSection, no Compose.
// The hub stores every timestamp as UTC RFC3339 text while /api/sales
// today aggregates on the UTC calendar day (mitt store/sale.go); the
// Panel buckets client-side by the bar's LOCAL hour like the mitt web
// Panel does (new Date(iso).getHours()). Consequence, kept visible: a
// sale closed near midnight can land in a different local hour/day than
// the UTC hero aggregate counts it in. Nothing here throws on bad hub
// data: unparseable timestamps degrade to skip/dash, never a crash.

// Service window rendered by the hourly chart (Figmaster 11h-22h).
const val PANEL_HOUR_START = 11
const val PANEL_HOUR_END = 22

// One bar of the hourly chart: service hour plus its closed-sales total.
data class HourBucket(
    val hour: Int,
    val label: String,
    val totalCents: Long,
)

// Parses a hub RFC3339 instant into its local hour. Null when the text
// is not a parseable instant, so callers can skip the row fail-soft.
fun hourOfLocal(closedAt: String, zone: ZoneId = ZoneId.systemDefault()): Int? =
    runCatching { Instant.parse(closedAt).atZone(zone).hour }.getOrNull()

// Groups closed sales into the 11h-22h service window by local hour.
// Always returns all twelve buckets (zero-filled like the web master),
// so the chart axis never shifts shape; sales outside the window or with
// garbage timestamps are excluded from the chart but still count in the
// KPI hero, which reads the hub aggregate, not these buckets.
fun bucketSalesByHour(
    sales: List<Sale>,
    zone: ZoneId = ZoneId.systemDefault(),
): List<HourBucket> {
    val sums = LongArray(PANEL_HOUR_END - PANEL_HOUR_START + 1)
    for (sale in sales) {
        val hour = hourOfLocal(sale.closedAt, zone) ?: continue
        if (hour in PANEL_HOUR_START..PANEL_HOUR_END) {
            sums[hour - PANEL_HOUR_START] += sale.totalCents
        }
    }
    return (PANEL_HOUR_START..PANEL_HOUR_END).map { hour ->
        HourBucket(hour = hour, label = "${hour}h", totalCents = sums[hour - PANEL_HOUR_START])
    }
}

// Charted volume: the summed hourly buckets (window sales only).
fun hourlySalesTotal(buckets: List<HourBucket>): Long = buckets.sumOf { it.totalCents }

// Top product by ordered quantity across closed sales.
data class TopProduct(val name: String, val qty: Int)

// Sums line quantities by product name, highest first, ties broken by
// name so the ranking is deterministic, capped at [limit] like the web
// "Mas pedidos" top-5.
fun topProducts(sales: List<Sale>, limit: Int = 5): List<TopProduct> {
    if (sales.isEmpty() || limit <= 0) return emptyList()
    val sums = LinkedHashMap<String, Int>()
    for (sale in sales) {
        for (line in sale.items) {
            sums[line.name] = (sums[line.name] ?: 0) + line.qty
        }
    }
    return sums.entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .take(limit)
        .map { TopProduct(name = it.key, qty = it.value) }
}

// Closed-sales volume per table, highest total first.
data class TableSales(
    val label: String,
    val totalCents: Long,
    val count: Int,
)

// Groups by table id, resolving the human label through [labelOf] which
// falls back to the raw id (same contract as the web labelOf helper).
fun salesByTable(
    sales: List<Sale>,
    labelOf: (String) -> String = { it },
): List<TableSales> {
    if (sales.isEmpty()) return emptyList()
    val totals = LinkedHashMap<String, Long>()
    val counts = LinkedHashMap<String, Int>()
    for (sale in sales) {
        totals[sale.tableId] = (totals[sale.tableId] ?: 0L) + sale.totalCents
        counts[sale.tableId] = (counts[sale.tableId] ?: 0) + 1
    }
    return totals.entries
        .sortedByDescending { it.value }
        .map { (tableId, total) ->
            TableSales(label = labelOf(tableId), totalCents = total, count = counts[tableId] ?: 0)
        }
}

// Net of the service: collected sales minus today gastos minus supplier
// payments. The hub exposes no supplier ledger and no payment method on
// close, so supplier payments stay 0 until a method exists; the footer
// says so instead of inventing a figure.
fun netCents(
    salesTotalCents: Long,
    expensesCents: Long,
    supplierPaidCents: Long = 0L,
): Long = salesTotalCents - expensesCents - supplierPaidCents

// Average ticket, rounded like the web Panel (Math.round), zero when no
// order closed yet so the KPI never divides by zero.
fun averageTicketCents(totalCents: Long, count: Int): Long =
    if (count > 0) Math.round(totalCents.toDouble() / count) else 0L

// True when a hub RFC3339 instant falls on the given local calendar day.
// Garbage timestamps return false (fail-soft exclusion from the total).
fun isLocalDay(closedAt: String, day: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Boolean =
    runCatching { Instant.parse(closedAt).atZone(zone).toLocalDate() == day }.getOrDefault(false)

// Today gastos for the net footer. Adaptation, kept honest: the mobile
// ExpenseItem carries no qty (the Gastos form posts qty 1.0 per row), so
// this sums cost_cents of rows dated today instead of the web qty x cost.
fun expensesTodayTotal(
    expenses: List<ExpenseItem>,
    today: LocalDate,
    zone: ZoneId = ZoneId.systemDefault(),
): Long = expenses.filter { isLocalDay(it.date, today, zone) }.sumOf { it.costCents }

private val saleHourFormat = DateTimeFormatter.ofPattern("HH:mm")

// Recent-order hour in local HH:mm, "—" when the timestamp is garbage.
fun formatSaleHour(closedAt: String, zone: ZoneId = ZoneId.systemDefault()): String =
    runCatching { saleHourFormat.format(Instant.parse(closedAt).atZone(zone)) }.getOrDefault("—")

// CTA bindings for the Panel footer buttons: each user action routes to
// its own navigation callback. The indirection keeps the routing
// contract JVM-testable without Compose UI tests.
data class PanelCtas(
    val onConnectMobile: () -> Unit,
    val onViewSuppliers: () -> Unit,
)

fun panelCtas(
    onConnectMobile: () -> Unit,
    onViewSuppliers: () -> Unit,
): PanelCtas = PanelCtas(onConnectMobile = onConnectMobile, onViewSuppliers = onViewSuppliers)
