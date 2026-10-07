package dev.mott.app.ui.order

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.mott.app.domain.Product
import dev.mott.app.money.Money
import dev.mott.app.ui.theme.FigureStyle
import dev.mott.app.ui.theme.TotalStyle

// Step 2 of the order flow: add products with +/- steppers. The bottom
// bar stays visible with the running total and CONFIRMAR, disabled
// until at least one line exists.
@Composable
fun ProductsScreen(
    state: OrderUiState,
    onIncrement: (String) -> Unit,
    onDecrement: (String) -> Unit,
    onConfirm: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    shopName: String? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        TextButton(onClick = onBack) {
            Text("ATRÁS")
        }
        val tableLabel = state.selectedTable?.label
        OrderScreenHeader(
            title = if (tableLabel != null) "Pedido · $tableLabel" else "Pedido",
            isOffline = state.isOffline,
            shopName = shopName,
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (tableLabel == null) {
            OrderErrorState(
                message = "Elegir una mesa para continuar",
                actionLabel = "ELEGIR MESA",
                onAction = onBack,
                modifier = Modifier.weight(1f),
            )
        } else if (state.products.isEmpty()) {
            OrderEmptyState(
                title = "Sin productos",
                hint = "Todavía no hay productos cargados. Revisar la conexión e intentar de nuevo.",
                modifier = Modifier.weight(1f),
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.products, key = { it.id }) { product ->
                    ProductRow(
                        product = product,
                        qty = state.lines[product.id] ?: 0,
                        onIncrement = { onIncrement(product.id) },
                        onDecrement = { onDecrement(product.id) },
                    )
                    HorizontalDivider()
                }
            }
            if (state.error != null) {
                Text(
                    text = state.error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            OrderBottomBar(
                totalCents = state.runningTotalCents,
                confirmEnabled = state.lines.isNotEmpty(),
                onConfirm = onConfirm,
            )
        }
    }
}

@Composable
private fun ProductRow(
    product: Product,
    qty: Int,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
            Text(
                text = "$ ${Money.formatCents(product.priceCents)}",
                style = FigureStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!product.available) {
                Text(
                    text = "NO DISPONIBLE",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        Stepper(
            qty = qty,
            enabled = product.available,
            onIncrement = onIncrement,
            onDecrement = onDecrement,
        )
    }
}

@Composable
private fun Stepper(
    qty: Int,
    enabled: Boolean,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        OutlinedButton(
            onClick = onDecrement,
            enabled = enabled && qty > 0,
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
            enabled = enabled,
            modifier = Modifier.size(48.dp),
        ) {
            Text(text = "+", fontSize = 24.sp)
        }
    }
}

@Composable
private fun OrderBottomBar(
    totalCents: Long,
    confirmEnabled: Boolean,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 4.dp,
    ) {
        Column(modifier = Modifier.padding(vertical = 12.dp)) {
            Text(
                text = "TOTAL",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "$ ${Money.formatCents(totalCents)}",
                style = TotalStyle,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onConfirm,
                enabled = confirmEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
            ) {
                Text("CONFIRMAR")
            }
        }
    }
}
