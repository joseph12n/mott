package dev.mott.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.mott.app.data.ApiException
import dev.mott.app.data.SalesRepo
import dev.mott.app.domain.Tab
import dev.mott.app.net.NetStatus
import dev.mott.app.ui.order.OfflineBanner
import dev.mott.app.ui.order.OrderScreenHeader
import dev.mott.app.ui.order.OrderViewModel
import dev.mott.app.ui.order.TableRef
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

// Mesas section without overlays. The hub owns tables and their open tabs
// (the bill is the tab, the table row only carries the label).
//
//   LIST   : header, new-table row, grid of table tiles (one tap target each)
//   DETAIL : the selected table full width with a back affordance (phones)
//   SPLIT  : list and detail side by side (wide sections)
//
// The pane comes from the pure mesasPaneFor over the section's own width.
// Every action lives inline: opening a table, ANOTAR, and the two-tap
// confirmations for closing or deleting. Selection is the ViewModel's
// selectedTableId, so "no selection" means the list on narrow screens.
@Composable
fun MesasSection(
    viewModel: OrderViewModel,
    salesRepo: SalesRepo,
    modifier: Modifier = Modifier,
    pendingProductId: String? = null,
    onConsumePending: () -> Unit = {},
    shopName: String? = null,
    onNavigateConnection: () -> Unit = {},
    // The adaptive shell owns the title/subtitle; its Refrescar bumps
    // refreshSignal to re-trigger the tabs load.
    showHeader: Boolean = true,
    refreshSignal: Int = 0,
) {
    val state by viewModel.state.collectAsState()
    // Open tabs load through the named-state loader: a 401 (hub restart)
    // or any other failure renders an error card with retry instead of
    // escaping the coroutine and crashing the process.
    var tabsState by remember { mutableStateOf<LoadState<List<Tab>>>(LoadState.Loading) }
    var closeNote by remember { mutableStateOf<String?>(null) }
    // Admin outcome: honest inline note/error plus the loader-contract
    // failure with its retry for transport/auth failures.
    var newLabel by remember { mutableStateOf("") }
    var adminNote by remember { mutableStateOf<String?>(null) }
    var adminError by remember { mutableStateOf<String?>(null) }
    var adminFailure by remember { mutableStateOf<LoadFailureReason?>(null) }
    var adminRetry by remember { mutableStateOf<(() -> Unit)?>(null) }
    val scope = rememberCoroutineScope()
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
                viewModel.startNewOrder()
                adminNote = "Mesa ${table.label} eliminada."
            } catch (e: Exception) {
                reportAdminError(e, ::tableDeleteInlineCopy) { deleteTable(table) }
            }
        }
    }

    // Selecting a table starts from a clean order, so a draft never leaks
    // from one table to the next while the list stays visible (split).
    // A product picked in the catalog lands on the chosen table.
    fun selectTable(table: TableRef) {
        viewModel.startNewOrder()
        viewModel.selectTable(table.id)
        if (pendingProductId != null) {
            viewModel.increment(pendingProductId)
            onConsumePending()
        }
        closeNote = null
        clearAdminOutcome()
    }

    val pendingProduct = remember(state.products, pendingProductId) {
        state.products.find { it.id == pendingProductId }
    }
    val selectedTable = state.selectedTable

    // Outcome lines (close note, admin note/error, load failure) render in
    // the pane the user is looking at, never twice.
    val outcome: @Composable () -> Unit = {
        MesasOutcome(
            closeNote = closeNote,
            adminNote = adminNote,
            adminError = adminError,
            adminFailure = adminFailure,
            onRetry = { adminRetry?.invoke() },
            onRePair = onNavigateConnection,
        )
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val sectionWidth = maxWidth
        val pane = mesasPaneFor(
            mesasWidthClassFor(sectionWidth.value.toInt()),
            hasSelection = selectedTable != null,
        )

        // Back from the full-width detail returns to the list.
        BackHandler(enabled = pane == MesasPane.DETAIL) { viewModel.startNewOrder() }

        val detail: @Composable (Modifier) -> Unit = { detailModifier ->
            if (selectedTable == null) {
                MittCard(modifier = detailModifier.padding(16.dp).fillMaxWidth()) {
                    Text(
                        text = "Elegí una mesa para ver su cuenta.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                TableDetailPane(
                    table = selectedTable,
                    tab = openTabFor(openTabs, selectedTable.id),
                    viewModel = viewModel,
                    showBack = pane == MesasPane.DETAIL,
                    onBack = { viewModel.startNewOrder() },
                    onOpenTable = { openTable(selectedTable.id) },
                    onDeleteTable = { deleteTable(selectedTable) },
                    onClosed = { synced ->
                        closeNote = if (synced) {
                            "MESA ${selectedTable.label} CERRADA Y SINCRONIZADA"
                        } else {
                            "MESA ${selectedTable.label}: CIERRE PENDIENTE DE SYNC"
                        }
                        refreshTabs()
                        viewModel.startNewOrder()
                    },
                    modifier = detailModifier,
                    outcome = if (pane == MesasPane.DETAIL) outcome else ({}),
                )
            }
        }

        val list: @Composable (Modifier, Dp) -> Unit = { listModifier, listWidth ->
            TableListPane(
                widthDp = listWidth,
                tables = state.tables,
                tabsFailure = tabsState as? LoadState.Failed,
                openTabs = openTabs,
                selectedId = state.selectedTableId,
                isOffline = state.isOffline,
                loadError = state.error,
                pendingProductName = pendingProduct?.name,
                shopName = shopName,
                showHeader = showHeader,
                newLabel = newLabel,
                onNewLabelChange = { newLabel = it },
                onCreate = ::createTable,
                onRetryTabs = ::refreshTabs,
                onRePair = onNavigateConnection,
                onQuitPending = onConsumePending,
                onSelect = ::selectTable,
                outcome = if (pane == MesasPane.DETAIL) ({}) else outcome,
                modifier = listModifier,
            )
        }

        when (pane) {
            MesasPane.LIST -> list(Modifier.fillMaxSize(), sectionWidth)
            MesasPane.DETAIL -> detail(Modifier.fillMaxSize())
            MesasPane.SPLIT -> Row(modifier = Modifier.fillMaxSize()) {
                list(Modifier.weight(1f).fillMaxHeight(), sectionWidth / 2)
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                detail(Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}

// List pane: one grid so the whole pane scrolls as a unit. Header content
// spans every column; each table is one tile and one tap target.
@Composable
private fun TableListPane(
    widthDp: Dp,
    tables: List<TableRef>,
    tabsFailure: LoadState.Failed?,
    openTabs: List<Tab>,
    selectedId: String?,
    isOffline: Boolean,
    loadError: String?,
    pendingProductName: String?,
    shopName: String?,
    showHeader: Boolean,
    newLabel: String,
    onNewLabelChange: (String) -> Unit,
    onCreate: () -> Unit,
    onRetryTabs: () -> Unit,
    onRePair: () -> Unit,
    onQuitPending: () -> Unit,
    onSelect: (TableRef) -> Unit,
    outcome: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 32dp = the 16dp content padding on each side.
    val columns = tileColumns(widthDp.value.toInt() - 32)
    val fullSpan: LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Shell-owned title: skip our own header but keep the offline
        // banner so the offline state never goes silent.
        if (showHeader) {
            item(span = fullSpan) {
                OrderScreenHeader(title = "Mesas", isOffline = isOffline, shopName = shopName)
            }
        } else if (isOffline) {
            item(span = fullSpan) { OfflineBanner() }
        }
        item(span = fullSpan) {
            if (tabsFailure != null) {
                MittLoadErrorCard(
                    reason = tabsFailure.reason,
                    onRetry = onRetryTabs,
                    onRePair = onRePair,
                )
            } else {
                Text(
                    text = "${openTables(tables).size} abiertas · " +
                        "${mittMoneyLabel(openTabs.sumOf { it.totalCents() })} en curso",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (pendingProductName != null) {
            item(span = fullSpan) {
                MittCard(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Agregando $pendingProductName: elegí la mesa",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = onQuitPending,
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) { Text("QUITAR") }
                    }
                }
            }
        }
        item(span = fullSpan) { outcome() }
        item(span = fullSpan) {
            NewTableRow(
                widthDp = widthDp,
                value = newLabel,
                onValueChange = onNewLabelChange,
                onCreate = onCreate,
            )
        }
        if (tables.isEmpty()) {
            item(span = fullSpan) {
                Text(
                    text = loadError ?: "Sin mesas registradas.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (loadError != null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        } else {
            items(tables, key = { it.id }) { table ->
                TableTile(
                    table = table,
                    totalCents = openTabTotal(openTabs, table.id),
                    selected = table.id == selectedId,
                    onClick = { onSelect(table) },
                )
            }
            if (loadError != null) {
                item(span = fullSpan) {
                    Text(
                        text = loadError,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

// New-table input: field and button share a row when there is room and
// stack otherwise, so neither is ever squeezed or clipped.
@Composable
private fun NewTableRow(
    widthDp: Dp,
    value: String,
    onValueChange: (String) -> Unit,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val field: @Composable (Modifier) -> Unit = { fieldModifier ->
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text("Nombre de nueva mesa", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            singleLine = true,
            modifier = fieldModifier,
        )
    }
    if (widthDp >= 420.dp) {
        Row(
            modifier = modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            field(Modifier.weight(1f))
            MittSecondaryButton(
                label = "AGREGAR MESA",
                onClick = onCreate,
                enabled = value.isNotBlank(),
                modifier = Modifier.width(180.dp),
            )
        }
    } else {
        Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            field(Modifier.fillMaxWidth())
            MittSecondaryButton(
                label = "AGREGAR MESA",
                onClick = onCreate,
                enabled = value.isNotBlank(),
            )
        }
    }
}

// One table = one tap target (the whole card). Status is a pill with a dot
// and text, never color alone; the open-tab total shows only when occupied.
@Composable
private fun TableTile(
    table: TableRef,
    totalCents: Long,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MittClickableCard(
        onClick = onClick,
        selected = selected,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 96.dp),
    ) {
        Text(
            text = table.label,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(8.dp))
        MittStatusPill(occupied = table.occupied)
        if (table.occupied) {
            Spacer(modifier = Modifier.height(8.dp))
            MittMoneyText(cents = totalCents)
        }
    }
}

@Composable
private fun MesasOutcome(
    closeNote: String?,
    adminNote: String?,
    adminError: String?,
    adminFailure: LoadFailureReason?,
    onRetry: () -> Unit,
    onRePair: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (closeNote != null) {
            Text(
                text = closeNote,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (adminNote != null) {
            Text(
                text = adminNote,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (adminError != null) {
            Text(
                text = adminError,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (adminFailure != null) {
            MittLoadErrorCard(reason = adminFailure, onRetry = onRetry, onRePair = onRePair)
        }
    }
}
