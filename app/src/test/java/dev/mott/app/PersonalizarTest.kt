package dev.mott.app

import dev.mott.app.data.Brand
import dev.mott.app.data.remote.ApiClient
import dev.mott.app.data.remote.MittApi
import dev.mott.app.ui.BRAND_PRESETS
import dev.mott.app.ui.LogoMode
import dev.mott.app.ui.MAX_LOGO_BYTES
import dev.mott.app.ui.brandingSaveInlineCopy
import dev.mott.app.ui.buildLogoDataUrl
import dev.mott.app.ui.isValidHex6
import dev.mott.app.ui.logoDataUrlTooLarge
import dev.mott.app.ui.patchBranding
import dev.mott.app.ui.personalizarThemeOptions
import dev.mott.app.ui.presetTintHex
import dev.mott.app.ui.stagedBrandingDraft
import dev.mott.app.ui.theme.ThemeMode
import dev.mott.app.ui.theme.resolveDark
import dev.mott.app.ui.validateBrandingDraft
import dev.mott.app.ui.validateLogoDataUrl
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.TimeUnit

// T8 Personalizar parity with the mitt PC reduction: 22 gallery presets
// ported exactly, hex/name/logo staging validated client-side, every save
// landing as ONE merged PATCH /api/branding (explicit null clears the
// logo, an absent key keeps it). JVM-pure plus MockWebServer; no Compose
// UI tests. DUMMY tokens only — never commit a real hub secret.
class PersonalizarTest {
    private var server: MockWebServer? = null

    @After
    fun tearDown() {
        runCatching { server?.shutdown() }
        server = null
    }

    private fun json(code: Int, body: String) {
        server!!.enqueue(
            MockResponse().setResponseCode(code).setBody(body).addHeader("Content-Type", "application/json"),
        )
    }

    private fun start(): MittApi {
        val s = MockWebServer()
        s.start()
        server = s
        return ApiClient.build(s.url("/").toString(), "pair-token", logger = false)
    }

    private fun takeRequest() = server!!.takeRequest(5, TimeUnit.SECONDS)!!

    private fun baseBrand() = Brand(
        shopName = "Bar Central",
        primaryHex = "#1C1F24",
        accentHex = "#E8A33D",
        backgroundHex = "#121417",
        updatedAt = "2026-10-07T12:00:00Z",
    )

    // -- 22-preset gallery (mitt + Figma presets.ts, ported exactly) --------

    @Test
    fun `presets hold 22 palettes`() {
        assertEquals(22, BRAND_PRESETS.size)
    }

    @Test
    fun `presets match the PC gallery values exactly`() {
        val expected = listOf(
            "esmeralda" to ("#0e7a5a" to "#f2a33a"),
            "tomate" to ("#c8372d" to "#f2b441"),
            "moka" to ("#7a4e33" to "#d99a4e"),
            "nori" to ("#24313d" to "#e4573d"),
            "brasa" to ("#d4501a" to "#f0b429"),
            "neon" to ("#7c3aed" to "#16c7e0"),
            "fresa" to ("#e0457b" to "#3cc0a6"),
            "trigo" to ("#b7791f" to "#7a9e3a"),
            "oceano" to ("#0b6fb8" to "#f2a33a"),
            "fresco" to ("#3f8f3a" to "#f08a24"),
            "chile" to ("#b3261e" to "#2f9e6e"),
            "lavanda" to ("#8e6bbf" to "#f08fb2"),
            "medianoche" to ("#2d3b55" to "#d9aa45"),
            "indigo" to ("#4a4fd8" to "#ffb020"),
            "ambar" to ("#b86f0a" to "#3b7a9e"),
            "tropical" to ("#ee6a1c" to "#2fb344"),
            "sakura" to ("#cf4d79" to "#6b94a8"),
            "matcha" to ("#5b7f3b" to "#c9a24a"),
            "turquesa" to ("#0e8c97" to "#ff7a59"),
            "vino" to ("#7d1f3d" to "#d8a657"),
            "carbon" to ("#303733" to "#e8c547"),
            "mostaza" to ("#c58b0a" to "#d6452f"),
        )
        assertEquals(expected.map { it.first }, BRAND_PRESETS.map { it.id })
        expected.forEachIndexed { index, (id, colors) ->
            val preset = BRAND_PRESETS[index]
            assertEquals(id, preset.id)
            assertEquals(colors.first, preset.brandHex)
            assertEquals(colors.second, preset.accentHex)
        }
    }

    @Test
    fun `preset ids are unique and every hex is strict rrggbb`() {
        assertEquals(BRAND_PRESETS.size, BRAND_PRESETS.map { it.id }.toSet().size)
        BRAND_PRESETS.forEach {
            assertTrue("${it.id} brand ${it.brandHex}", isValidHex6(it.brandHex))
            assertTrue("${it.id} accent ${it.accentHex}", isValidHex6(it.accentHex))
        }
    }

