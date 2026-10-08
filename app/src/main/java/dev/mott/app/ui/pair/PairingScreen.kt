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
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import dev.mott.app.data.PairingStore
import dev.mott.app.data.parsePairingCode
import dev.mott.app.ui.MittCard
import dev.mott.app.ui.MittPrimaryButton
import dev.mott.app.ui.MittSecondaryButton
import dev.mott.app.ui.MittSectionTitle
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

// Pairing validation outcome: Ok saves the pairing, BadToken means the
// QR is stale, Unreachable means the phone cannot reach the hub.
sealed interface PairResult {
    data object Ok : PairResult
    data object BadToken : PairResult
    data object Unreachable : PairResult
}

// Validates a parsed pairing against the AUTHED GET /api/products carrying
// the token, mirroring the mitt web client. GET /api/health is public (the
// hub exempts it from auth) and the hub rotates its token every run, so a
// health check would happily accept a stale token that then 401s on every
// real call: 2xx on products means paired, 401/403 mean a stale token,
// anything else (IO/timeout/cleartext) is unreachable.
suspend fun validatePairing(baseUrl: String, token: String): PairResult {
    return withContext(Dispatchers.IO) {
        runCatching {
            val client = OkHttpClient()
            val request = Request.Builder()
                .url(baseUrl.trimEnd('/') + "/api/products")
                .header("Authorization", "Bearer $token")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                when (response.code) {
                    in 200..299 -> PairResult.Ok
                    401, 403 -> PairResult.BadToken
                    else -> PairResult.Unreachable
                }
            }
        }.getOrDefault(PairResult.Unreachable)
    }
}

// First-run screen: pair this device with the mitt hub over QR or paste.
// Scanning uses the Play services code scanner (framed viewfinder UI owned
// by Google, handles dense QR codes); pasting accepts the same code text.
// All failures surface as fixed Spanish strings — raw exceptions never
// reach the UI.
@Composable
fun PairingScreen(
    store: PairingStore,
    onPaired: () -> Unit,
    modifier: Modifier = Modifier,
    validate: suspend (baseUrl: String, token: String) -> PairResult = ::validatePairing,
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
            val result = try {
                validate(parsed.baseUrl, parsed.token)
            } catch (_: Exception) {
                PairResult.Unreachable
            }
            when (result) {
                PairResult.Ok -> Unit
                PairResult.BadToken -> {
                    status = PairingStatus.ERROR
                    error = "TOKEN INVÁLIDO: EL SERVIDOR SE REINICIÓ O CAMBIÓ SU TOKEN. PEDÍ UN QR NUEVO."
                    return@launch
                }
                PairResult.Unreachable -> {
                    status = PairingStatus.ERROR
                    error = "NO SE LLEGA AL SERVIDOR. USA EL MISMO WIFI DEL BAR."
                    return@launch
                }
            }
            store.save(parsed.baseUrl, parsed.token)
            pairedUrl = parsed.baseUrl
            status = PairingStatus.SUCCESS
        }
    }

    // Play services code scanner: GMS renders its own framed viewfinder UI
    // and handles dense pairing QRs (long URL + 64-hex token) with auto-zoom.
    // Built lazily at tap time so JVM unit tests never load GMS classes.
    fun launchScanner() {
        showCameraRationale = false
        status = PairingStatus.SCANNING
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .enableAutoZoom()
            .build()
        GmsBarcodeScanning.getClient(context, options)
            .startScan()
            .addOnSuccessListener { barcode ->
                val raw = barcode.rawValue
                if (raw == null) {
                    status = PairingStatus.IDLE
                } else {
                    handleCode(raw)
                }
            }
            .addOnCanceledListener {
                status = PairingStatus.IDLE
            }
            .addOnFailureListener {
                status = PairingStatus.ERROR
                error = "NO SE PUDO LEER EL QR. PROBÁ DE NUEVO O PEGÁ EL CÓDIGO."
            }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            launchScanner()
        } else {
            showCameraRationale = true
        }
    }

    fun startScan() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            launchScanner()
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
        MittSectionTitle(
            title = "Conectar servidor",
            sub = "Escaneá el QR del servidor o pegá el código para empezar a anotar.",
        )
        // Scan-first hero: the camera path is the fastest way to pair,
        // so it gets the biggest surface and the primary CTA.
        MittCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Apuntá la cámara al QR del bar",
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "El QR está en la pantalla del servidor. Se lee en segundos y no hay que escribir nada.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            MittPrimaryButton(
                label = "ESCANEAR QR",
                onClick = ::startScan,
                enabled = status != PairingStatus.VALIDATING,
            )
            if (showCameraRationale) {
                Text(
                    text = "SE NECESITA LA CÁMARA PARA ESCANEAR EL QR. ACEPTÁ EL PERMISO PARA CONTINUAR.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        // Paste fallback, same code the QR carries.
        MittCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "¿Sin cámara? Pegá el código",
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = pasted,
                onValueChange = { pasted = it },
                label = { Text("Código de pairing") },
                placeholder = { Text("mitt://pair?url=... o http://...|token") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
            Spacer(modifier = Modifier.height(8.dp))
            MittSecondaryButton(
                label = "PEGAR CÓDIGO",
                onClick = { handleCode(pasted) },
                enabled = pasted.isNotBlank() && status != PairingStatus.VALIDATING,
            )
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
                MittCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "CONECTADO A ${pairedUrl ?: ""}",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    MittPrimaryButton(label = "CONTINUAR", onClick = onPaired)
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
