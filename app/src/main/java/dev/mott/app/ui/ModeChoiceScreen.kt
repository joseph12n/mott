package dev.mott.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.mott.app.data.AppMode
import dev.mott.app.data.capabilitiesFor

// First-run mode chooser. The PC is optional now, so this is the one
// decision that shapes everything else: the phone alone (full catalog
// control, works with no Wi-Fi) or a client of the PC hub (the PC owns the
// catalog). Changeable later from Conexión.
@Composable
fun ModeChoiceScreen(
    onChoose: (AppMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MittSectionTitle(
            title = "Cómo vas a usar mott",
            sub = "Elegí una vez; podés cambiarlo después desde Conexión.",
        )
        MittCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = capabilitiesFor(AppMode.MOBILE).label,
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "El teléfono funciona solo: mesas, productos, proveedores y " +
                    "marca se administran acá, sin PC y sin Wi-Fi.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            MittPrimaryButton(
                label = "USAR SOLO EL MÓVIL",
                onClick = { onChoose(AppMode.MOBILE) },
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }
        MittCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = capabilitiesFor(AppMode.HUB).label,
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "El teléfono se enlaza con la PC del bar por QR: la PC " +
                    "guarda mesas, productos y proveedores, y el teléfono toma " +
                    "pedidos, cierra mesas y carga gastos.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            MittSecondaryButton(
                label = "CONECTAR CON LA PC (QR)",
                onClick = { onChoose(AppMode.HUB) },
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }
        Text(
            text = "Con PC, las altas y bajas del catálogo se hacen en la PC; " +
                "el teléfono queda para operar el servicio.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
