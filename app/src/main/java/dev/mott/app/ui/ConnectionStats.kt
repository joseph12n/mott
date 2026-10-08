package dev.mott.app.ui

import dev.mott.app.data.OpQueue
import dev.mott.app.ui.order.OrderCatalog

// Mobile-side Conexion numbers: everything the phone already knows, no hub
// probing. Table/product counts come from the cached catalog snapshot
// (ApiOrderCatalog memory state), the queue depth from the offline outbox.
// A null pendingCount means unknown (never observed), never zero-as-default.
data class ConnectionKpis(
    val tableCount: Int,
    val productCount: Int,
    val pendingCount: Int? = null,
)

fun computeConnectionKpis(catalog: OrderCatalog, pendingCount: Int?): ConnectionKpis =
    ConnectionKpis(
        tableCount = catalog.listTables().size,
        productCount = catalog.listProducts().size,
        pendingCount = pendingCount,
    )

// Offline queue depth: how many ops still wait for the hub FIFO drain.
suspend fun pendingQueueDepth(queue: OpQueue): Int = queue.peekAll().size

// Clipboard button feedback, same wording as the PC Conexion view.
fun connectionCopyLabel(copied: Boolean): String =
    if (copied) "Copiado" else "Copiar dirección"

// Status pill: paired with no reported failure reads as an active server;
// a named LoadFailureReason (T1 vocabulary) names the failure instead.
fun connectionPillText(reason: LoadFailureReason?): String =
    reason?.title() ?: "Servidor activo"

// Mobile pairing guide: the phone side of the PC QR flow. Step 1 opens the
// hub on the PC, step 2 scans its QR, step 3 is the catalog auto-download
// plus first order.
fun connectionSteps(): List<String> = listOf(
    "Abrí mitt en la PC conectada al mismo Wi-Fi del bar.",
    "Escanéá el QR que muestra la pantalla del servidor.",
    "Mesas y productos se descargan solos: empezá a anotar.",
)
