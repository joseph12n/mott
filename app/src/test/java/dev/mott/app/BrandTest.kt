package dev.mott.app

import androidx.compose.ui.graphics.Color
import dev.mott.app.data.Brand
import dev.mott.app.data.BrandStore
import dev.mott.app.data.parseHex6
import dev.mott.app.data.parsePairingCode
import dev.mott.app.data.refreshBrand
import dev.mott.app.data.remote.ApiClient
import dev.mott.app.data.remote.BrandingResponse
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

// Brand link-proof: a realistic mitt pairing URL decodes through the
// existing PairingCode parser, and a realistic GET /api/branding payload
// decodes through the new DTO with hexes applying to paintable colors.
// DUMMY tokens only — never commit a real hub secret.
class BrandTest {
    private val server = MockWebServer()

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `mitt wire pairing url decodes url and token`() {
        val parsed = parsePairingCode(
            "mitt://pair?url=http%3A%2F%2F192.168.1.20%3A8080&token=DUMMY-DUMMY-TOKEN-0000",
        )

        assertEquals("http://192.168.1.20:8080", parsed.baseUrl)
        assertEquals("DUMMY-DUMMY-TOKEN-0000", parsed.token)
    }

    @Test
    fun `mitt wire branding json decodes every field`() {
        val dto: BrandingResponse = ApiClient.json.decodeFromString(MITT_WIRE_BRANDING_JSON)

        assertEquals("Bar Central", dto.shopName)
        assertEquals("#1C1F24", dto.primary)
        assertEquals("#E8A33D", dto.accent)
        assertEquals("#121417", dto.background)
        assertEquals(false, dto.hasLogo)
        assertEquals("2026-10-07T12:00:00Z", dto.updatedAt)
    }

    @Test
    fun `mitt wire branding json maps to brand with applying hexes`() {
        val dto: BrandingResponse = ApiClient.json.decodeFromString(MITT_WIRE_BRANDING_JSON)
        val brand = dto.toBrand()

        assertEquals(
            Brand(
                shopName = "Bar Central",
                primaryHex = "#1C1F24",
                accentHex = "#E8A33D",
                backgroundHex = "#121417",
                updatedAt = "2026-10-07T12:00:00Z",
            ),
            brand,
        )
        assertEquals(Color(0xFF1C1F24), parseHex6(brand.primaryHex))
        assertEquals(Color(0xFFE8A33D), parseHex6(brand.accentHex))
        assertEquals(Color(0xFF121417), parseHex6(brand.backgroundHex))
    }

    @Test
    fun `branding json tolerates unknown keys and missing logo fields`() {
        val dto: BrandingResponse = ApiClient.json.decodeFromString(
            """{"shop_name":"mitt","primary":"#1C1F24","accent":"#E8A33D","background":"#121417","future_key":7}""",
        )

        assertEquals("mitt", dto.shopName)
        assertEquals(false, dto.hasLogo)
        assertEquals("", dto.updatedAt)
    }

    @Test
    fun `parseHex6 accepts strict rrggbb in any case`() {
        assertEquals(Color(0xFFE8A33D), parseHex6("#E8A33D"))
        assertEquals(Color(0xFFE8A33D), parseHex6("#e8a33d"))
        assertEquals(Color(0xFF000000), parseHex6("#000000"))
        assertEquals(Color(0xFFFFFFFF), parseHex6("#ffffff"))
    }

    @Test
    fun `parseHex6 rejects anything off spec`() {
        assertNull(parseHex6(null))
        assertNull(parseHex6(""))
        assertNull(parseHex6("E8A33D"))
        assertNull(parseHex6("#FFF"))
        assertNull(parseHex6("#12345"))
        assertNull(parseHex6("#1234567"))
        assertNull(parseHex6("#GGGGGG"))
        assertNull(parseHex6(" #E8A33D"))
        assertNull(parseHex6("#E8A33D "))
    }

    @Test
    fun `brand defaults match mitt dark tokens`() {
        assertEquals("mitt", BrandStore.DEFAULT_SHOP_NAME)
        assertEquals("#1C1F24", BrandStore.DEFAULT_PRIMARY)
        assertEquals("#E8A33D", BrandStore.DEFAULT_ACCENT)
        assertEquals("#121417", BrandStore.DEFAULT_BACKGROUND)
        assertEquals(BrandStore.DEFAULT, BrandStore.DEFAULT.copy())
    }

    @Test
    fun `refreshBrand saves the mitt wire payload`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(MITT_WIRE_BRANDING_JSON)
                .addHeader("Content-Type", "application/json"),
        )
        val saved = mutableListOf<Brand>()

        val ok = refreshBrand(server.url("/").toString(), { saved.add(it) })

        assertTrue(ok)
        assertEquals(1, saved.size)
        assertEquals("Bar Central", saved.single().shopName)
        assertEquals("#E8A33D", saved.single().accentHex)
        assertEquals("/api/branding", server.takeRequest(5, TimeUnit.SECONDS)?.path)
    }

    @Test
    fun `refreshBrand keeps cache silently on server error`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("""{"error":{"code":"internal","message":"x"}}"""))
        var calls = 0

        val ok = refreshBrand(server.url("/").toString(), { calls++ })

        assertFalse(ok)
        assertEquals(0, calls)
    }

    @Test
    fun `refreshBrand keeps cache silently on bad payload`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{not json"))
        var calls = 0

        val ok = refreshBrand(server.url("/").toString(), { calls++ })

        assertFalse(ok)
        assertEquals(0, calls)
    }

    companion object {
        // Realistic GET /api/branding body (mitt wire, DUMMY content).
        const val MITT_WIRE_BRANDING_JSON =
            """{"shop_name":"Bar Central","primary":"#1C1F24","accent":"#E8A33D","background":"#121417","has_logo":false,"updated_at":"2026-10-07T12:00:00Z"}"""
    }
}
