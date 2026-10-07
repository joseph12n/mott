package dev.mott.app

import dev.mott.app.data.parsePairingCode
import dev.mott.app.ui.pair.PairResult
import dev.mott.app.ui.pair.validatePairing
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

// JVM-pure pairing-code rules: valid shapes decode, bad shapes throw
// IllegalArgumentException with a message (never silently accepted).
class PairingCodeTest {

    @Test
    fun `valid qr code decodes url and token`() {
        val parsed = parsePairingCode(
            "mitt://pair?url=http%3A%2F%2F192.168.1.10%3A8080&token=abcdefghijklmnop",
        )

        assertEquals("http://192.168.1.10:8080", parsed.baseUrl)
        assertEquals("abcdefghijklmnop", parsed.token)
    }

    @Test
    fun `valid https url with port is accepted`() {
        val parsed = parsePairingCode(
            "mitt://pair?url=https%3A%2F%2Fhub.local%3A8443&token=0123456789abcdef",
        )

        assertEquals("https://hub.local:8443", parsed.baseUrl)
        assertEquals("0123456789abcdef", parsed.token)
    }

    @Test
    fun `trailing slash is stripped from base url`() {
        val parsed = parsePairingCode(
            "http://192.168.1.10:8080/|abcdefghijklmnopqr",
        )

        assertEquals("http://192.168.1.10:8080", parsed.baseUrl)
    }

    @Test
    fun `bare url pipe token fallback is accepted`() {
        val parsed = parsePairingCode("http://192.168.1.10:8080|abcdefghijklmnop")

        assertEquals("http://192.168.1.10:8080", parsed.baseUrl)
        assertEquals("abcdefghijklmnop", parsed.token)
    }

    @Test
    fun `surrounding whitespace is ignored`() {
        val parsed = parsePairingCode("  http://192.168.1.10:8080|abcdefghijklmnop  ")

        assertEquals("http://192.168.1.10:8080", parsed.baseUrl)
    }

    @Test
    fun `bad scheme is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            parsePairingCode("http://pair?url=http%3A%2F%2Fh%3A8080&token=abcdefghijklmnop")
        }
    }

    @Test
    fun `wrong host is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            parsePairingCode("mitt://hub?url=http%3A%2F%2Fh%3A8080&token=abcdefghijklmnop")
        }
    }

    @Test
    fun `missing token is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            parsePairingCode("mitt://pair?url=http%3A%2F%2Fh%3A8080")
        }
    }

    @Test
    fun `missing url is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            parsePairingCode("mitt://pair?token=abcdefghijklmnop")
        }
    }

    @Test
    fun `short token is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            parsePairingCode("mitt://pair?url=http%3A%2F%2Fh%3A8080&token=short")
        }
    }

    @Test
    fun `non-http url is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            parsePairingCode("mitt://pair?url=ftp%3A%2F%2Fh%3A21&token=abcdefghijklmnop")
        }
    }

    @Test
    fun `url without port is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            parsePairingCode("mitt://pair?url=http%3A%2F%2Fh&token=abcdefghijklmnop")
        }
    }

    @Test
    fun `fallback without separator is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            parsePairingCode("just-some-text")
        }
    }

    @Test
    fun `empty input is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            parsePairingCode("   ")
        }
    }

    @Test
    fun `healthy hub with token reports Ok`() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200))
        server.start()
        try {
            val result = validatePairing(server.url("/").toString(), "abcdefghijklmnop")

            assertEquals(PairResult.Ok, result)
            assertEquals(
                "Bearer abcdefghijklmnop",
                server.takeRequest().getHeader("Authorization"),
            )
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `wrong token reports BadToken`() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(401))
        server.start()
        try {
            assertEquals(
                PairResult.BadToken,
                validatePairing(server.url("/").toString(), "abcdefghijklmnop"),
            )
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `forbidden token reports BadToken`() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(403))
        server.start()
        try {
            assertEquals(
                PairResult.BadToken,
                validatePairing(server.url("/").toString(), "abcdefghijklmnop"),
            )
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `unreachable hub reports Unreachable`() = runBlocking {
        val server = MockWebServer()
        server.start()
        val deadUrl = server.url("/").toString()
        server.shutdown()

        assertEquals(
            PairResult.Unreachable,
            validatePairing(deadUrl, "abcdefghijklmnop"),
        )
    }
}
