package dev.mott.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.mott.app.data.ApiException
import dev.mott.app.data.Brand
import dev.mott.app.data.BrandStore
import dev.mott.app.data.PairingStore
import dev.mott.app.data.parseHex6
import dev.mott.app.data.refreshBrandIfOnline
import dev.mott.app.data.remote.ApiClient
import dev.mott.app.net.NetStatus
import dev.mott.app.ui.order.OfflineBanner
import dev.mott.app.ui.theme.ThemeMode
import dev.mott.app.ui.theme.ThemeModeStore
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

// Personalizar parity with the mitt PC reduction
// (mitt frontend/src/views/Personalizar.tsx): shop identity editor where
// every change stages LOCALLY and lands with ONE merged PATCH
// /api/branding (absent keys keep stored values, explicit null clears
// the logo — hub merge semantics). The hub background stays untouched
// except on factory reset (the PC view has no background control, so
// neither does this section). Dark mode is a local ThemeModeStore
// preference wired here in full (Sistema/Claro/Oscuro); it never enters
// the branding DTO (documented hub gap). The shell TEMA toggle keeps
// working: both write the same store, which is the canonical place.
// The shell header owns the title/subtitle, so this file renders body
// only. Self-sufficient from the saved pairing (ProveedoresSection
// precedent): no MainActivity wiring change needed — after a save the
// merged truth persists into BrandStore, so the whole app re-themes on
// the next brand load. Cancellation keeps propagating (R3-002), never
// silent, never crashing.
@Composable
fun PersonalizarSection(
    modifier: Modifier = Modifier,
    onBrandChanged: () -> Unit = {},
    onThemeModeChanged: () -> Unit = {},
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    // Same stores the shell and the brand cache use: theme writes land
    // where the TEMA toggle reads, brand saves where MottTheme paints.
    val brandStore = remember(appContext) { BrandStore(appContext) }
    val themeModeStore = remember(appContext) { ThemeModeStore(appContext) }
    val pairingStore = remember(appContext) { PairingStore(appContext) }
    fun api() = pairingStore.get()?.let { pairing ->
        ApiClient.build(pairing.baseUrl, pairing.token, logger = false)
    }
    var brandState by remember { mutableStateOf<LoadState<Brand>>(LoadState.Loading) }
    var themeMode by remember { mutableStateOf(themeModeStore.get()) }
    var saving by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var failure by remember { mutableStateOf<LoadFailureReason?>(null) }
    var retry by remember { mutableStateOf<(() -> Unit)?>(null) }
    var resetArm by remember { mutableStateOf(false) }
    var remoteLogo by remember { mutableStateOf<Bitmap?>(null) }
    val scope = rememberCoroutineScope()

    fun refresh() {
        scope.launch {
            brandState = LoadState.Loading
            brandState = try {
                // Public brand pull when online; offline keeps the cached
                // brand (or token defaults) silently, same as app start.
                refreshBrandIfOnline(appContext, pairingStore, brandStore)
                LoadState.Ready(brandStore.getOrDefault())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LoadState.Failed(classifyLoadFailure(e))
            }
        }
    }
    LaunchedEffect(Unit) { refresh() }
    val base = (brandState as? LoadState.Ready)?.data ?: brandStore.getOrDefault()
    val paired = pairingStore.get() != null

    // Staging form, rebased onto the live brand after every save (PC
    // useEffect parity): edits never touch the hub until Guardar.
    var shopName by remember(base) { mutableStateOf(base.shopName) }
    var primary by remember(base) { mutableStateOf(base.primaryHex) }
    var accent by remember(base) { mutableStateOf(base.accentHex) }
    var logoMode by remember(base) { mutableStateOf(LogoMode.KEEP) }
    var logoDataUrl by remember(base) { mutableStateOf("") }
    var logoError by remember(base) { mutableStateOf<String?>(null) }

    // Current hub logo for the preview tile (public raw bytes, cached
    // silently on any failure — the form never depends on it).
    LaunchedEffect(base.updatedAt, paired) {
        remoteLogo = null
        val pairing = pairingStore.get() ?: return@LaunchedEffect
        remoteLogo = runCatching { fetchLogoBitmap(pairing.baseUrl) }.getOrNull()
    }

    val draft = stagedBrandingDraft(base, shopName, primary, accent, logoMode, logoDataUrl)
    val invalid = validateBrandingDraft(base, shopName, primary, accent)
    val logoInvalid = if (logoMode == LogoMode.SET) validateLogoDataUrl(logoDataUrl) else null
    val canSave = paired && draft.isDirty && invalid == null && logoInvalid == null && !saving

    // Image picker through the system gallery (ActivityResult GetContent,
    // same launcher family as the QR permission flow in PairingScreen).
    // GetContent needs no storage permission: the system picker grants a
    // one-shot read URI, so no manifest or runtime permission applies.
    val logoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        logoError = null
        val staged = runCatching { readLogoDataUrl(appContext, uri) }
        val dataUrl = staged.getOrNull()
        if (dataUrl == null) {
            logoError = logoReadInlineCopy(staged.exceptionOrNull())
            return@rememberLauncherForActivityResult
        }
        val check = validateLogoDataUrl(dataUrl)
        if (check != null) {
            logoError = check
            return@rememberLauncherForActivityResult
        }
        logoDataUrl = dataUrl
        logoMode = LogoMode.SET
    }

    fun clearOutcome() {
        note = null
        error = null
        failure = null
        retry = null
    }

    // Shared save-error mapping: named 401/413/422 copy inline,
    // everything else through SectionLoaders with retry. Staged values
    // are kept on failure so no edit is ever lost.
    fun reportSaveError(e: Throwable, doRetry: () -> Unit) {
        if (e is CancellationException) throw e
        val named = (e as? ApiException)?.let { brandingSaveInlineCopy(it.status) }
            ?: (e as? IOException)?.let { "Sin conexión. Revisá el Wi-Fi del bar e intentá de nuevo." }
        if (named != null) {
            error = named
            return
        }
        failure = classifyLoadFailure(e)
        retry = doRetry
    }

    fun save() {
        clearOutcome()
        resetArm = false
        val localInvalid = validateBrandingDraft(base, shopName, primary, accent)
        if (localInvalid != null) {
            error = localInvalid
            return
        }
        if (logoMode == LogoMode.SET) {
            val logoCheck = validateLogoDataUrl(logoDataUrl)
            if (logoCheck != null) {
                logoError = logoCheck
                return
            }
        }
        if (!NetStatus.isOnline(context)) {
            error = "Sin conexión. Revisá el Wi-Fi del bar e intentá de nuevo."
            return
        }
        val api = api()
        if (api == null) {
            error = "Se necesita conexión. Guardá un token en Conexión para personalizar la marca."
            return
        }
        val body = draft.toPatchBody() ?: return
        scope.launch {
            saving = true
            try {
                // ONE merged PATCH; the hub answers the merged truth,
                // which persists into BrandStore and rebases the form.
                val next = patchBranding(api, body)
                brandStore.save(next)
                brandState = LoadState.Ready(next)
                onBrandChanged()
                note = "Marca guardada."
            } catch (e: Exception) {
                reportSaveError(e, ::save)
            } finally {
                saving = false
            }
        }
    }

    // Factory reset (PC reset parity): the ONLY write that touches the
    // background, restoring name + all three colors. Two-tap confirm
    // (delete precedent): the first tap arms, the second sends.
    fun reset() {
        clearOutcome()
        if (!resetArm) {
            resetArm = true
            return
        }
        if (!NetStatus.isOnline(context)) {
            error = "Sin conexión. Revisá el Wi-Fi del bar e intentá de nuevo."
            resetArm = false
            return
        }
        val api = api()
        if (api == null) {
            error = "Se necesita conexión. Guardá un token en Conexión para personalizar la marca."
            resetArm = false
            return
        }
        scope.launch {
            saving = true
            try {
                val next = patchBranding(
                    api,
                    buildJsonObject {
                        put("shop_name", JsonPrimitive(BrandStore.DEFAULT_SHOP_NAME))
                        put("primary", JsonPrimitive(BrandStore.DEFAULT_PRIMARY))
                        put("accent", JsonPrimitive(BrandStore.DEFAULT_ACCENT))
                        put("background", JsonPrimitive(BrandStore.DEFAULT_BACKGROUND))
                    },
                )
                brandStore.save(next)
                brandState = LoadState.Ready(next)
                resetArm = false
                onBrandChanged()
                note = "Marca restablecida."
            } catch (e: Exception) {
                resetArm = false
                reportSaveError(e, ::reset)
            } finally {
                saving = false
            }
        }
    }

    // Staged preview: the picked image while SET, the hub logo otherwise
    // (null while clearing or offline). Decoded once per staged value.
    val stagedBitmap = remember(logoDataUrl, logoMode) {
        if (logoMode != LogoMode.SET || logoDataUrl.isEmpty()) null
        else runCatching {
            logoBytesOf(logoDataUrl)?.let {
                BitmapFactory.decodeByteArray(it, 0, it.size)
            }
        }.getOrNull()
    }
    val previewBitmap: Bitmap? = when (logoMode) {
        LogoMode.SET -> stagedBitmap
        LogoMode.CLEAR -> null
        LogoMode.KEEP -> remoteLogo
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        if (!NetStatus.isOnline(context)) {
            OfflineBanner()
            Spacer(modifier = Modifier.height(4.dp))
        }
        if (!paired) {
            MittCard(modifier = Modifier.fillMaxWidth()) {
                Text(text = "Se necesita conexión", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Guardá un token en Conexión para personalizar la marca.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
        // Establecimiento: logo tile + shop name, staged locally.
        MittCard(modifier = Modifier.fillMaxWidth()) {
            Text(text = "Establecimiento", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    val bitmap = previewBitmap
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Logo",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Text(
                            text = if (logoMode == LogoMode.CLEAR) "Se quita al guardar" else "Sin logo",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(modifier = Modifier.padding(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MittSecondaryButton(
                        label = if (previewBitmap != null) "CAMBIAR LOGO" else "SUBIR LOGO",
                        onClick = { logoPicker.launch("image/*") },
                    )
                    if (logoMode == LogoMode.SET || previewBitmap != null) {
                        TextButton(
                            onClick = {
                                logoMode = LogoMode.CLEAR
                                logoDataUrl = ""
                                logoError = null
                            },
                        ) { Text("Quitar") }
                    }
                }
            }
            if (logoError != null) {
                Text(
                    text = logoError ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = shopName,
                onValueChange = { shopName = it },
                label = { Text("Nombre del local") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            if (invalid != null) {
                Text(
                    text = invalid,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        // Tema: full Sistema/Claro/Oscuro picker over the T3 store (local
        // only, never sent in the branding DTO) plus the custom hexes.
        MittCard(modifier = Modifier.fillMaxWidth()) {
            Text(text = "Tema", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (option in personalizarThemeOptions()) {
                    val selected = themeMode == option
                    TextButton(
                        onClick = {
                            themeModeStore.save(option)
                            themeMode = option
                            onThemeModeChanged()
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = if (selected) "● ${option.label}" else option.label,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "Colores a medida", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            HexField(label = "Principal", value = primary, onValue = { primary = it })
            Spacer(modifier = Modifier.height(8.dp))
            HexField(label = "Acento", value = accent, onValue = { accent = it })
            if (invalid != null && (isValidHex6(primary).not() || isValidHex6(accent).not())) {
                Text(
                    text = "Usa el formato #rrggbb.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        MittPrimaryButton(
            label = if (saving) "GUARDANDO…" else "GUARDAR",
            onClick = ::save,
            enabled = canSave,
        )
        Spacer(modifier = Modifier.height(8.dp))
        MittSecondaryButton(
            label = if (resetArm) "CONFIRMAR RESTABLECIDO" else "RESTABLECER",
            onClick = ::reset,
            enabled = paired && !saving,
        )
        val outcome = error ?: note
        if (outcome != null) {
            Text(
                text = outcome,
                style = MaterialTheme.typography.bodyMedium,
                color = if (error != null) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        failure?.let { reason ->
            Spacer(modifier = Modifier.height(12.dp))
            MittLoadErrorCard(
                reason = reason,
                onRetry = { retry?.invoke() },
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        // Preset gallery: the 22 PC palettes as brand/accent/tint
        // stripes with a selected ring. Tapping one stages both hexes.
        MittCard(modifier = Modifier.fillMaxWidth()) {
            Text(text = "Presets de diseño", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "${BRAND_PRESETS.size} paletas para cada tipo de local",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            for (row in BRAND_PRESETS.chunked(2)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (preset in row) {
                        val on = primary == preset.brandHex && accent == preset.accentHex
                        MittClickableCard(
                            onClick = {
                                primary = preset.brandHex
                                accent = preset.accentHex
                            },
                            selected = on,
                            modifier = Modifier.weight(1f),
                        ) {
                            PresetStripes(brandHex = preset.brandHex, accentHex = preset.accentHex)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = preset.name,
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                    Text(
                                        text = preset.tag,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (on) {
                                    Text(
                                        text = "✓",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                    // Even row width when the gallery count is odd.
                    if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
        val loadFailure = (brandState as? LoadState.Failed)?.reason
        if (loadFailure != null) {
            MittLoadErrorCard(reason = loadFailure, onRetry = ::refresh)
            Spacer(modifier = Modifier.height(12.dp))
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

// Hex field with a live swatch: the staged value paints the box when it
// parses, so typos read as an empty swatch plus the inline message.
@Composable
private fun HexField(label: String, value: String, onValue: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(parseHex6(value) ?: Color.Transparent),
        )
        Spacer(modifier = Modifier.padding(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValue,
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
    }
}

// PC gallery stripe shape: brand (3x) + accent (1.2x) + the 14% tint
// (1x), the same proportions the web master renders.
@Composable
private fun PresetStripes(brandHex: String, accentHex: String) {
    val brand = parseHex6(brandHex) ?: Color.Transparent
    val accent = parseHex6(accentHex) ?: Color.Transparent
    val tint = parseHex6(presetTintHex(brandHex)) ?: Color.Transparent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(12.dp)),
    ) {
        Box(modifier = Modifier.weight(3f).fillMaxSize().background(brand))
        Box(modifier = Modifier.weight(1.2f).fillMaxSize().background(accent))
        Box(modifier = Modifier.weight(1f).fillMaxSize().background(tint))
    }
}

// Maps a logo-read failure onto its inline copy: bad kinds name the
// accepted mimes, oversized rasters name the 512KB cap, anything else
// (unreadable URI, decode failure) reads as a plain read error.
private fun logoReadInlineCopy(e: Throwable?): String = when (e) {
    is IllegalArgumentException -> "El logo debe ser PNG, JPEG o SVG."
    is IllegalStateException -> "El logo no debe superar 512 KB."
    else -> "No se pudo leer el archivo."
}

// Reads the picked image as a hub-ready data:<mime>;base64 URL. Rasters
// past the 512KiB cap downscale (halving sample sizes, JPEG 85) so a
// big camera photo still fits; vectors pass through untouched. Throws
// IllegalArgumentException on rejected kinds, IllegalStateException when
// the payload cannot fit, IOException when the URI will not read.
private fun readLogoDataUrl(context: Context, uri: Uri): String {
    val resolver = context.contentResolver
    val mime = resolver.getType(uri) ?: throw IllegalArgumentException("rejected logo mime")
    if (mime !in ACCEPTED_LOGO_MIMES) throw IllegalArgumentException("rejected logo mime")
    val raw = try {
        resolver.openInputStream(uri)?.use { it.readBytes() }
    } catch (e: IOException) {
        throw e
    } ?: throw IOException("unreadable logo uri")
    if (mime == "image/svg+xml") {
        if (raw.size > MAX_LOGO_BYTES) throw IllegalStateException("oversized logo")
        return buildLogoDataUrl(mime, raw)
    }
    if (raw.size <= MAX_LOGO_BYTES) return buildLogoDataUrl(mime, raw)
    var sample = 2
    while (sample <= 16) {
        val decoded = runCatching {
            resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(
                    stream,
                    null,
                    BitmapFactory.Options().apply { inSampleSize = sample },
                )
            }
        }.getOrNull()
        if (decoded != null) {
            val out = ByteArrayOutputStream()
            decoded.compress(Bitmap.CompressFormat.JPEG, 85, out)
            decoded.recycle()
            val bytes = out.toByteArray()
            if (bytes.isNotEmpty() && bytes.size <= MAX_LOGO_BYTES) {
                return buildLogoDataUrl("image/jpeg", bytes)
            }
        }
        sample *= 2
    }
    throw IllegalStateException("oversized logo")
}

// Fetches the public raw logo bytes for the preview tile, downsampled
// past 512px so a big hub logo never blows the tile budget. Null on any
// failure (no logo stored, offline, bad payload): the form never depends
// on it, so failures stay silent by design.
private suspend fun fetchLogoBitmap(baseUrl: String): Bitmap? = withContext(Dispatchers.IO) {
    runCatching {
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
        client.newCall(
            Request.Builder().url(baseUrl.trimEnd('/') + "/api/branding/logo").get().build(),
        ).execute().use { response ->
            if (response.code != 200) return@withContext null
            val bytes = response.body?.bytes() ?: return@withContext null
            if (bytes.isEmpty() || bytes.size > 8 * 1024 * 1024) return@withContext null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (bounds.outWidth / sample > 512 || bounds.outHeight / sample > 512) sample *= 2
            BitmapFactory.decodeByteArray(
                bytes,
                0,
                bytes.size,
                BitmapFactory.Options().apply { inSampleSize = sample },
            )
        }
    }.getOrNull()
}
