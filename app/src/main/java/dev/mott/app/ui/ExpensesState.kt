package dev.mott.app.ui

import dev.mott.app.data.ApiException
import dev.mott.app.data.ExpenseItem
import kotlinx.coroutines.CancellationException
import java.io.IOException
import kotlin.math.floor

// Pure Gastos helpers behind the T6 parity UI. They mirror the mitt PC
// Gastos reduction: each line totals qty x unit cost, the header carries
// the total on the right, rows render divided with no delete control (the
// hub exposes GET + POST /api/expenses only), and the mobile form keeps
// concepto+monto with qty defaulting to 1 (the web asks for qty explicitly;
// one unit is the honest mobile default and keeps totals identical).
// English identifiers; every user-visible string stays Spanish.
fun expenseLineTotal(item: ExpenseItem): Long =
    Math.round(item.qty * item.costCents)

fun expensesTotal(items: List<ExpenseItem>): Long =
    items.sumOf(::expenseLineTotal)

// Whole quantities render without decimals ("2 × ...", like the web);
// fractional ones (kilos, liters) keep theirs.
fun formatExpenseQty(qty: Double): String =
    if (!qty.isInfinite() && qty == floor(qty)) {
        qty.toLong().toString()
    } else {
        qty.toString()
    }

// Row subline mirroring the web copy: "date · qty × unit".
fun expenseSubline(item: ExpenseItem): String {
    val unit = "${formatExpenseQty(item.qty)} × ${mittMoneyLabel(item.costCents)}"
    return if (item.date.isNotBlank()) "${item.date} · $unit" else unit
}

// R3-003 gate: the Sin-gastos empty state renders only when there is no
// load failure, so it never shows together with the failure card.
fun shouldShowExpensesEmpty(items: List<ExpenseItem>, failure: LoadFailureReason?): Boolean =
    items.isEmpty() && failure == null

// Cancellation-safe expenses load through the shared SectionLoaders
// contract: Ready on data, Failed with a named reason on any other
// failure. Cancellation keeps propagating (R3-002) so LaunchedEffect
// teardown and structured concurrency keep working; it is never
// classified as ERROR_INESPERADO.
suspend fun loadExpensesState(fetch: suspend () -> List<ExpenseItem>): LoadState<List<ExpenseItem>> {
    try {
        return LoadState.Ready(fetch())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        return LoadState.Failed(classifyLoadFailure(e))
    }
}

// Save-failure copy for the expense form. A 422 is bad input, not a sick
// server, so it reads as REVISAR (retry would never help); transport and
// server failures stay honest about retrying. Cancellation is rethrown,
// never mapped (R3-002).
fun mapExpenseSaveFailure(t: Throwable): String {
    if (t is CancellationException) throw t
    return when {
        t is IllegalArgumentException -> "REVISAR CONCEPTO Y MONTO"
        t is ApiException && t.status == 422 -> "REVISAR CONCEPTO Y MONTO"
        t is ApiException -> "ERROR DEL SERVIDOR, SE REINTENTA"
        t is IOException -> "SIN CONEXIÓN, SE REINTENTA"
        else -> "ERROR INESPERADO, SE REINTENTA"
    }
}
