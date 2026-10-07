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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.mott.app.data.Pairing

// Conexión section for a paired device: server status plus the Figma
// "Cómo conectar" steps and brand surface, in card language. The unpaired
// entry stays the QR-first PairingScreen (pairing gate in MainActivity);
// brand editing is web-only, the phone only displays the hub brand.
@Composable
fun ConnectionSection(
    pairing: Pairing,
    modifier: Modifier = Modifier,
    shopName: String? = null,
    onUnpair: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MittSectionTitle(
            title = "Conexión",
            sub = "App enlazada con el servidor del bar",
        )
        MittCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = "Servidor", style = MaterialTheme.typography.titleLarge)
                MittLivePill(text = "Servidor activo")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = pairing.baseUrl,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val shop = shopName?.trim().orEmpty()
            if (shop.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Mostrando los colores de $shop",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        MittCard(modifier = Modifier.fillMaxWidth()) {
            Text(text = "Cómo conectar", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp))
            ConnectionSteps(
                listOf(
                    "Conectá el celular a la misma red Wi-Fi del bar.",
                    "Si cambia el servidor, desvinculá y escaneá el QR nuevo.",
                    "Mesas y productos se descargan solos al conectar.",
                ),
            )
        }
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
