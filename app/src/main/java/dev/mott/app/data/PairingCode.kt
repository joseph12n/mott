package dev.mott.app.data

import java.net.URI
import java.net.URLDecoder

// Credentials decoded from a pairing code: the mitt hub URL plus token.
// Pure Kotlin so the parsing rules stay JVM-testable without Android.
data class PairingData(
    val baseUrl: String,
    val token: String,
)

// Parses a pairing code from QR content or pasted text.
// Primary format: mitt://pair?url=<http://host:port>&token=<token>.
// Fallback for manual paste: bare "url|token".
// Throws IllegalArgumentException describing the first rule broken.
fun parsePairingCode(raw: String): PairingData {
    val input = raw.trim()
    require(input.isNotEmpty()) { "pairing code is empty" }
    // QR codes use the mitt scheme; anything else (including a bare
    // http(s) URL) is the manual "url|token" paste fallback.
    return if (input.startsWith("mitt://", ignoreCase = true)) {
        parseQrUri(input)
    } else {
        parseBareFallback(input)
    }
}

private fun parseQrUri(input: String): PairingData {
    val uri = runCatching { URI(input) }.getOrNull()
        ?: throw IllegalArgumentException("pairing code is not a valid URI")
    require(uri.scheme == "mitt") { "pairing scheme must be mitt" }
    require(uri.host == "pair") { "pairing host must be pair" }
    val query = uri.rawQuery
        ?: throw IllegalArgumentException("pairing code is missing url and token")
    val params = parseQuery(query)
    val url = params["url"]
        ?: throw IllegalArgumentException("pairing code is missing url")
    val token = params["token"]
        ?: throw IllegalArgumentException("pairing code is missing token")
    return validateParts(url, token)
}

private fun parseBareFallback(input: String): PairingData {
    val sep = input.indexOf('|')
    require(sep > 0 && sep < input.length - 1) {
        "pairing code must look like mitt://pair?url=...&token=... or url|token"
    }
    return validateParts(input.substring(0, sep).trim(), input.substring(sep + 1).trim())
}

private fun validateParts(url: String, token: String): PairingData {
    require(url.startsWith("http://") || url.startsWith("https://")) {
        "pairing url must start with http:// or https://"
    }
    val parsed = runCatching { URI(url) }.getOrNull()
    require(parsed?.host != null) { "pairing url has no host" }
    require(parsed.port != -1) { "pairing url must include a port" }
    require(token.length >= 16) { "pairing token must be at least 16 characters" }
    return PairingData(baseUrl = url.trimEnd('/'), token = token)
}

private fun parseQuery(rawQuery: String): Map<String, String> {
    val out = mutableMapOf<String, String>()
    for (pair in rawQuery.split('&')) {
        val eq = pair.indexOf('=')
        if (eq <= 0) continue
        val key = URLDecoder.decode(pair.substring(0, eq), "UTF-8")
        if (key !in out) {
            out[key] = URLDecoder.decode(pair.substring(eq + 1), "UTF-8")
        }
    }
    return out
}
