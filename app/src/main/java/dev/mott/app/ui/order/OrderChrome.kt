package dev.mott.app.ui.order

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.mott.app.ui.MittCard
import dev.mott.app.ui.MittSectionTitle

// Shared Operate-mode chrome: scanable headers, status that is never
// color-only, and error/empty/offline states for every screen.
// Table and stock status pills live in MittUi (MittStatusPill /
// MittStockPill); this file keeps the screen chrome on top of them.

@Composable
fun OfflineBanner(modifier: Modifier = Modifier) {
    MittCard(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val dotColor = MaterialTheme.colorScheme.primary
            Canvas(modifier = Modifier.size(14.dp)) {
                drawCircle(color = dotColor)
            }
            Text(
                text = "SIN CONEXIÓN — se guarda y sincroniza",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun OrderScreenHeader(
    title: String,
    isOffline: Boolean,
    shopName: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Hub brand name from state, never hardcoded: blank stays hidden.
        val shop = shopName?.trim().orEmpty()
        if (shop.isNotEmpty()) {
            Text(
                text = shop,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        MittSectionTitle(title = title)
        if (isOffline) {
            Spacer(modifier = Modifier.height(12.dp))
            OfflineBanner()
        }
    }
}

@Composable
fun OrderErrorState(
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "ATENCIÓN",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.error,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
            )
            if (actionLabel != null && onAction != null) {
                Button(onClick = onAction) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
fun OrderEmptyState(
    title: String,
    hint: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = hint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
