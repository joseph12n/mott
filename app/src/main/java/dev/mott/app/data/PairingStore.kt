package dev.mott.app.data

import android.content.Context
import android.content.SharedPreferences

// Paired hub credentials: the mitt base URL plus the Bearer pairing token.
// Backed by plain SharedPreferences. Hardening follow-up (explicit
// non-goal of M4): move the token to EncryptedSharedPreferences so it is
// not readable in cleartext on a rooted device.
class PairingStore(
    private val prefs: SharedPreferences,
) {
    constructor(context: Context) : this(
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
    )

    fun get(): Pairing? {
        val baseUrl = prefs.getString(KEY_BASE_URL, null)
        val token = prefs.getString(KEY_TOKEN, null)
        if (baseUrl.isNullOrEmpty() || token.isNullOrEmpty()) return null
        return Pairing(baseUrl = baseUrl, token = token)
    }

    fun save(baseUrl: String, token: String) {
        prefs.edit()
            .putString(KEY_BASE_URL, baseUrl)
            .putString(KEY_TOKEN, token)
            .apply()
    }

    fun clear() {
        prefs.edit()
            .remove(KEY_BASE_URL)
            .remove(KEY_TOKEN)
            .apply()
    }

    companion object {
        const val PREFS_NAME = "pairing"
        const val KEY_BASE_URL = "base_url"
        const val KEY_TOKEN = "token"
    }
}

// The hub this device syncs against, or null when never paired.
data class Pairing(
    val baseUrl: String,
    val token: String,
)
