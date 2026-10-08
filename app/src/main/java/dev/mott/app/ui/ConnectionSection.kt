package dev.mott.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import dev.mott.app.data.Pairing

// Conexión section for a paired device, mobile side of the PC pairing: the
// current pairing state (hub URL with copy action, bar name), the named
// connection status (T1 LoadFailureReason vocabulary), the 3-step mobile
// guide, three KPI cards with honest phone-side numbers, and DESVINCULAR.
// The unpaired entry stays the QR-first PairingScreen (pairing gate in
// MainActivity); brand editing is web-only, the phone only displays it.
//
// NOTE: no "Registrar todas las mesas" button here. The Figma mock faked it
// as a local flag flip, and the hub exposes no bulk-register route (only
// POST /api/tables per table in mitt internal/api/tables.go), so there is
// nothing honest to wire it to on mobile.
@Composable
fun ConnectionSection(
    pairing: Pairing,
    modifier: Modifier = Modifier,
    shopName: String? = null,
    onUnpair: () -> Unit = {},
    // T3 shell: the ShellHeader owns the title/subtitle.
    showHeader: Boolean = true,
    // Named failure to surface, if the host observed one (null = no known
    // failure, reads as an active server). Defaults keep the MainActivity
    // call site unchanged.
    status: LoadFailureReason? = null,
    // Phone-side numbers for the KPI cards (null = not wired yet, cards
    // render "—" instead of fake zeros).
    kpis: ConnectionKpis? = null,
) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Shell-owned title: the shell subtitle reuses this same copy.
        if (showHeader) {
            MittSectionTitle(
                title = "Conexión",
                sub = "App enlazada con el servidor del bar",
            )
        }
        MittCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = "Servidor", style = MaterialTheme.typography.titleLarge)
                MittLivePill(text = connectionPillText(status), live = status == null)
            }
            Spacer(modifier = Modifier.height(8.dp))
            val shop = shopName?.trim().orEmpty()
            if (shop.isNotEmpty()) {
                Text(
                    text = shop,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            Text(
                text = pairing.baseUrl,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            MittSecondaryButton(
                label = connectionCopyLabel(copied),
                onClick = {
                    clipboard.setText(AnnotatedString(pairing.baseUrl))
                    copied = true
                },
            )
        }
        // Named failure block: only a rejected token (hub restart rotates
        // it) is fixable from here — DESVINCULAR drives re-pairing through
        // the pairing gate, so the shortcut reuses it to ask for a new QR.
        if (status != null) {
            MittCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = status.title(),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = status.message(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (status.showRePair()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    MittSecondaryButton(
                        label = "PEDIR QR NUEVO",
                        onClick = onUnpair,
                    )
                }
            }
        }
        MittCard(modifier = Modifier.fillMaxWidth()) {
            Text(text = "Cómo conectar", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp))
            ConnectionSteps(connectionSteps())
        }
        ConnectionKpiCards(kpis = kpis)
        MittCard(modifier = Modifier.fillMaxWidth()) {
            Text(text = "Dispositivo", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Para usar otro servidor, desvinculá este primero.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            MittDangerButton(label = "DESVINCULAR", onClick = onUnpair)
        }
    }
}

// Three KPI cards with honest phone-side numbers: synced tables and products
// from the cached catalog snapshot, offline queue depth from the outbox.
// Unwired (null) renders "—", never a fake zero.
@Composable
private fun ConnectionKpiCards(kpis: ConnectionKpis?, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MittCard(modifier = Modifier.weight(1f)) {
            ConnectionKpiValue(
                label = "Mesas sincronizadas",
                value = kpis?.tableCount?.toString() ?: "—",
                caption = "descargadas del servidor",
            )
        }
        MittCard(modifier = Modifier.weight(1f)) {
            ConnectionKpiValue(
                label = "Productos",
                value = kpis?.productCount?.toString() ?: "—",
                caption = "listos para anotar",
            )
        }
        MittCard(modifier = Modifier.weight(1f)) {
            val pending = kpis?.pendingCount
            ConnectionKpiValue(
                label = "Cola offline",
                value = when (pending) {
                    null -> "—"
                    0 -> "Al día"
                    else -> pending.toString()
                },
                caption = when (pending) {
                    null -> "sin datos todavía"
                    0 -> "nada por sincronizar"
                    else -> "por sincronizar"
                },
            )
        }
    }
}

@Composable
private fun ConnectionKpiValue(label: String, value: String, caption: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = value,
        style = MaterialTheme.typography.headlineMedium,
    )
    Text(
        text = caption,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ConnectionSteps(steps: List<String>, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        steps.forEachIndexed { i, step ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = (i + 1).toString(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = step,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