    @Test
    fun `preset tint is the PC 14 percent brand over white stripe`() {
        // color-mix(in srgb, #0e7a5a 14%, white) resolved once, no CSS math.
        assertEquals("#ddece7", presetTintHex("#0e7a5a"))
        assertNull(presetTintHex("not-a-hex"))
    }

    // -- hex + name validation -------------------------------------------------

    @Test
    fun `hex validation accepts strict rrggbb in any case`() {
        assertTrue(isValidHex6("#0e7a5a"))
        assertTrue(isValidHex6("#E8A33D"))
        assertTrue(isValidHex6("#000000"))
        assertFalse(isValidHex6(null))
        assertFalse(isValidHex6(""))
        assertFalse(isValidHex6("E8A33D"))
        assertFalse(isValidHex6("#FFF"))
        assertFalse(isValidHex6("#GGGGGG"))
        assertFalse(isValidHex6(" #E8A33D"))
    }

    @Test
    fun `draft validation names bad names and bad colors`() {
        val base = baseBrand()
        assertNull(validateBrandingDraft(base, "Bar Central", "#1C1F24", "#E8A33D"))
        assertEquals(
            "El nombre lleva de 1 a 60 caracteres.",
            validateBrandingDraft(base, "   ", "#1C1F24", "#E8A33D"),
        )
        assertEquals(
            "El nombre lleva de 1 a 60 caracteres.",
            validateBrandingDraft(base, "N".repeat(61), "#1C1F24", "#E8A33D"),
        )
        assertEquals(
            "Usa el formato #rrggbb.",
            validateBrandingDraft(base, "Bar", "red", "#E8A33D"),
        )
        assertEquals(
            "Usa el formato #rrggbb.",
            validateBrandingDraft(base, "Bar", "#1C1F24", "#12345"),
        )
    }

    // -- staging: only changed keys travel in one merged PATCH -----------------

    @Test
    fun `clean draft builds no body`() {
        val base = baseBrand()
        assertFalse(stagedBrandingDraft(base, base.shopName, base.primaryHex, base.accentHex, LogoMode.KEEP, "").isDirty)
        assertNull(
            stagedBrandingDraft(base, base.shopName, base.primaryHex, base.accentHex, LogoMode.KEEP, "").toPatchBody(),
        )
    }

    @Test
    fun `name only change sends a single merged patch with shop_name`() = runBlocking {
        val api = start()
        json(200, """{"shop_name":"Bar Nuevo","primary":"#1C1F24","accent":"#E8A33D","background":"#121417","has_logo":false,"updated_at":"t"}""")
        val base = baseBrand()

        val draft = stagedBrandingDraft(base, "Bar Nuevo", base.primaryHex, base.accentHex, LogoMode.KEEP, "")
        assertTrue(draft.isDirty)
        val brand = patchBranding(api, draft.toPatchBody()!!)

        assertEquals("Bar Nuevo", brand.shopName)
        assertEquals(1, server!!.requestCount)
        val request = takeRequest()
        assertEquals("/api/branding", request.path)
        assertEquals("PATCH", request.method)
        assertEquals("""{"shop_name":"Bar Nuevo"}""", request.body.readUtf8())
    }

    @Test
    fun `color changes send only the changed colors`() = runBlocking {
        val api = start()
        json(200, """{"shop_name":"Bar Central","primary":"#0e7a5a","accent":"#f2a33a","background":"#121417","has_logo":false,"updated_at":"t"}""")
        val base = baseBrand()

        val draft = stagedBrandingDraft(base, base.shopName, "#0e7a5a", "#f2a33a", LogoMode.KEEP, "")
        patchBranding(api, draft.toPatchBody()!!)

        val request = takeRequest()
        assertEquals("""{"primary":"#0e7a5a","accent":"#f2a33a"}""", request.body.readUtf8())
    }

    @Test
    fun `logo set sends the data url string`() = runBlocking {
        val api = start()
        json(200, """{"shop_name":"Bar Central","primary":"#1C1F24","accent":"#E8A33D","background":"#121417","has_logo":true,"updated_at":"t"}""")
        val base = baseBrand()
        val dataUrl = buildLogoDataUrl("image/png", byteArrayOf(1, 2, 3))

        val draft = stagedBrandingDraft(base, base.shopName, base.primaryHex, base.accentHex, LogoMode.SET, dataUrl)
        patchBranding(api, draft.toPatchBody()!!)

        val request = takeRequest()
        assertEquals("""{"logo_data_url":"$dataUrl"}""", request.body.readUtf8())
    }

