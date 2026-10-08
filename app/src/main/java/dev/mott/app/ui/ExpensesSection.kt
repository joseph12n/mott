package dev.mott.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
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
import dev.mott.app.data.ExpenseItem
import dev.mott.app.data.ExpensesRepo
import dev.mott.app.ui.order.OrderEmptyState
import dev.mott.app.ui.theme.TotalStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

// Gastos section in Figma card language: header-right total, add form,
// divided rows. Mirrors the mitt PC Gastos reduction: each line totals
// qty x unit cost, rows have no delete control (the hub exposes GET + POST
// /api/expenses only), and the form stays concepto+monto with qty
// defaulting to 1. Adds ride the offline outbox and report SINCRONIZADO /
// PENDIENTE like ANOTAR. Loads go through the shared SectionLoaders
// contract with retry; CancellationException is rethrown, never classified
// (R3-002); the Sin-gastos empty state renders only without failure, never
// together with the failure card (R3-003).
@Composable
fun ExpensesSection(
    repo: ExpensesRepo,
    modifier: Modifier = Modifier,
    onNavigateConnection: () -> Unit = {},
    // T3 shell: the ShellHeader owns the title/subtitle, and its
    // Refrescar bumps refreshSignal to re-trigger the list load.
    showHeader: Boolean = true,
    refreshSignal: Int = 0,
) {
    var items by remember { mutableStateOf(emptyList<ExpenseItem>()) }
    var concept by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    // Named load failure (token_invalido / sin_servidor / error_inesperado)
    // instead of a silent empty list: the user sees why and can retry.
    var failure by remember { mutableStateOf<LoadFailureReason?>(null) }
    val scope = rememberCoroutineScope()
    val amountCents = remember(amount) { parseAmountToCents(amount) }
    val totalCents = expensesTotal(items)

    fun refresh() {
        scope.launch {
            // Cancellation-safe loader: only Ready/Failed come back;
            // cancellation propagates out of the launch untouched.
            when (val loaded = loadExpensesState(repo::list)) {
                is LoadState.Ready -> {
                    items = loaded.data
                    failure = null
                }
                is LoadState.Failed -> failure = loaded.reason
                LoadState.Loading -> Unit
            }
        }
    }
    LaunchedEffect(refreshSignal) { refresh() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        // Total on the right of the header, like the web master: the
        // shell subtitle reuses the same "Egresos..." copy.
        if (showHeader) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    MittSectionTitle(title = "Gastos", sub = "Egresos registrados del servicio")
                }
                MittMoneyText(cents = totalCents, style = TotalStyle)
            }
            Spacer(modifier = Modifier.height(12.dp))
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MittMoneyText(cents = totalCents, style = TotalStyle)
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
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
                        try {
                            // Mobile posts a single unit per gasto (qty =
                            // 1); the web asks for qty explicitly.
                            note = repo.add(text, qty = 1.0, costCents = cents)
                        } catch (e: CancellationException) {
                            // R3-002: cancellation keeps propagating, it is
                            // never classified into a note.
                            throw e
                        } catch (e: Exception) {
                            note = mapExpenseSaveFailure(e)
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
        failure?.let { reason ->
            MittLoadErrorCard(
                reason = reason,
                onRetry = ::refresh,
                onRePair = onNavigateConnection,
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
        // R3-003: the empty state renders only without failure, so it
        // never shows together with the failure card above.
        if (shouldShowExpensesEmpty(items, failure)) {
            OrderEmptyState(
                title = "Sin gastos",
                hint = "Todavía no hay egresos registrados en este servicio.",
                modifier = Modifier.weight(1f),
            )
        } else if (items.isNotEmpty()) {
            MittCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                LazyColumn {
                    itemsIndexed(items, key = { _, expense -> expense.id }) { index, expense ->
                        if (index > 0) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = expense.description,
                                    style = MaterialTheme.typography.titleLarge,
                                )
                                Text(
                                    text = expenseSubline(expense),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            MittMoneyText(cents = expenseLineTotal(expense))
                        }
                    }
                }
            }
        }
    }
}
