package dev.mott.app.ui.order

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// Step 1 of the order flow: pick a table. Tap selects and advances.
// Occupied tables stay selectable so waiters can add to an open tab.
@Composable
fun TablesScreen(
    state: OrderUiState,
    onSelectTable: (String) -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    shopName: String? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        OrderScreenHeader(title = "Elegir mesa", isOffline = state.isOffline, shopName = shopName)
        Spacer(modifier = Modifier.height(16.dp))
        if (state.error != null && state.tables.isEmpty()) {
            OrderErrorState(message = state.error, modifier = Modifier.weight(1f))
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
                    TableCard(
                        table = table,
                        selected = table.id == state.selectedTableId,
                        onClick = {
                            onSelectTable(table.id)
                            onNext()
                        },
                    )
                }
            }
        }
        if (state.error != null && state.tables.isNotEmpty()) {
            Text(
                text = state.error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun TableCard(
    table: TableRef,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.heightIn(min = 120.dp),
        border = if (selected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = table.label,
                style = MaterialTheme.typography.headlineSmall,
            )
            OccupancyLabel(occupied = table.occupied)
        }
    }
}
