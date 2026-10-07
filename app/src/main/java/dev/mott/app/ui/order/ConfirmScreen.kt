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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.mott.app.money.Money
import dev.mott.app.ui.theme.FigureStyle
import dev.mott.app.ui.theme.TotalStyle

// Step 3 of the order flow: review the summary and write it down.
// ANOTAR validates and exposes the ClosedTab payload; wiring the
// payload into the sync queue is M6.
@Composable
fun ConfirmScreen(
    state: OrderUiState,
    onConfirm: () -> Unit,
    onNewOrder: () -> Unit,
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
        val committed = state.lastOrder
        if (committed != null) {
            CommittedState(
                tableLabel = state.selectedTable?.label ?: committed.tableId,
                totalCents = committed.totalCents,
                lineCount = committed.lines.size,
                isOffline = state.isOffline,
                onNewOrder = onNewOrder,
                modifier = Modifier.weight(1f),
            )
            return
        }
        val tableLabel = state.selectedTable?.label
        val productsById = state.products.associateBy { it.id }
        OrderScreenHeader(title = "Confirmar pedido", isOffline = state.isOffline, shopName = shopName)
        Spacer(modifier = Modifier.height(8.dp))
        if (tableLabel == null || state.lines.isEmpty()) {
            val message = if (tableLabel == null) {
                "Elegir una mesa para continuar"
            } else {
                "Agregar al menos un producto"
            }
            OrderErrorState(
                message = message,
                actionLabel = "VOLVER",
                onAction = onBack,
                modifier = Modifier.weight(1f),
            )
            return
        }
        Text(
            text = tableLabel,
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(modifier = Modifier.height(12.dp))
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(state.lines.entries.toList(), key = { it.key }) { (productId, qty) ->
                val product = productsById[productId]
                val name = product?.name ?: productId
                val lineTotal = (product?.priceCents ?: 0L) * qty
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "$name × $qty",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "$ ${Money.formatCents(lineTotal)}",
                        style = FigureStyle,
                    )
                }
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
        Text(
            text = "TOTAL",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "$ ${Money.formatCents(state.runningTotalCents)}",
            style = TotalStyle,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = onConfirm,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
        ) {
            Text("ANOTAR")
        }
    }
}

@Composable
private fun CommittedState(
    tableLabel: String,
    totalCents: Long,
    lineCount: Int,
    isOffline: Boolean,
    onNewOrder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "PEDIDO ANOTADO",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.tertiary,
        )
        Text(
            text = "$tableLabel · $lineCount productos",
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = "$ ${Money.formatCents(totalCents)}",
            style = TotalStyle,
        )
        if (isOffline) {
            OfflineBanner()
        }
        Spacer(modifier = Modifier.weight(1f))
        Button(
            onClick = onNewOrder,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
        ) {
            Text("NUEVO PEDIDO")
        }
    }
}
