package dev.mott.app.ui

import dev.mott.app.data.Supplier
import kotlinx.coroutines.CancellationException

// Pure Proveedores helpers behind the T7 parity UI. They mirror the mitt
// PC Proveedores reduction exactly:
// - The hub stores a short record {name, phone, note} with NO
//   per-supplier catalog or purchase ledger, so the list shows contact
//   info (phone or "Sin teléfono" plus the note) and never invented
//   balances.
// - Hub limits: name trimmed 1..80 chars, phone <=40, note <=200
//   (domain/supplier.go). Client validation mirrors those bounds; a 422
//   is the server-side twin of the same rule.
// - GET /api/suppliers is name-ordered; the client preserves that order
//   and only tracks which card is selected.
// - DELETE answers 204 with no guards (nothing references suppliers),
//   so unlike tables no local occupancy check applies.
// HTTP status alone decides the inline copy; anything without a named
// copy returns null and the caller falls back to the shared
// SectionLoaders contract (TOKEN_INVALIDO/SIN_SERVIDOR/ERROR_INESPERADO
// with retry). English identifiers; every user-visible string Spanish.
const val SUPPLIER_NAME_MAX = 80
const val SUPPLIER_PHONE_MAX = 40
const val SUPPLIER_NOTE_MAX = 200

// Client-side mirror of the hub supplier rule. Null means the input is
// valid; anything else is the inline message, same words the web master
// notifies with ("Escriba el nombre del proveedor." on add,
// "El nombre no puede quedar vacío." on edit share this blank copy).
fun validateSupplierInput(name: String, phone: String, note: String): String? {
    if (name.trim().isEmpty()) return "Escriba el nombre del proveedor."
    if (name.trim().length > SUPPLIER_NAME_MAX) return "El nombre debe tener como máximo 80 caracteres."
    if (phone.length > SUPPLIER_PHONE_MAX) return "El teléfono debe tener como máximo 40 caracteres."
    if (note.length > SUPPLIER_NOTE_MAX) return "La nota debe tener como máximo 200 caracteres."
    return null
}

fun supplierSaveInlineCopy(status: Int): String? = when (status) {
    422 -> "Revisá los datos: nombre 1-80, teléfono hasta 40 y nota hasta 200 caracteres."
    else -> null
}

fun supplierMissingInlineCopy(status: Int): String? = when (status) {
    404 -> "El proveedor ya no existe."
    else -> null
}

// Selection fallback mirroring the PC effects: the first supplier seeds
// the selection and a stale id (deleted elsewhere) falls back to it.
// Null only when the list itself is empty.
fun resolveSupplierSelection(suppliers: List<Supplier>, selectedId: String?): String? {
    if (suppliers.isEmpty()) return null
    if (suppliers.any { it.id == selectedId }) return selectedId
    return suppliers.first().id
}

// Edit-form dirtiness mirroring the PC dirty flag: the name compares
// trimmed (the hub trims on write), phone and note compare raw.
fun supplierDirty(current: Supplier, name: String, phone: String, note: String): Boolean =
    name.trim() != current.name || phone != current.phone || note != current.note

// Cancellation-safe suppliers load through the shared SectionLoaders
// contract: Ready on data, Failed with a named reason on any other
// failure. Cancellation keeps propagating (R3-002) so LaunchedEffect
// teardown and structured concurrency keep working; it is never
// classified as ERROR_INESPERADO.
suspend fun loadSuppliersState(fetch: suspend () -> List<Supplier>): LoadState<List<Supplier>> {
    try {
        return LoadState.Ready(fetch())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        return LoadState.Failed(classifyLoadFailure(e))
    }
}
