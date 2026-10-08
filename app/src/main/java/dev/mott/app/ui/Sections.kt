package dev.mott.app.ui

import dev.mott.app.data.Sale
import dev.mott.app.data.TodayResult
import dev.mott.app.domain.Tab

// Bottom-nav sections, mirroring the Figma web master nav order (App.tsx):
// Panel, Mesas, Catálogo, Proveedores, Gastos, Conexión, Personalizar.
// English identifiers; labels stay Spanish for the user-visible nav.
enum class AppSection(val route: String, val label: String) {
    PANEL("panel", "Panel"),
    MESAS("mesas", "Mesas"),
    CATALOGO("catalogo", "Catálogo"),
    PROVEEDORES("proveedores", "Proveedores"),
    GASTOS("gastos", "Gastos"),
    CONEXION("conexion", "Conexión"),
    PERSONALIZAR("personalizar", "Personalizar"),
}

// Shell header subtitle per section, in the app's existing Spanish tone.
// Gastos/Conexión reuse the copy their bodies already rendered so the
// shell header dedup keeps the same words.
fun AppSection.subtitle(): String = when (this) {
    AppSection.PANEL -> "Ventas, mesas y órdenes del día"
    AppSection.MESAS -> "Cuentas abiertas por mesa"
    AppSection.CATALOGO -> "Lista de precios y stock"
    AppSection.PROVEEDORES -> "Compras y contactos del bar"
    AppSection.GASTOS -> "Egresos registrados del servicio"
    AppSection.CONEXION -> "App enlazada con el servidor del bar"
    AppSection.PERSONALIZAR -> "Logo, nombre y colores del bar"
}

// Mesas nav badge: open-table count like the master sidebar
// (App.tsx:86-88 renders the count only when > 0). Null hides the badge.
fun mesasBadgeText(openCount: Int): String? =
    if (openCount <= 0) null else openCount.toString()

// Connectivity pill state behind the shell CONECTADO pill: paired devices
// pulse live, unpaired ones state offline without motion.
data class ConnectionPillState(val text: String, val live: Boolean)

fun connectionPillState(paired: Boolean): ConnectionPillState =
    if (paired) {
        ConnectionPillState(text = "CONECTADO", live = true)
    } else {
        ConnectionPillState(text = "SIN CONEXIÓN", live = false)
    }

// Pairing gate: unpaired devices land on Conexión (the pairing entry,
// QR-first kept); paired devices land on Panel.
fun startSection(paired: Boolean): AppSection =
    if (paired) AppSection.PANEL else AppSection.CONEXION

// Route back-mapping for the bottom bar selected state. Unknown routes
// fall back to Panel so the bar never renders with nothing selected.
fun sectionForRoute(route: String?): AppSection =
    AppSection.entries.find { it.route == route } ?: AppSection.PANEL

// Panel hero figures: sales side from SalesRepo (today aggregate), operate
// side from the open-tabs list. Pure sum, no fetching, never throws.
data class PanelKpis(
    val salesTotalCents: Long,
    val salesCount: Int,
    val openCount: Int,
    val inProgressCents: Long,
)

fun computePanelKpis(today: TodayResult, openTabs: List<Tab>): PanelKpis = PanelKpis(
    salesTotalCents = today.totalCents,
    salesCount = today.count,
    openCount = openTabs.size,
    inProgressCents = openTabs.sumOf { it.totalCents() },
)

// One bar of the Panel sales chart: ISO day plus a short MM-DD label for
// the axis. Labels derive from string slicing only, so a malformed
// timestamp degrades to a short label instead of crashing offline.
data class DayBucket(
    val day: String,
    val label: String,
    val totalCents: Long,
)

// Groups recent sales (hub order is newest first) into per-day totals and
// keeps the last [days] days, oldest first for left-to-right bars.
// Empty input yields an empty list; the UI renders "Sin ventas todavía".
fun bucketSalesByDay(sales: List<Sale>, days: Int = 7): List<DayBucket> {
    if (sales.isEmpty() || days <= 0) return emptyList()
    val sums = LinkedHashMap<String, Long>()
    for (sale in sales) {
        val day = sale.closedAt.take(10)
        sums[day] = (sums[day] ?: 0L) + sale.totalCents
    }
    return sums.keys.sorted().takeLast(days).map { day ->
        DayBucket(day = day, label = day.takeLast(5), totalCents = sums[day] ?: 0L)
    }
}

// Open-tab lookup for the Mesas grid: each table card shows its running
// total next to the Ocupada/Libre pill. Null when the table has no tab.
fun openTabFor(openTabs: List<Tab>, tableId: String): Tab? =
    openTabs.find { it.tableId == tableId }

fun openTabTotal(openTabs: List<Tab>, tableId: String): Long =
    openTabFor(openTabs, tableId)?.totalCents() ?: 0L

// Parses the Gastos amount field into cents. Accepts both decimal
// separators ("12.50" and "12,50"); blank, non-numeric, zero and negative
// inputs return null so the form stays disabled instead of guessing.
fun parseAmountToCents(raw: String): Long? {
    val normalized = raw.trim().replace(',', '.')
    if (normalized.isEmpty()) return null
    val amount = normalized.toDoubleOrNull() ?: return null
    if (!amount.isFinite() || amount <= 0.0) return null
    return Math.round(amount * 100)
}
