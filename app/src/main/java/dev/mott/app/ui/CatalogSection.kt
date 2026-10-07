package dev.mott.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.mott.app.ui.order.OrderEmptyState
import dev.mott.app.ui.order.OrderScreenHeader
import dev.mott.app.ui.order.OrderUiState

// No product add/edit here: catalog admin is web-only.
@Composable
fun CatalogSection(
    state: OrderUiState,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
    shopName: String? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        OrderScreenHeader(title = "Catálogo", isOffline = state.isOffline, shopName = shopName)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Lista de precios y stock. Tocá un producto para agregarlo en Mesas.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(12.dp))
        if (state.products.isEmpty()) {
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
                    MittCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (product.available) {
                                    Modifier
                                        .clip(MaterialTheme.shapes.large)
                                        .clickable { onPick(product.id) }
                                } else {
                                    Modifier
                                },
                            ),
                    ) {
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
                            }
                            MittStockPill(available = product.available)
                        }
                    }
                }
            }
        }
    }
}
