package dev.mott.app

import dev.mott.app.data.PendingQueue
import dev.mott.app.data.remote.ApiClient
import dev.mott.app.data.remote.MittApi
import dev.mott.app.domain.Product
import dev.mott.app.ui.CatalogAdminRepo
import dev.mott.app.ui.parseProductPriceToCents
import dev.mott.app.ui.pickableProducts
import dev.mott.app.ui.productCreateInlineCopy
import dev.mott.app.ui.productToggleInlineCopy
import dev.mott.app.ui.validateProductInput
import dev.mott.app.data.ApiException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.util.concurrent.TimeUnit

// T6 Catalogo parity: hub has no category field (flat list, like the mitt
// PC Catalogo reduction) and no product DELETE (trash = PATCH
// available=false). JVM-pure: validation, pick-flow exclusion, and the
// admin wire mapping over MockWebServer.
class CatalogAdminTest {
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

    private fun admin(
        api: MittApi,
        online: Boolean = true,
    ) = CatalogAdminRepo(apiProvider = { api }, isOnline = { online })

    @Test
    fun `product price parses dot and comma, zero allowed`() {
        assertEquals(1250L, parseProductPriceToCents("12.50"))
        assertEquals(1250L, parseProductPriceToCents("12,50"))
        assertEquals(1200L, parseProductPriceToCents("12"))
        // Hub rule is price_cents >= 0: a free item is a valid product,
        // unlike the Gastos amount which must stay positive.
        assertEquals(0L, parseProductPriceToCents("0"))
        assertEquals(0L, parseProductPriceToCents("0,00"))
    }

    @Test
    fun `product price rejects blank garbage and negative`() {
        assertNull(parseProductPriceToCents(""))
        assertNull(parseProductPriceToCents("  "))
        assertNull(parseProductPriceToCents("abc"))
        assertNull(parseProductPriceToCents("-3"))
        assertNull(parseProductPriceToCents("-1,50"))
    }

    @Test
    fun `product input validation names missing name and price`() {
        assertEquals("Escribí el nombre del producto.", validateProductInput("", 100L))
        assertEquals("Escribí el nombre del producto.", validateProductInput("   ", 100L))
        assertEquals("Escribí un precio válido.", validateProductInput("Fernet", null))
        assertNull(validateProductInput("Fernet", 1250L))
        assertNull(validateProductInput("Fernet", 0L))
    }

    @Test
    fun `product create copy names the 422 rule`() {
        assertEquals("Revisá el nombre y el precio del producto.", productCreateInlineCopy(422))
        assertNull(productCreateInlineCopy(500))
        assertEquals("El producto ya no existe.", productToggleInlineCopy(404))
        assertNull(productToggleInlineCopy(422))
    }

    @Test
    fun `pickable products exclude unavailable from the mesas flow`() {
        val products = listOf(
            Product("p1", "Fernet", 1250, available = true),
            Product("p6", "Papas fritas", 700, available = false),
        )

        assertEquals(listOf("p1"), pickableProducts(products).map { it.id })
        assertTrue(pickableProducts(emptyList()).isEmpty())
    }

    @Test
    fun `create product posts name and price_cents and maps 201`() = runBlocking {
        val api = start()
        json(201, """{"id":"p9","name":"Fernet","price_cents":1250,"available":true}""")
        val repo = admin(api)

        val created = repo.createProduct("Fernet", 1250L)

        assertEquals("p9", created.id)
        assertEquals("Fernet", created.name)
        assertEquals(1250L, created.priceCents)
        assertTrue(created.available)
        val request = server!!.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("/api/products", request.path)
        assertEquals("POST", request.method)
        val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals("Fernet", body["name"]?.jsonPrimitive?.contentOrNull)
        assertEquals(1250L, body["price_cents"]?.jsonPrimitive?.long)
    }

    @Test
    fun `create product surfaces 422 as typed error`() = runBlocking {
        val api = start()
        json(422, """{"error":{"code":"validation_error","message":"price must not be negative"}}""")
        val repo = admin(api)

        try {
            repo.createProduct("Fernet", -50L)
        } catch (e: ApiException) {
            assertEquals(422, e.status)
            return@runBlocking
        }
        fail("expected ApiException(422)")
    }

    @Test
    fun `trash patches available false and maps 200`() = runBlocking {
        val api = start()
        json(200, """{"id":"p1","name":"Fernet","price_cents":1250,"available":false}""")
        val repo = admin(api)

        val patched = repo.setAvailable("p1", false)

        assertEquals("p1", patched.id)
        assertTrue(!patched.available)
        val request = server!!.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("/api/products/p1", request.path)
        assertEquals("PATCH", request.method)
        val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals(false, body["available"]?.jsonPrimitive?.boolean)
    }

    @Test
    fun `admin create offline throws io without http`() = runBlocking {
        val api = start()
        val repo = admin(api, online = false)

        try {
            repo.createProduct("Fernet", 1250L)
        } catch (e: IOException) {
            assertEquals(0, server!!.requestCount)
            return@runBlocking
        }
        fail("expected IOException offline")
    }

    @Test
    fun `admin toggle unpaired throws io`() = runBlocking {
        val repo = CatalogAdminRepo(apiProvider = { null })

        try {
            repo.setAvailable("p1", false)
        } catch (e: IOException) {
            return@runBlocking
        }
        fail("expected IOException unpaired")
    }
}
