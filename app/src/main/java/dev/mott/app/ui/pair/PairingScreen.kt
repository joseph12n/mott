package dev.mott.app.ui.pair

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import dev.mott.app.data.PairingStore
import dev.mott.app.data.parsePairingCode
import dev.mott.app.ui.order.OrderScreenHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

// Pairing flow states, from idle to a saved pairing.
enum class PairingStatus {
    IDLE,
    SCANNING,
    VALIDATING,
    SUCCESS,
    ERROR,
}

// Validates a parsed pairing against GET /api/health carrying the token.
// ApiClient skips auth on health by design, so this uses a direct OkHttp
// call: any 2xx with the Bearer token attached counts as paired.
suspend fun validatePairing(baseUrl: String, token: String): Boolean {
    return withContext(Dispatchers.IO) {
        runCatching {
            val client = OkHttpClient()
            val request = Request.Builder()
                .url(baseUrl.trimEnd('/') + "/api/health")
                .header("Authorization", "Bearer $token")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                response.code in 200..299
            }
        }.getOrDefault(false)
    }
}

// First-run screen: pair this device with the mitt hub over QR or paste.
// Scanning uses the zxing IntentIntegrator pattern via ScanContract (no
// CameraX); pasting accepts the same code text. All failures surface as
// fixed Spanish strings — raw exceptions never reach the UI.
@Composable
fun PairingScreen(
    store: PairingStore,
    onPaired: () -> Unit,
    modifier: Modifier = Modifier,
    validate: suspend (baseUrl: String, token: String) -> Boolean = ::validatePairing,
) {
    var status by remember { mutableStateOf(PairingStatus.IDLE) }
    var error by remember { mutableStateOf<String?>(null) }
    var pasted by remember { mutableStateOf("") }
    var showCameraRationale by remember { mutableStateOf(false) }
    var pairedUrl by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    fun handleCode(raw: String) {
        scope.launch {
            status = PairingStatus.VALIDATING
            error = null
            val parsed = try {
                parsePairingCode(raw)
            } catch (_: IllegalArgumentException) {
                status = PairingStatus.ERROR
                error = "CÓDIGO NO VÁLIDO. ESCANEÁ DE NUEVO O PEGALO."
                return@launch
            }
            val ok = try {
                validate(parsed.baseUrl, parsed.token)
            } catch (_: Exception) {
                false
            }
            if (!ok) {
                status = PairingStatus.ERROR
                error = "NO SE PUDO CONECTAR CON EL SERVIDOR. REVISÁ LA RED."
                return@launch
            }
            store.save(parsed.baseUrl, parsed.token)
            pairedUrl = parsed.baseUrl
            status = PairingStatus.SUCCESS
        }
    }

    // IntentIntegrator pattern: fire the scanner intent, read its contents.
    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val contents = result.contents
        if (contents == null) {
            status = PairingStatus.IDLE
        } else {
            handleCode(contents)
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            showCameraRationale = false
            status = PairingStatus.SCANNING
            scanLauncher.launch(
                ScanOptions().setPrompt("Apuntá al QR del servidor"),
            )
        } else {
            showCameraRationale = true
        }
    }

    fun startScan() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            showCameraRationale = false
            status = PairingStatus.SCANNING
            scanLauncher.launch(
                ScanOptions().setPrompt("Apuntá al QR del servidor"),
            )
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OrderScreenHeader(title = "Conectar servidor", isOffline = false)
        Text(
            text = "Escaneá el QR del servidor o pegá el código para empezar a anotar.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Button(
            onClick = ::startScan,
            enabled = status != PairingStatus.VALIDATING,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
        ) {
            Text("ESCANEAR QR")
        }
        if (showCameraRationale) {
            Text(
                text = "SE NECESITA LA CÁMARA PARA ESCANEAR EL QR. ACEPTÁ EL PERMISO PARA CONTINUAR.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        OutlinedTextField(
            value = pasted,
            onValueChange = { pasted = it },
            label = { Text("Código de pairing") },
            placeholder = { Text("mitt://pair?url=... o http://...|token") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
        )
        OutlinedButton(
            onClick = { handleCode(pasted) },
            enabled = pasted.isNotBlank() && status != PairingStatus.VALIDATING,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
        ) {
            Text("PEGAR CÓDIGO")
        }
        when (status) {
            PairingStatus.VALIDATING, PairingStatus.SCANNING -> {
                Text(
                    text = "CONECTANDO...",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            PairingStatus.ERROR -> {
                Text(
                    text = error ?: "CÓDIGO NO VÁLIDO. ESCANEÁ DE NUEVO O PEGALO.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            PairingStatus.SUCCESS -> {
                Text(
                    text = "CONECTADO A ${pairedUrl ?: ""}",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.tertiary,
                )
                Button(
                    onClick = onPaired,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp),
                ) {
                    Text("CONTINUAR")
                }
            }
            PairingStatus.IDLE -> Unit
        }
        Spacer(modifier = Modifier.weight(1f))
        TextButton(onClick = onPaired) {
            Text("OMITIR POR AHORA")
        }
    }
}
