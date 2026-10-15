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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.mott.app.domain.Product
import dev.mott.app.domain.Tab
import dev.mott.app.ui.order.OrderViewModel
import dev.mott.app.ui.order.TableRef
import dev.mott.app.ui.theme.FigureStyle
import dev.mott.app.ui.theme.TotalStyle

// In-section table detail (replaces the old bottom sheet). Top to bottom:
// header, free-table actions or open-tab lines, product steppers in a
// scrollable region, then a sticky bar (total, ANOTAR, inline-confirm
// close). The scrolling list takes weight(1f) and the bar sits below it in
// the same Column, so the bar can never cover a product row.
@Composable
fun TableDetailPane(
    table: TableRef,
    tab: Tab?,
    viewModel: OrderViewModel,
    showBack: Boolean,
    onBack: () -> Unit,
    onOpenTable: () -> Unit,
    onDeleteTable: () -> Unit,
    onClosed: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    outcome: @Composable () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    val productsById = remember(state.products) { state.products.associateBy { it.id } }
    // Confirm state belongs to one table: switching tables resets it.
    key(table.id) {
        Column(modifier = modifier.fillMaxSize()) {
            DetailHeader(table = table, showBack = showBack, onBack = onBack)
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { outcome() }
                if (!table.occupied) {
                    item {
                        MittPrimaryButton(label = "ABRIR MESA", onClick = onOpenTable)
                    }
                    item {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    item {
                        TwoTapAction(
                            label = "Eliminar mesa",
                            armedLabel = "CONFIRMAR ELIMINAR",
                            danger = false,
                            onConfirm = onDeleteTable,
                        )
                    }
                }
                if (tab != null && tab.lines.isNotEmpty()) {
                    item {
                        Text(text = "Cuenta abierta", style = MaterialTheme.typography.titleLarge)
                    }
                    items(tab.lines, key = { "line-${it.productId}" }) { line ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = "${line.qty}× ${line.name}",
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            MittMoneyText(cents = line.lineTotal)
                        }
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
                items(state.products, key = { "product-${it.id}" }) { product ->
                    DetailProductRow(
                        product = product,
                        qty = state.lines[product.id] ?: 0,
                        onIncrement = { viewModel.increment(product.id) },
                        onDecrement = { viewModel.decrement(product.id) },
                    )
                }
                // Keep the product name lookup honest when a line references
                // a product the refreshed catalog no longer carries.
                val missing = state.lines.keys.filter { productsById[it] == null }
                if (missing.isNotEmpty()) {
                    item {
                        Text(
                            text = "Algunos productos ya no están en el catálogo.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            StickyActionBar(
                table = table,
                tab = tab,
                viewModel = viewModel,
                onClosed = onClosed,
            )
        }
    }
}

@Composable
private fun DetailHeader(
    table: TableRef,
    showBack: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = if (showBack) 4.dp else 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (showBack) {
            TextButton(
                onClick = onBack,
                modifier = Modifier.heightIn(min = 48.dp).widthIn(min = 48.dp),
            ) {
                Text(text = "← VOLVER", maxLines = 1)
            }
        }
        Text(
            text = table.label,
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        MittStatusPill(occupied = table.occupied)
    }
}

// Sticky bottom bar: running total beside ANOTAR, the committed-order
// result state, and (open tab only) the two-tap CERRAR MESA.
@Composable
private fun StickyActionBar(
    table: TableRef,
    tab: Tab?,
    viewModel: OrderViewModel,
    onClosed: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val committed = state.lastOrder
                if (committed != null) {
                    Text(text = "PEDIDO ANOTADO", style = MaterialTheme.typography.titleMedium)
                    MittMoneyText(cents = committed.totalCents, style = TotalStyle)
                    if (state.lastSync != null) {
                        Text(
                            text = state.lastSync!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    MittSecondaryButton(
                        label = "NUEVO PEDIDO",
                        onClick = { viewModel.startNewOrder() },
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Column {
                            Text(text = "TOTAL", style = MaterialTheme.typography.labelLarge)
                            MittMoneyText(cents = state.runningTotalCents, style = TotalStyle)
                        }
                        MittPrimaryButton(
                            label = "ANOTAR",
                            onClick = { viewModel.submit() },
                            enabled = state.lines.isNotEmpty(),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (state.lastSync != null) {
                        Text(
                            text = state.lastSync!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (state.error != null) {
                    Text(
                        text = state.error!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (tab != null) {
                    TwoTapAction(
                        label = "CERRAR MESA",
                        armedLabel = "CONFIRMAR CIERRE · ${mittMoneyLabel(tab.totalCents())}",
                        danger = true,
                        onConfirm = { viewModel.closeTab(tab.id, table.id, onClosed) },
                    )
                }
            }
        }
    }
}

// Inline two-tap action (no dialog): the first tap turns the button into the
// confirm label and shows CANCELAR below it; the second tap runs onConfirm.
// The transition rule lives in the pure nextConfirm.
@Composable
private fun TwoTapAction(
    label: String,
    armedLabel: String,
    danger: Boolean,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var step by remember { mutableStateOf(ConfirmStep.IDLE) }
    fun tap(tap: ConfirmTap) {
        val next = nextConfirm(step, tap)
        step = next.step
        if (next.execute) onConfirm()
    }
    Column(modifier = modifier.fillMaxWidth()) {
        if (step == ConfirmStep.IDLE) {
            if (danger) {
                MittDangerButton(label = label, onClick = { tap(ConfirmTap.PRIMARY) })
            } else {
                MittSecondaryButton(label = label, onClick = { tap(ConfirmTap.PRIMARY) })
            }
        } else {
            MittDangerButton(label = armedLabel, onClick = { tap(ConfirmTap.PRIMARY) })
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
                onClick = { tap(ConfirmTap.CANCEL) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text("CANCELAR")
            }
        }
    }
}

@Composable
private fun DetailProductRow(
    product: Product,
    qty: Int,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MittCard(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
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
            OutlinedButton(
                onClick = onDecrement,
                enabled = product.available && qty > 0,
                shape = MaterialTheme.shapes.small,
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.size(48.dp),
            ) {
                Text(text = "−", fontSize = 24.sp)
            }
            Text(
                text = qty.toString(),
                style = FigureStyle,
                modifier = Modifier.widthIn(min = 24.dp),
            )
            OutlinedButton(
                onClick = onIncrement,
                enabled = product.available,
                shape = MaterialTheme.shapes.small,
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.size(48.dp),
            ) {
                Text(text = "+", fontSize = 24.sp)
            }
        }
    }
}
