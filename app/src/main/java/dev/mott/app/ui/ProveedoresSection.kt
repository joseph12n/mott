package dev.mott.app.ui

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.mott.app.data.ApiException
import dev.mott.app.data.PairingStore
import dev.mott.app.data.SalesRepo
import dev.mott.app.data.Supplier
import dev.mott.app.data.remote.ApiClient
import dev.mott.app.net.NetStatus
import dev.mott.app.ui.order.OfflineBanner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

// Proveedores parity with the mitt PC reduction: a flat add-supplier form
// (nombre + teléfono + nota), one selectable card per supplier showing
// contact info, and a detail card with edit (PATCH) + delete (DELETE).
// The hub stores NO per-supplier catalog or purchase ledger, so no
// balances render anywhere (the Figma mock suggests owed-balance areas,
// but the hub reduction dropped them: inventing them would be fake
// data). The shell header owns the title/subtitle, so this file renders
// the count line plus body only. Loads and mutations go through the
// shared SectionLoaders contract with retry; CancellationException is
// rethrown, never classified (R3-002).
@Composable
fun ProveedoresSection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    // Self-sufficient repo from the saved pairing (CatalogSection
    // precedent): no MainActivity wiring change needed.
    val repo = remember(appContext) {
        val pairingStore = PairingStore(appContext)
        SalesRepo(
            apiProvider = {
                pairingStore.get()?.let { pairing ->
                    ApiClient.build(pairing.baseUrl, pairing.token, logger = false)
                }
            },
            isOnline = { NetStatus.isOnline(appContext) },
        )
    }
    var suppliersState by remember { mutableStateOf<LoadState<List<Supplier>>>(LoadState.Loading) }
    var selIdRaw by remember { mutableStateOf<String?>(null) }
    var addName by remember { mutableStateOf("") }
    var addPhone by remember { mutableStateOf("") }
    var addNote by remember { mutableStateOf("") }
    var adminNote by remember { mutableStateOf<String?>(null) }
    var adminError by remember { mutableStateOf<String?>(null) }
    var adminFailure by remember { mutableStateOf<LoadFailureReason?>(null) }
    var adminRetry by remember { mutableStateOf<(() -> Unit)?>(null) }
    val scope = rememberCoroutineScope()

    fun refresh() {
        scope.launch {
            suppliersState = loadSuppliersState(repo::listSuppliers)
        }
    }
    LaunchedEffect(Unit) { refresh() }
    val suppliers = (suppliersState as? LoadState.Ready)?.data ?: emptyList()
    val loadFailure = (suppliersState as? LoadState.Failed)?.reason
    val selId = resolveSupplierSelection(suppliers, selIdRaw)
    val selected = suppliers.find { it.id == selId }

    // Edit form resets per selected supplier (PC effect parity).
    var editName by remember(selId) { mutableStateOf(selected?.name ?: "") }
    var editPhone by remember(selId) { mutableStateOf(selected?.phone ?: "") }
    var editNote by remember(selId) { mutableStateOf(selected?.note ?: "") }
    // Two-tap delete: the first tap arms, the second confirms.
    var deleteArm by remember(selId) { mutableStateOf(false) }

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

    fun addSupplier() {
        clearAdminOutcome()
        val invalid = validateSupplierInput(addName, addPhone, addNote)
        if (invalid != null) {
            adminError = invalid
            return
        }
        if (!checkOnline()) return
        val name = addName.trim()
        val phone = addPhone
        val note = addNote
        scope.launch {
            try {
                val created = repo.createSupplier(name, phone, note)
                selIdRaw = created.id
                addName = ""
                addPhone = ""
                addNote = ""
                adminNote = "Proveedor ${created.name} agregado."
                refresh()
            } catch (e: Exception) {
                reportAdminError(e, ::supplierSaveInlineCopy) { addSupplier() }
            }
        }
    }

    fun saveSupplier(sup: Supplier) {
        clearAdminOutcome()
        val invalid = validateSupplierInput(editName, editPhone, editNote)
        if (invalid != null) {
            adminError = invalid
            return
        }
        if (!checkOnline()) return
        val name = editName.trim()
        scope.launch {
            try {
                // Partial update: only changed fields travel (nil keeps
                // the stored value server-side).
                repo.patchSupplier(
                    sup.id,
                    name = name.takeIf { it != sup.name },
                    phone = editPhone.takeIf { it != sup.phone },
                    note = editNote.takeIf { it != sup.note },
                )
                adminNote = "Proveedor $name guardado."
                refresh()
            } catch (e: Exception) {
                reportAdminError(e, { status ->
                    supplierSaveInlineCopy(status) ?: supplierMissingInlineCopy(status)
                }) { saveSupplier(sup) }
            }
        }
    }

    fun deleteSupplier(sup: Supplier) {
        clearAdminOutcome()
        if (!deleteArm) {
            deleteArm = true
            return
        }
        if (!checkOnline()) {
            deleteArm = false
            return
        }
        scope.launch {
            try {
                repo.deleteSupplier(sup.id)
                selIdRaw = null
                deleteArm = false
                adminNote = "Proveedor ${sup.name} eliminado."
                refresh()
            } catch (e: Exception) {
                deleteArm = false
                reportAdminError(e, ::supplierMissingInlineCopy) { deleteSupplier(sup) }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        // Shell-owned title: only the offline banner plus the count line
        // live here (the shell subtitle already names the section).
        if (!NetStatus.isOnline(context)) {
            OfflineBanner()
            Spacer(modifier = Modifier.height(4.dp))
        }
        Text(
            text = "${suppliers.size} proveedores registrados",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(12.dp))
        MittCard(modifier = Modifier.fillMaxWidth()) {
            Text(text = "Agregar proveedor", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = addName,
                onValueChange = { addName = it },
                label = { Text("Nombre del proveedor") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = addPhone,
                onValueChange = { addPhone = it },
                label = { Text("Teléfono") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = addNote,
                onValueChange = { addNote = it },
                label = { Text("Nota (rubro, horario…)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(modifier = Modifier.height(12.dp))
            MittPrimaryButton(
                label = "AGREGAR PROVEEDOR",
                onClick = ::addSupplier,
                enabled = addName.isNotBlank(),
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
        loadFailure?.let { reason ->
            MittLoadErrorCard(
                reason = reason,
                onRetry = ::refresh,
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
        adminFailure?.let { reason ->
            MittLoadErrorCard(
                reason = reason,
                onRetry = { adminRetry?.invoke() },
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
        if (suppliers.isEmpty() && loadFailure == null) {
            MittCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Agrega tu primer proveedor.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            for (supplier in suppliers) {
                MittClickableCard(
                    onClick = { selIdRaw = supplier.id },
                    selected = supplier.id == selId,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = supplier.name,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = supplier.phone.ifBlank { "Sin teléfono" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (supplier.note.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = supplier.note,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
        val sup = selected
        if (sup != null) {
            MittCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = sup.name,
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        if (sup.phone.isNotBlank()) {
                            Text(
                                text = sup.phone,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            MittLivePill(text = "Sin teléfono", live = false)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                MittDangerButton(
                    label = if (deleteArm) "CONFIRMAR ELIMINACIÓN" else "ELIMINAR",
                    onClick = { deleteSupplier(sup) },
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = editName,
                    onValueChange = { editName = it },
                    label = { Text("Nombre") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = editPhone,
                    onValueChange = { editPhone = it },
                    label = { Text("Teléfono") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = editNote,
                    onValueChange = { editNote = it },
                    label = { Text("Nota") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(12.dp))
                MittPrimaryButton(
                    label = "GUARDAR CAMBIOS",
                    onClick = { saveSupplier(sup) },
                    enabled = supplierDirty(sup, editName, editPhone, editNote) &&
                        editName.trim().isNotEmpty(),
                )
            }
        } else if (suppliers.isNotEmpty()) {
            MittCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Selecciona o agrega un proveedor.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}