    @Test
    fun `logo clear sends explicit null while keep omits the key`() = runBlocking {
        val api = start()
        repeat(2) {
            json(200, """{"shop_name":"Bar Central","primary":"#1C1F24","accent":"#E8A33D","background":"#121417","has_logo":false,"updated_at":"t"}""")
        }
        val base = baseBrand()

        val clear = stagedBrandingDraft(base, base.shopName, base.primaryHex, base.accentHex, LogoMode.CLEAR, "")
        assertTrue(clear.isDirty)
        patchBranding(api, clear.toPatchBody()!!)
        assertEquals("""{"logo_data_url":null}""", takeRequest().body.readUtf8())

        // A color-only change next to a kept logo never mentions the logo.
        val keep = stagedBrandingDraft(base, base.shopName, "#0e7a5a", base.accentHex, LogoMode.KEEP, "")
        patchBranding(api, keep.toPatchBody()!!)
        assertEquals("""{"primary":"#0e7a5a"}""", takeRequest().body.readUtf8())
    }

    @Test
    fun `patch 422 and 413 throw typed errors with named copy`() = runBlocking {
        val api = start()
        val failing = api
        json(422, """{"error":{"code":"validation_error","message":"bad color"}}""")
        json(413, """{"error":{"code":"too_large","message":"logo exceeds 512KiB"}}""")

        try {
            patchBranding(failing, buildJsonObject { put("primary", "red") })
            fail("expected ApiException(422)")
        } catch (e: dev.mott.app.data.ApiException) {
            assertEquals(422, e.status)
        }
        try {
            patchBranding(failing, buildJsonObject { put("logo_data_url", "data:image/png;base64,xx") })
            fail("expected ApiException(413)")
        } catch (e: dev.mott.app.data.ApiException) {
            assertEquals(413, e.status)
            assertEquals(
                "El logo es demasiado grande (máximo 512 KB).",
                brandingSaveInlineCopy(e.status),
            )
        }
        assertEquals(
            "Revisá los datos: nombre 1-60 y colores #rrggbb.",
            brandingSaveInlineCopy(422),
        )
        assertEquals(
            "El servidor se reinició o cambió su token. Volvé a vincular la app desde Conexión.",
            brandingSaveInlineCopy(401),
        )
    }

    // -- 512KiB logo guard (client-side, before any request) --------------------

    @Test
    fun `logo data urls carry the mime and decode back to bytes`() {
        val dataUrl = buildLogoDataUrl("image/png", byteArrayOf(1, 2, 3))
        assertEquals("data:image/png;base64,AQID", dataUrl)
        assertNull(validateLogoDataUrl(dataUrl))
    }

    @Test
    fun `logo guard rejects bad mime and oversized payloads`() {
        assertEquals(
            "El logo debe ser PNG, JPEG o SVG.",
            validateLogoDataUrl("data:image/gif;base64,AQID"),
        )
        assertEquals(
            "El logo debe ser PNG, JPEG o SVG.",
            validateLogoDataUrl("not-a-data-url"),
        )
        val big = buildLogoDataUrl("image/png", ByteArray(MAX_LOGO_BYTES + 1))
        assertTrue(logoDataUrlTooLarge(big))
        assertEquals("El logo no debe superar 512 KB.", validateLogoDataUrl(big))
        val small = buildLogoDataUrl("image/jpeg", ByteArray(1024))
        assertFalse(logoDataUrlTooLarge(small))
        assertNull(validateLogoDataUrl(small))
    }

    @Test
    fun `logo cap matches the hub 512KiB domain rule`() {
        assertEquals(512 * 1024, MAX_LOGO_BYTES)
    }

    // -- theme-mode UI state mapping (no Compose tests) --------------------------

    @Test
    fun `theme options list sistema claro oscuro in order`() {
        assertEquals(
            listOf(ThemeMode.SISTEMA, ThemeMode.CLARO, ThemeMode.OSCURO),
            personalizarThemeOptions(),
        )
    }

    @Test
    fun `theme modes resolve against the device flag`() {
        assertTrue(ThemeMode.OSCURO.resolveDark(systemDark = false))
        assertFalse(ThemeMode.CLARO.resolveDark(systemDark = true))
        assertTrue(ThemeMode.SISTEMA.resolveDark(systemDark = true))
        assertFalse(ThemeMode.SISTEMA.resolveDark(systemDark = false))
    }

    @Test
    fun `explicit null survives the retrofit json body`() = runBlocking {
        val api = start()
        json(200, """{"shop_name":"Bar Central","primary":"#1C1F24","accent":"#E8A33D","background":"#121417","has_logo":false,"updated_at":"t"}""")

        val body = buildJsonObject { put("logo_data_url", JsonNull) }
        patchBranding(api, body)

        assertEquals("""{"logo_data_url":null}""", takeRequest().body.readUtf8())
        assertEquals(1, server!!.requestCount)
    }
}
