package dev.mott.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.mott.app.data.ApiException
import dev.mott.app.data.PairingStore
import dev.mott.app.data.remote.ApiClient
import dev.mott.app.domain.Product
import dev.mott.app.net.NetStatus
import dev.mott.app.ui.order.OfflineBanner
import dev.mott.app.ui.order.OrderEmptyState
import dev.mott.app.ui.order.OrderScreenHeader
import dev.mott.app.ui.order.OrderUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

// Catalogo parity with the mitt PC reduction: a flat add-product form
// (nombre + precio) plus one row per product. The hub has NO category
// field, so the list stays flat with no client-side grouping (the design
// mock groups by category, but the hub reduction flattened that away);
// the hub has NO product DELETE, so the trash control PATCHes
// available=false and unavailable rows stay listed, muted with their
// Sin-stock pill, untappable. Tapping an available product keeps the
// mozo flow: it travels to Mesas as the pending pick.
@Composable
fun CatalogSection(
    state: OrderUiState,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
    shopName: String? = null,
    // T3 shell: the ShellHeader owns the title/subtitle.
    showHeader: Boolean = true,
    // Admin seam: production falls back to a self-sufficient repo built
    // from the saved pairing (no extra wiring needed); tests and previews
    // inject their own.
    catalogAdmin: CatalogAdminRepo? = null,
    // Refresh hook after a mutation lands (production: reload the hub
    // catalog through the ViewModel so the new row shows up at once).
    onCatalogChanged: () -> Unit = {},
    onNavigateConnection: () -> Unit = {},
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val admin = catalogAdmin ?: remember(appContext) {
        val pairingStore = PairingStore(appContext)
        CatalogAdminRepo(
            apiProvider = {
                pairingStore.get()?.let { pairing ->
                    ApiClient.build(pairing.baseUrl, pairing.token, logger = false)
                }
            },
            isOnline = { NetStatus.isOnline(appContext) },
        )
    }
    var newName by remember { mutableStateOf("") }
    var newPrice by remember { mutableStateOf("") }
    var adminNote by remember { mutableStateOf<String?>(null) }
    var adminError by remember { mutableStateOf<String?>(null) }
    var adminFailure by remember { mutableStateOf<LoadFailureReason?>(null) }
    var adminRetry by remember { mutableStateOf<(() -> Unit)?>(null) }
    val scope = rememberCoroutineScope()
    val priceCents = remember(newPrice) { parseProductPriceToCents(newPrice) }

    // Shared admin error mapping, same contract as the Mesas section:
    // named 422/404 copy inline, everything else through SectionLoaders
    // with retry. Cancellation keeps propagating (R3-002), never silent,
    // never crashing.
    fun reportAdminError(e: Throwable, inlineCopy: (Int) -> String?, retry: () -> Unit) {
        if (e is CancellationException) throw e
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

    fun submitProduct() {
        clearAdminOutcome()
        val invalid = validateProductInput(newName, priceCents)
        if (invalid != null) {
            adminError = invalid
            return
        }
        if (!checkOnline()) return
        val name = newName.trim()
        val cents = priceCents ?: return
        scope.launch {
            try {
                val created = admin.createProduct(name, cents)
                onCatalogChanged()
                newName = ""
                newPrice = ""
                adminNote = "Producto ${created.name} agregado."
            } catch (e: Exception) {
                reportAdminError(e, ::productCreateInlineCopy) { submitProduct() }
            }
        }
    }

    // Trash control: marks the product unavailable, never deletes it (the
    // hub exposes no product DELETE; the row stays listed, muted).
    fun markUnavailable(product: Product) {
        clearAdminOutcome()
        if (!checkOnline()) return
        scope.launch {
            try {
                admin.setAvailable(product.id, false)
                onCatalogChanged()
                adminNote = "Producto ${product.name} sin stock."
            } catch (e: Exception) {
                reportAdminError(e, ::productToggleInlineCopy) { markUnavailable(product) }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        // Shell-owned title: skip our own header but keep the offline
        // banner so the offline state never goes silent.
        if (showHeader) {
            OrderScreenHeader(title = "Catálogo", isOffline = state.isOffline, shopName = shopName)
        } else if (state.isOffline) {
            OfflineBanner()
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            // The shell subtitle already says "Lista de precios y stock",
            // so under the shell only the Mesas hint remains.
            text = if (showHeader) {
                "Lista de precios y stock. Tocá un producto para agregarlo en Mesas."
            } else {
                "Tocá un producto para agregarlo en Mesas."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(12.dp))
        MittCard(modifier = Modifier.fillMaxWidth()) {
            Text(text = "Agregar producto", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                label = { Text("Nombre del producto") },
                placeholder = { Text("Fernet, cerveza, ...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = newPrice,
                onValueChange = { newPrice = it },
                label = { Text("Precio") },
                placeholder = { Text("0.00") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            Spacer(modifier = Modifier.height(12.dp))
            MittPrimaryButton(
                label = "AGREGAR PRODUCTO",
                onClick = ::submitProduct,
                enabled = newName.isNotBlank() && priceCents != null,
            )
            val outcome = adminError ?: adminNote
            if (outcome != null) {
                Text(
                    text = outcome,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (adminError != null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        adminFailure?.let { reason ->
            MittLoadErrorCard(
                reason = reason,
                onRetry = { adminRetry?.invoke() },
                onRePair = onNavigateConnection,
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
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
                            // Trash marks the product unavailable (PATCH
                            // available=false); only on sellable rows, like
                            // the Mesas chip X.
                            if (product.available) {
                                TextButton(
                                    onClick = { markUnavailable(product) },
                                    modifier = Modifier.height(32.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                ) {
                                    Text(text = "✕", fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
