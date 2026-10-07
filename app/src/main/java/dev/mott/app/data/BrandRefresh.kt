package dev.mott.app.data

import android.content.Context
import dev.mott.app.data.remote.BrandingResponse
import dev.mott.app.net.NetStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

private val brandJson = Json { ignoreUnknownKeys = true }

// Fetches public GET /api/branding (no auth, same contract as
// GET /api/health) and hands the parsed Brand to save. Any failure —
// offline, non-200, bad payload — returns false and leaves the cache
// untouched, so callers keep painting cached/offline defaults silently.
suspend fun refreshBrand(
    baseUrl: String,
    save: (Brand) -> Unit,
    client: OkHttpClient = defaultBrandClient(),
): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + "/api/branding")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (response.code != 200) return@withContext false
            val body = response.body?.string() ?: return@withContext false
            save(brandJson.decodeFromString<BrandingResponse>(body).toBrand())
            true
        }
    }.getOrDefault(false)
}

fun defaultBrandClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(10, TimeUnit.SECONDS)
    .build()

// Smallest online seam: refresh only when the device reports connectivity
// and a pairing exists. Offline-first holds: false just means the cached
// brand (or token defaults) keeps painting.
suspend fun refreshBrandIfOnline(
    context: Context,
    pairing: PairingStore,
    brands: BrandStore,
): Boolean {
    if (!NetStatus.isOnline(context)) return false
    val baseUrl = pairing.get()?.baseUrl ?: return false
    return refreshBrand(baseUrl, { brands.save(it) })
}
