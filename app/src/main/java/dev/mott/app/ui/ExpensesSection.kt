package dev.mott.app.ui

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.mott.app.data.ApiException
import dev.mott.app.data.ExpenseItem
import dev.mott.app.data.ExpensesRepo
import dev.mott.app.ui.order.OrderEmptyState
import dev.mott.app.ui.theme.TotalStyle
import kotlinx.coroutines.launch

// Gastos section in Figma card language: total hero, add form, rows.
// Adds ride the offline outbox (qty is always one unit on mobile) and
// report SINCRONIZADO / PENDIENTE like ANOTAR. Hub failures surface as a
// short line; the list fail-softs to cache or empty, never crashes.
@Composable
fun ExpensesSection(
    repo: ExpensesRepo,
    modifier: Modifier = Modifier,
) {
    var items by remember { mutableStateOf(emptyList<ExpenseItem>()) }
    var concept by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val amountCents = remember(amount) { parseAmountToCents(amount) }

    fun refresh() {
        scope.launch { items = runCatching { repo.list() }.getOrDefault(emptyList()) }
    }
    LaunchedEffect(Unit) { refresh() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        MittSectionTitle(title = "Gastos", sub = "Egresos registrados del servicio")
        Spacer(modifier = Modifier.height(12.dp))
        MittCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Total gastado",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MittMoneyText(cents = items.sumOf { it.costCents }, style = TotalStyle)
        }
        Spacer(modifier = Modifier.height(12.dp))
        MittCard(modifier = Modifier.fillMaxWidth()) {
            Text(text = "Registrar gasto", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = concept,
                onValueChange = { concept = it },
                label = { Text("Concepto") },
                placeholder = { Text("Hielo, limpieza, cambio") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it },
                label = { Text("Monto") },
                placeholder = { Text("0.00") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            Spacer(modifier = Modifier.height(12.dp))
            MittPrimaryButton(
                label = if (saving) "GUARDANDO..." else "REGISTRAR GASTO",
                onClick = {
                    val cents = amountCents ?: return@MittPrimaryButton
                    val text = concept.trim()
                    if (text.isEmpty()) return@MittPrimaryButton
                    saving = true
                    scope.launch {
                        note = try {
                            repo.add(text, qty = 1.0, costCents = cents)
                        } catch (_: IllegalArgumentException) {
                            "REVISAR CONCEPTO Y MONTO"
                        } catch (_: ApiException) {
                            "ERROR DEL SERVIDOR, SE REINTENTA"
                        } finally {
                            saving = false
                        }
                        if (note == "SINCRONIZADO" || (note?.startsWith("PENDIENTE") == true)) {
                            concept = ""
                            amount = ""
                        }
                        refresh()
                    }
                },
                enabled = !saving && concept.isNotBlank() && amountCents != null,
            )
            if (note != null) {
                Text(
                    text = note!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        if (items.isEmpty()) {
            OrderEmptyState(
                title = "Sin gastos",
                hint = "Todavía no hay egresos registrados en este servicio.",
                modifier = Modifier.weight(1f),
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items, key = { it.id }) { expense ->
                    MittCard(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = expense.description,
                                    style = MaterialTheme.typography.titleLarge,
                                )
                                if (expense.date.isNotBlank()) {
                                    Text(
                                        text = expense.date,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            MittMoneyText(cents = expense.costCents)
                        }
                    }
                }
            }
        }
    }
}
