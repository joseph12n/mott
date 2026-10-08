package dev.mott.app.ui

import dev.mott.app.ui.order.TableRef

// Pure Mesas admin helpers behind the T5 parity UI: label validation,
// free/open partitioning and per-op named-error copy. HTTP status alone
// decides the copy (the hub yields 409 only for table_occupied on DELETE,
// 422 only for the label rule on POST /api/tables and unknown_table on
// POST /api/tabs), so no error-body parsing is needed. Anything without
// a named copy returns null and the caller falls back to the shared
// SectionLoaders contract (TOKEN_INVALIDO/SIN_SERVIDOR/ERROR_INESPERADO
// with retry). English identifiers; every user-visible string Spanish.
const val TABLE_LABEL_MAX = 40

// Client-side mirror of the hub label rule (trimmed, 1..40 chars).
// Null means the label is valid; anything else is the inline message.
fun validateTableLabel(raw: String): String? {
    val label = raw.trim()
    if (label.isEmpty()) return "Escriba el nombre de la mesa."
    if (label.length > TABLE_LABEL_MAX) return "El nombre debe tener entre 1 y 40 caracteres."
    return null
}

// Direct add-product row validation, same words as the web master notify.
fun validateAdminPick(productId: String, qty: Int): String? =
    if (productId.isEmpty() || qty <= 0) {
        "Elija un producto y una cantidad válida."
    } else {
        null
    }

// Direct add-product pick per open-table card: product plus qty.
data class AdminPick(val productId: String = "", val qty: Int = 1)

fun freeTables(tables: List<TableRef>): List<TableRef> = tables.filter { !it.occupied }

fun openTables(tables: List<TableRef>): List<TableRef> = tables.filter { it.occupied }

fun tableCreateInlineCopy(status: Int): String? = when (status) {
    422 -> "El nombre debe tener entre 1 y 40 caracteres."
    else -> null
}

fun tableDeleteInlineCopy(status: Int): String? = when (status) {
    409 -> "La mesa está ocupada"
    404 -> "La mesa ya no existe."
    else -> null
}

fun openTabInlineCopy(status: Int): String? = when (status) {
    422 -> "Mesa desconocida. Actualizá las mesas e intentá de nuevo."
    else -> null
}

fun addItemInlineCopy(status: Int): String? = when (status) {
    422 -> "Elija un producto y una cantidad válida."
    404 -> "La cuenta ya no existe."
    else -> null
}

fun closeInlineCopy(status: Int): String? = when (status) {
    404 -> "La cuenta ya no existe."
    else -> null
}
