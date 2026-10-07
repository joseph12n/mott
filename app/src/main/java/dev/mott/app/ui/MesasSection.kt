package dev.mott.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.mott.app.data.SalesRepo
import dev.mott.app.domain.Product
import dev.mott.app.domain.Tab
import dev.mott.app.ui.order.OrderEmptyState
import dev.mott.app.ui.order.OrderErrorState
import dev.mott.app.ui.order.OrderScreenHeader
import dev.mott.app.ui.order.OrderViewModel
import dev.mott.app.ui.order.TableRef
import dev.mott.app.ui.theme.FigureStyle
import dev.mott.app.ui.theme.TotalStyle
import kotlinx.coroutines.launch

// No table add/remove here: table admin is web-only.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MesasSection(
    viewModel: OrderViewModel,
    salesRepo: SalesRepo,
    modifier: Modifier = Modifier,
    pendingProductId: String? = null,
    onConsumePending: () -> Unit = {},
    shopName: String? = null,
) {
    val state by viewModel.state.collectAsState()
    var openTabs by remember { mutableStateOf(emptyList<Tab>()) }
    var sheetTableId by remember { mutableStateOf<String?>(null) }
    var closeNote by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun refreshTabs() {
        scope.launch { openTabs = salesRepo.openTabs() }
    }
    LaunchedEffect(Unit) { refreshTabs() }

    val pendingProduct = remember(state.products, pendingProductId) {
        state.products.find { it.id == pendingProductId }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        OrderScreenHeader(title = "Mesas", isOffline = state.isOffline, shopName = shopName)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "${openTabs.size} abiertas · ${mittMoneyLabel(openTabs.sumOf { it.totalCents() })} en curso",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
        MittKpiRow(kpis = computeTablesKpis(state))
        Spacer(modifier = Modifier.height(16.dp))
        if (state.error != null && state.tables.isEmpty()) {
            OrderErrorState(message = state.error!!, modifier = Modifier.weight(1f))
        } else if (state.tables.isEmpty()) {
            OrderEmptyState(
                title = "Sin mesas",
                hint = "Todavía no hay mesas cargadas. Revisar la conexión e intentar de nuevo.",
                modifier = Modifier.weight(1f),
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.tables, key = { it.id }) { table ->
                    val total = openTabTotal(openTabs, table.id)
                    MittClickableCard(
                        onClick = {
                            viewModel.selectTable(table.id)
                            if (pendingProductId != null) {
                                viewModel.increment(pendingProductId)
                                onConsumePending()
                            }
                            closeNote = null
                            sheetTableId = table.id
                        },
                        selected = table.id == state.selectedTableId,
                        modifier = Modifier.heightIn(min = 132.dp),
                    ) {
                        Text(text = table.label, style = MaterialTheme.typography.headlineSmall)
                        Spacer(modifier = Modifier.height(8.dp))
                        MittStatusPill(occupied = table.occupied)
                        if (total > 0L) {
                            Spacer(modifier = Modifier.height(8.dp))
                            MittMoneyText(cents = total, style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
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
