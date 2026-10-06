package dev.mott.app.data.remote

import retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

// Builds the Retrofit client for talking to the mitt hub.
// Auth is a Bearer pairing token on every request except GET /api/health,
// which stays public so the app can discover the hub before pairing.
// Fail-closed: when token is null no Authorization header is sent and the
// server rejects protected calls with 401 instead of leaking a bad secret.
object ApiClient {
    private const val HEALTH_PATH = "/api/health"

    val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun build(baseUrl: String, token: String?, logger: Boolean): MittApi {
        val safeBase = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val clientBuilder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(authInterceptor(token))
        if (logger) {
            clientBuilder.addInterceptor(
                HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY),
            )
        }
        return Retrofit.Builder()
            .baseUrl(safeBase)
            .client(clientBuilder.build())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(MittApi::class.java)
    }

    private fun authInterceptor(token: String?): Interceptor = Interceptor { chain ->
        val request = chain.request()
        if (request.url.encodedPath == HEALTH_PATH || token == null) {
            chain.proceed(request)
        } else {
            chain.proceed(
                request.newBuilder()
                    .header("Authorization", "Bearer $token")
                    .build(),
            )
        }
    }
}
