package dev.mott.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.mott.app.ui.theme.ThemeMode

// Shell top bar per the master Header pattern (views/ui.tsx): display
// title in Bricolage (headlineMedium) plus the section subtitle, with a
// right slot carrying the CONECTADO pill — pulsing via the shared
// livePulse ring while paired — next to the Refrescar and theme-mode
// actions from the master sidebar (App.tsx:92-98).
@Composable
fun ShellHeader(
    section: AppSection,
    connected: Boolean,
    themeMode: ThemeMode,
    onRefresh: () -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pill = connectionPillState(paired = connected)
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = section.label,
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = section.subtitle(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            MittLivePill(
                text = pill.text,
                live = pill.live,
                modifier = Modifier.livePulse(enabled = pill.live),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onRefresh) { Text("REFRESCAR") }
            TextButton(onClick = { onThemeModeChange(nextThemeMode(themeMode)) }) {
                Text("TEMA: ${themeMode.label.uppercase()}")
            }
        }
    }
}

// Cycles Sistema -> Claro -> Oscuro -> Sistema for the compact shell
// toggle. T8 owns the full Claro/Oscuro picker in Personalizar.
fun nextThemeMode(current: ThemeMode): ThemeMode = when (current) {
    ThemeMode.SISTEMA -> ThemeMode.CLARO
    ThemeMode.CLARO -> ThemeMode.OSCURO
    ThemeMode.OSCURO -> ThemeMode.SISTEMA
}
