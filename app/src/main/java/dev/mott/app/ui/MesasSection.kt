package dev.mott.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.mott.app.data.ApiException
import dev.mott.app.data.SalesRepo
import dev.mott.app.domain.Product
import dev.mott.app.domain.Tab
import dev.mott.app.net.NetStatus
import dev.mott.app.ui.order.OfflineBanner
import dev.mott.app.ui.order.OrderScreenHeader
import dev.mott.app.ui.order.OrderViewModel
import dev.mott.app.ui.order.TableRef
import dev.mott.app.ui.theme.FigureStyle
import dev.mott.app.ui.theme.TotalStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

// Mesas parity with the web master: hub-owned tables plus their open tabs
// (the bill is the tab, the table row only carries the label). Open-table
// selector plus Abrir, new-table input plus Agregar, chips for every table
// with an occupancy dot and delete X, and one card per open table with its
// lines, total, product-plus-qty row and Cerrar (no payment method, hub
// reduction). The existing bottom-sheet stepper/ANOTAR flow stays intact:
// tapping a chip opens the sheet, exactly the 3-tap mozo flow.
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MesasSection(
    viewModel: OrderViewModel,
    salesRepo: SalesRepo,
    modifier: Modifier = Modifier,
    pendingProductId: String? = null,
    onConsumePending: () -> Unit = {},
    shopName: String? = null,
    onNavigateConnection: () -> Unit = {},
    // T3 shell: the ShellHeader owns the title/subtitle, and its
    // Refrescar bumps refreshSignal to re-trigger the tabs load.
    showHeader: Boolean = true,
    refreshSignal: Int = 0,
) {
    val state by viewModel.state.collectAsState()
    // Open tabs load through the named-state loader: a 401 (hub restart)
    // or any other failure renders an error card with retry instead of
    // escaping the coroutine and crashing the process.
    var tabsState by remember { mutableStateOf<LoadState<List<Tab>>>(LoadState.Loading) }
    var sheetTableId by remember { mutableStateOf<String?>(null) }
    var closeNote by remember { mutableStateOf<String?>(null) }
    // Table admin state: free-table pick, new-table input, per-card picks,
    // plus the honest inline outcome (note/error) and the loader-contract
    // failure with its retry for transport/auth failures.
    var freePickId by remember { mutableStateOf<String?>(null) }
    var newLabel by remember { mutableStateOf("") }
    var picks by remember { mutableStateOf<Map<String, AdminPick>>(emptyMap()) }
    var adminNote by remember { mutableStateOf<String?>(null) }
    var adminError by remember { mutableStateOf<String?>(null) }
    var adminFailure by remember { mutableStateOf<LoadFailureReason?>(null) }
    var adminRetry by remember { mutableStateOf<(() -> Unit)?>(null) }
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current

    fun refreshTabs() {
        scope.launch { tabsState = loadOpenTabsState(salesRepo) }
    }
    LaunchedEffect(refreshSignal) { refreshTabs() }
    val openTabs = (tabsState as? LoadState.Ready)?.data ?: emptyList()

    // Tables come from the hub catalog the ViewModel already holds; every
    // admin mutation refreshes it plus the tabs above.
    fun refreshTables() {
        scope.launch {
            viewModel.loadCatalog()
            tabsState = loadOpenTabsState(salesRepo)
        }
    }

    // Shared admin error mapping: named 409/422 copy inline, everything
    // else through the SectionLoaders contract with retry. Never throws,
    // never silent, never crashes.
    fun reportAdminError(e: Throwable, inlineCopy: (Int) -> String?, retry: () -> Unit) {
        if (e is CancellationException) return
        val named = (e as? ApiException)?.let { inlineCopy(it.status) }
        if (named != null) {
            adminError = named
            return
        }
        adminFailure = classifyLoadFailure(e)
        adminRetry = retry
    }

    fun clearAdminOutcome() {
        adminNote = null
        adminError = null
        adminFailure = null
        adminRetry = null
    }

    // Offline pre-flight before any doomed request goes out.
    fun checkOnline(): Boolean {
        if (NetStatus.isOnline(context)) return true
        adminError = "Sin conexión. Revisá el Wi-Fi del bar e intentá de nuevo."
        return false
    }

    fun openTable(tableId: String) {
        clearAdminOutcome()
        val table = state.tables.find { it.id == tableId }
        if (table == null) {
            adminError = "Mesa desconocida. Actualizá las mesas e intentá de nuevo."
            return
        }
        if (!checkOnline()) return
        scope.launch {
            try {
                salesRepo.openTab(tableId)
                refreshTables()
                freePickId = null
                adminNote = "Mesa ${table.label} abierta."
            } catch (e: Exception) {
                reportAdminError(e, ::openTabInlineCopy) { openTable(tableId) }
            }
        }
    }

    fun createTable() {
        clearAdminOutcome()
        val invalid = validateTableLabel(newLabel)
        if (invalid != null) {
            adminError = invalid
            return
        }
        if (!checkOnline()) return
        val label = newLabel.trim()
        scope.launch {
            try {
                val created = salesRepo.createTable(label)
                refreshTables()
                newLabel = ""
                adminNote = "Mesa ${created.label} agregada."
            } catch (e: Exception) {
                reportAdminError(e, ::tableCreateInlineCopy) { createTable() }
            }
        }
    }

    fun deleteTable(table: TableRef) {
        clearAdminOutcome()
        // Local guard first: an occupied table refuses without a request.
        // The hub 409 table_occupied below covers the race where the tab
        // opened after the catalog snapshot; the table is kept either way.
        if (table.occupied) {
            adminError = "La mesa está ocupada"
            return
        }
        if (!checkOnline()) return
        scope.launch {
            try {
                salesRepo.deleteTable(table.id)
                refreshTables()
                adminNote = "Mesa ${table.label} eliminada."
            } catch (e: Exception) {
                reportAdminError(e, ::tableDeleteInlineCopy) { deleteTable(table) }
            }
        }
    }

    fun addItem(tab: Tab, tableLabel: String) {
        clearAdminOutcome()
        val pick = picks[tab.id] ?: AdminPick()
        val invalid = validateAdminPick(pick.productId, pick.qty)
        if (invalid != null) {
            adminError = invalid
            return
        }
        if (!checkOnline()) return
        scope.launch {
            try {
                salesRepo.addItem(tab.id, pick.productId, pick.qty)
                tabsState = loadOpenTabsState(salesRepo)
            } catch (e: Exception) {
                reportAdminError(e, ::addItemInlineCopy) { addItem(tab, tableLabel) }
            }
        }
    }

    fun closeTable(tab: Tab, tableLabel: String) {
        clearAdminOutcome()
        if (!checkOnline()) return
        scope.launch {
            try {
                val sale = salesRepo.closeTabNow(tab.id)
                refreshTables()
                adminNote = "Mesa $tableLabel cobrada: ${mittMoneyLabel(sale.totalCents)}."
            } catch (e: Exception) {
                reportAdminError(e, ::closeInlineCopy) { closeTable(tab, tableLabel) }
            }
        }
    }

    fun openSheet(table: TableRef) {
        viewModel.selectTable(table.id)
        if (pendingProductId != null) {
            viewModel.increment(pendingProductId)
            onConsumePending()
        }
        closeNote = null
        clearAdminOutcome()
        sheetTableId = table.id
    }

    val pendingProduct = remember(state.products, pendingProductId) {
        state.products.find { it.id == pendingProductId }
    }
    val freeTablesList = remember(state.tables) { freeTables(state.tables) }
    val openTablesList = remember(state.tables) { openTables(state.tables) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        // Shell-owned title: skip our own header but keep the offline
        // banner so the offline state never goes silent.
        if (showHeader) {
            OrderScreenHeader(title = "Mesas", isOffline = state.isOffline, shopName = shopName)
        } else if (state.isOffline) {
            OfflineBanner()
        }
        Spacer(modifier = Modifier.height(4.dp))
        val tabsFailure = tabsState as? LoadState.Failed
        if (tabsFailure != null) {
            MittLoadErrorCard(
                reason = tabsFailure.reason,
                onRetry = ::refreshTabs,
                onRePair = onNavigateConnection,
            )
            Spacer(modifier = Modifier.height(12.dp))
        } else {
            Text(
                text = "${openTablesList.size} abiertas · ${mittMoneyLabel(openTabs.sumOf { it.totalCents() })} en curso",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (pendingProduct != null) {
            Spacer(modifier = Modifier.height(8.dp))
            MittCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Agregando ${pendingProduct.name}: elegí la mesa",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onConsumePending) { Text("QUITAR") }
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        // Admin card mirroring the master top row: free-table selector
        // plus Abrir, new-table input plus Agregar.
        MittCard(modifier = Modifier.fillMaxWidth()) {
            PickerDropdown(
                label = "Mesa libre…",
                options = freeTablesList.map { it.id to it.label },
                selectedId = freePickId,
                onSelect = { freePickId = it },
            )
            Spacer(modifier = Modifier.height(8.dp))
            MittPrimaryButton(
                label = "ABRIR MESA",
                onClick = {
                    val id = freePickId
                    if (id == null) {
                        clearAdminOutcome()
                        adminError = "Elegí una mesa libre."
                    } else {
                        openTable(id)
                    }
                },
                enabled = freePickId != null,
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = newLabel,
                onValueChange = { newLabel = it },
                label = { Text("Nombre de nueva mesa") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
            MittSecondaryButton(
                label = "AGREGAR MESA",
                onClick = ::createTable,
                enabled = newLabel.isNotBlank(),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (freeTablesList.isNotEmpty()) {
                    "${freeTablesList.size} libres"
                } else {
                    "Sin mesas libres."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (adminNote != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = adminNote!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (adminError != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = adminError!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            val failure = adminFailure
            if (failure != null) {
                Spacer(modifier = Modifier.height(8.dp))
                MittLoadErrorCard(
                    reason = failure,
                    onRetry = { adminRetry?.invoke() },
                    onRePair = onNavigateConnection,
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        // Every table as a chip with its occupancy dot and delete X:
        // tapping the chip opens the sheet (the 3-tap mozo flow).
        MittCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Todas las mesas",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (state.error != null && state.tables.isEmpty()) {
                Text(
                    text = state.error!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            } else if (state.tables.isEmpty()) {
                Text(
                    text = "Sin mesas registradas.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (table in state.tables) {
                        TableChip(
                            table = table,
                            selected = table.id == state.selectedTableId,
                            onOpen = { openSheet(table) },
                            onDelete = { deleteTable(table) },
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        // One card per open table: lines, total, product-plus-qty row,
        // Cerrar with no payment method (hub reduction).
        if (openTablesList.isEmpty()) {
            MittCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "No hay mesas abiertas. Abrí una desde el selector de arriba.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            for (table in openTablesList) {
                val tab = openTabFor(openTabs, table.id)
                if (tab == null) continue
                val pick = picks[tab.id] ?: AdminPick()
                OpenTableCard(
                    table = table,
                    tab = tab,
                    products = state.products,
                    pick = pick,
                    onPick = { next -> picks = picks + (tab.id to next) },
                    onAdd = { addItem(tab, table.label) },
                    onClose = { closeTable(tab, table.label) },
                    onOpenSheet = { openSheet(table) },
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
        if (closeNote != null) {
            Text(
                text = closeNote!!,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (state.error != null && state.tables.isNotEmpty()) {
            Text(
                text = state.error!!,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
    }

    val sheetTable = state.tables.find { it.id == sheetTableId }
    if (sheetTable != null) {
        ModalBottomSheet(
            onDismissRequest = {
                sheetTableId = null
                viewModel.startNewOrder()
            },
            sheetState = sheetState,
        ) {
            TableSheet(
                table = sheetTable,
                tab = openTabFor(openTabs, sheetTable.id),
                viewModel = viewModel,
                onClosed = { synced ->
                    closeNote = if (synced) {
                        "MESA ${sheetTable.label} CERRADA Y SINCRONIZADA"
                    } else {
                        "MESA ${sheetTable.label}: CIERRE PENDIENTE DE SYNC"
                    }
                    refreshTabs()
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        sheetTableId = null
                        viewModel.startNewOrder()
                    }
                },
            )
        }
    }
}

// Compact dropdown over an outline button: the option list is small
// (tables, products) so a menu beats a dialog. The selected id drives
// the label; unmatched ids fall back to the placeholder.
@Composable
private fun PickerDropdown(
    label: String,
    options: List<Pair<String, String>>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.find { it.first == selectedId }?.second ?: label
    Box(modifier = modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            enabled = enabled && options.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = selectedLabel, modifier = Modifier.weight(1f))
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            for ((id, name) in options) {
                DropdownMenuItem(
                    text = { Text(text = name) },
                    onClick = {
                        onSelect(id)
                        expanded = false
                    },
                )
            }
        }
    }
}

// Chip for one table: occupancy dot plus label (never color-only), tap
// opens the sheet, X deletes. The X stays disabled while the table reads
// occupied locally; the hub 409 covers the race after the snapshot.
@Composable
private fun TableChip(
    table: TableRef,
    selected: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tone = if (table.occupied) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.tertiary
    }
    Surface(
        onClick = onOpen,
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        border = if (selected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Canvas(modifier = Modifier.size(8.dp)) {
                drawCircle(color = tone)
            }
            Text(
                text = table.label,
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = if (table.occupied) "Ocupada" else "Libre",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                onClick = onDelete,
                enabled = !table.occupied,
                modifier = Modifier.height(32.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
            ) {
                Text(text = "✕", fontSize = 14.sp)
            }
        }
    }
}

// Open-table card from the master: lines, 4xl total, product select plus
// qty plus Agregar, Cerrar with no payment method. Tapping the title
// opens the sheet for the full stepper/ANOTAR flow.
@Composable
private fun OpenTableCard(
    table: TableRef,
    tab: Tab,
    products: List<Product>,
    pick: AdminPick,
    onPick: (AdminPick) -> Unit,
    onAdd: () -> Unit,
    onClose: () -> Unit,
    onOpenSheet: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MittCard(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = onOpenSheet,
                contentPadding = PaddingValues(0.dp),
            ) {
                Text(
                    text = table.label,
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            MittStatusPill(occupied = true)
        }
        Spacer(modifier = Modifier.height(8.dp))
        if (tab.lines.isEmpty()) {
            Text(
                text = "Sin consumos.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            for (line in tab.lines) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                ) {
                    Text(
                        text = "${line.qty}× ${line.name}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    MittMoneyText(cents = line.lineTotal)
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        MittMoneyText(
            cents = tab.totalCents(),
            style = MaterialTheme.typography.displaySmall,
        )
        Spacer(modifier = Modifier.height(12.dp))
        PickerDropdown(
            label = "Producto…",
            options = products.filter { it.available }.map { it.id to it.name },
            selectedId = pick.productId.ifEmpty { null },
            onSelect = { onPick(pick.copy(productId = it)) },
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            OutlinedButton(
                onClick = { onPick(pick.copy(qty = (pick.qty - 1).coerceAtLeast(1))) },
                enabled = pick.qty > 1,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.size(48.dp),
            ) {
                Text(text = "−", fontSize = 24.sp)
            }
            Text(
                text = pick.qty.toString(),
                style = FigureStyle,
                modifier = Modifier.widthIn(min = 32.dp),
            )
            OutlinedButton(
                onClick = { onPick(pick.copy(qty = pick.qty + 1)) },
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.size(48.dp),
            ) {
                Text(text = "+", fontSize = 24.sp)
            }
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = onAdd, enabled = pick.productId.isNotEmpty()) {
                Text("AGREGAR")
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        MittDangerButton(label = "CERRAR", onClick = onClose)
    }
}

// Occupied-table detail and free-table order start share one sheet: the
// open tab lines when present, then the product steppers and ANOTAR from
// the existing OrderViewModel flow, re-skinned onto MittUi containers.
@Composable
private fun TableSheet(
    table: TableRef,
    tab: Tab?,
    viewModel: OrderViewModel,
    onClosed: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val productsById = remember(state.products) { state.products.associateBy { it.id } }
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = table.label,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                )
                MittStatusPill(occupied = table.occupied)
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        if (tab != null && tab.lines.isNotEmpty()) {
            item {
                Text(text = "Cuenta abierta", style = MaterialTheme.typography.titleLarge)
            }
            items(tab.lines, key = { it.productId }) { line ->
                MittCard(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${line.qty}× ${line.name}",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        MittMoneyText(cents = line.lineTotal)
                    }
                }
            }
            item {
                MittMoneyText(cents = tab.totalCents(), style = TotalStyle)
                Spacer(modifier = Modifier.height(4.dp))
                MittDangerButton(
                    label = "CERRAR MESA",
                    onClick = { viewModel.closeTab(tab.id, table.id, onClosed) },
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
        item {
            Text(text = "Agregar productos", style = MaterialTheme.typography.titleLarge)
            if (state.products.isEmpty()) {
                Text(
                    text = "Todavía no hay productos cargados. Revisar la conexión e intentar de nuevo.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(state.products, key = { it.id }) { product ->
            SheetProductRow(
                product = product,
                qty = state.lines[product.id] ?: 0,
                onIncrement = { viewModel.increment(product.id) },
                onDecrement = { viewModel.decrement(product.id) },
            )
        }
        item {
            if (state.lastOrder != null) {
                val committed = state.lastOrder!!
                MittCard(modifier = Modifier.fillMaxWidth()) {
                    Text(text = "PEDIDO ANOTADO", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    MittMoneyText(cents = committed.totalCents, style = TotalStyle)
                    if (state.lastSync != null) {
                        Text(
                            text = state.lastSync!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    MittSecondaryButton(
                        label = "NUEVO PEDIDO",
                        onClick = { viewModel.startNewOrder() },
                    )
                }
            } else {
                Text(text = "TOTAL", style = MaterialTheme.typography.labelLarge)
                MittMoneyText(cents = state.runningTotalCents, style = TotalStyle)
                Spacer(modifier = Modifier.height(12.dp))
                MittPrimaryButton(
                    label = "ANOTAR",
                    onClick = { viewModel.submit() },
                    enabled = state.lines.isNotEmpty(),
                )
                if (state.lastSync != null) {
                    Text(
                        text = state.lastSync!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            if (state.error != null) {
                Text(
                    text = state.error!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            // Keep the product name lookup honest when a line references a
            // product the refreshed catalog no longer carries.
            val missing = state.lines.keys.filter { productsById[it] == null }
            if (missing.isNotEmpty()) {
                Text(
                    text = "Algunos productos ya no están en el catálogo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun SheetProductRow(
    product: Product,
    qty: Int,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MittCard(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = if (product.available) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                MittMoneyText(
                    cents = product.priceCents,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!product.available) {
                    Spacer(modifier = Modifier.height(8.dp))
                    MittStockPill(available = false)
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                OutlinedButton(
                    onClick = onDecrement,
                    enabled = product.available && qty > 0,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.size(48.dp),
                ) {
                    Text(text = "−", fontSize = 24.sp)
                }
                Text(
                    text = qty.toString(),
                    style = FigureStyle,
                    modifier = Modifier.widthIn(min = 32.dp),
                )
                OutlinedButton(
                    onClick = onIncrement,
                    enabled = product.available,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.size(48.dp),
                ) {
                    Text(text = "+", fontSize = 24.sp)
                }
            }
        }
    }
}
