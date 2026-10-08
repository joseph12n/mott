package dev.mott.app

import dev.mott.app.data.ApiException
import dev.mott.app.data.SalesRepo
import dev.mott.app.data.Supplier
import dev.mott.app.data.remote.ApiClient
import dev.mott.app.data.remote.MittApi
import dev.mott.app.ui.LoadFailureReason
import dev.mott.app.ui.LoadState
import dev.mott.app.ui.classifyLoadFailure
import dev.mott.app.ui.loadSuppliersState
import dev.mott.app.ui.resolveSupplierSelection
import dev.mott.app.ui.supplierDirty
import dev.mott.app.ui.supplierMissingInlineCopy
import dev.mott.app.ui.supplierSaveInlineCopy
import dev.mott.app.ui.validateSupplierInput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.util.concurrent.TimeUnit

// T7 Proveedores parity: supplier CRUD over the mitt suppliers wire
// (MockWebServer for HTTP, pure asserts for the validation/copy/selection
// helpers). Mirrors the PC reduction exactly: {name, phone, note}, no
// ledger, no balances. No Compose UI tests.
class SuppliersAdminTest {
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

    private fun empty(code: Int) {
        server!!.enqueue(MockResponse().setResponseCode(code))
    }

    private fun start(): MittApi {
        val s = MockWebServer()
        s.start()
        server = s
        return ApiClient.build(s.url("/").toString(), "pair-token", logger = false)
    }

    private fun repo(api: MittApi, online: Boolean = true) =
        SalesRepo(apiProvider = { api }, isOnline = { online })

    private fun takeRequest() = server!!.takeRequest(5, TimeUnit.SECONDS)!!

    // -- client validation (hub limits: name 1..80, phone <=40, note <=200) --

    @Test
    fun `blank name is rejected with honest copy`() {
        assertEquals("Escriba el nombre del proveedor.", validateSupplierInput("", "", ""))
        assertEquals("Escriba el nombre del proveedor.", validateSupplierInput("   ", "1", "n"))
    }

    @Test
    fun `name longer than 80 chars is rejected`() {
        assertEquals(
            "El nombre debe tener como máximo 80 caracteres.",
            validateSupplierInput("N".repeat(81), "", ""),
        )
    }

    @Test
    fun `phone longer than 40 chars is rejected`() {
        assertEquals(
            "El teléfono debe tener como máximo 40 caracteres.",
            validateSupplierInput("Hielo", "1".repeat(41), ""),
        )
    }

    @Test
    fun `note longer than 200 chars is rejected`() {
        assertEquals(
            "La nota debe tener como máximo 200 caracteres.",
            validateSupplierInput("Hielo", "", "n".repeat(201)),
        )
    }

    @Test
    fun `boundary lengths are accepted`() {
        assertNull(validateSupplierInput("N".repeat(80), "1".repeat(40), "n".repeat(200)))
    }

    @Test
    fun `plain valid input is accepted`() {
        assertNull(validateSupplierInput("Distribuidora Sur", "555-1234", "Entrega martes"))
        assertNull(validateSupplierInput("Hielo", "", ""))
    }

    // -- inline named-error copy -------------------------------------------

    @Test
    fun `save 422 names the field limits`() {
        assertEquals(
            "Revisá los datos: nombre 1-80, teléfono hasta 40 y nota hasta 200 caracteres.",
            supplierSaveInlineCopy(422),
        )
        assertNull(supplierSaveInlineCopy(500))
        assertNull(supplierSaveInlineCopy(401))
    }

    @Test
    fun `missing 404 names the gone supplier`() {
        assertEquals("El proveedor ya no existe.", supplierMissingInlineCopy(404))
        assertNull(supplierMissingInlineCopy(500))
        assertNull(supplierMissingInlineCopy(422))
    }

    // -- selection + dirty (PC useEffect parity) ----------------------------

    @Test
    fun `selection keeps a valid id and falls back to the first`() {
        val suppliers = listOf(Supplier("s1", "Alfa", "", ""), Supplier("s2", "Zeta", "", ""))
        assertEquals("s2", resolveSupplierSelection(suppliers, "s2"))
        assertEquals("s1", resolveSupplierSelection(suppliers, "ghost"))
        assertEquals("s1", resolveSupplierSelection(suppliers, null))
        assertNull(resolveSupplierSelection(emptyList(), "s1"))
    }

    @Test
    fun `dirty tracks name trimmed plus phone and note`() {
        val current = Supplier("s1", "Alfa", "555", "nota")
        assertFalse(supplierDirty(current, "Alfa", "555", "nota"))
        assertFalse(supplierDirty(current, "  Alfa  ", "555", "nota"))
        assertTrue(supplierDirty(current, "Beta", "555", "nota"))
        assertTrue(supplierDirty(current, "Alfa", "556", "nota"))
        assertTrue(supplierDirty(current, "Alfa", "555", "otra"))
    }

    // -- list (hub order is name-ordered, client preserves it) --------------

    @Test
    fun `list maps suppliers preserving hub order`() = runBlocking {
        val api = start()
        json(200, """{"suppliers":[{"id":"s1","name":"Alfa","phone":"","note":""},{"id":"s2","name":"Zeta","phone":"555","note":"martes"}]}""")

        val suppliers = repo(api).listSuppliers()

        assertEquals(
            listOf(
                Supplier("s1", "Alfa", "", ""),
                Supplier("s2", "Zeta", "555", "martes"),
            ),
            suppliers,
        )
        val request = takeRequest()
        assertEquals("/api/suppliers", request.path)
        assertEquals("GET", request.method)
    }

    // -- create --------------------------------------------------------------

    @Test
    fun `create posts name phone note and maps the 201`() = runBlocking {
        val api = start()
        json(201, """{"id":"s9","name":"Hielo","phone":"555","note":"martes"}""")

        val created = repo(api).createSupplier("Hielo", "555", "martes")

        assertEquals(Supplier("s9", "Hielo", "555", "martes"), created)
        val request = takeRequest()
        assertEquals("/api/suppliers", request.path)
        assertEquals("POST", request.method)
        assertEquals("""{"name":"Hielo","phone":"555","note":"martes"}""", request.body.readUtf8())
    }

    @Test
    fun `create 422 throws typed error for the inline copy`() = runBlocking {
        val api = start()
        json(422, """{"error":{"code":"validation_error","message":"name must be 1..80"}}""")

        try {
            repo(api).createSupplier("N".repeat(81), "", "")
            fail("expected ApiException(422)")
        } catch (e: ApiException) {
            assertEquals(422, e.status)
            assertEquals(
                "Revisá los datos: nombre 1-80, teléfono hasta 40 y nota hasta 200 caracteres.",
                supplierSaveInlineCopy(e.status),
            )
        }
    }

    // -- patch (partial: only set fields travel) ------------------------------

    @Test
    fun `patch sends partial body and maps the 200`() = runBlocking {
        val api = start()
        json(200, """{"id":"s1","name":"Alfa","phone":"999","note":""}""")

        val patched = repo(api).patchSupplier("s1", phone = "999")

        assertEquals(Supplier("s1", "Alfa", "999", ""), patched)
        val request = takeRequest()
        assertEquals("/api/suppliers/s1", request.path)
        assertEquals("PATCH", request.method)
        assertEquals("""{"phone":"999"}""", request.body.readUtf8())
    }

    @Test
    fun `patch 404 throws typed error with missing copy`() = runBlocking {
        val api = start()
        json(404, """{"error":{"code":"not_found","message":"unknown supplier"}}""")

        try {
            repo(api).patchSupplier("ghost", phone = "1")
            fail("expected ApiException(404)")
        } catch (e: ApiException) {
            assertEquals(404, e.status)
            assertEquals("El proveedor ya no existe.", supplierMissingInlineCopy(e.status))
        }
    }

    // -- delete (204 unrestricted, no guards) -----------------------------------

    @Test
    fun `delete sends DELETE and succeeds on 204`() = runBlocking {
        val api = start()
        empty(204)

        repo(api).deleteSupplier("s1")

        val request = takeRequest()
        assertEquals("/api/suppliers/s1", request.path)
        assertEquals("DELETE", request.method)
    }

    @Test
    fun `delete removes the supplier from the next list`() = runBlocking {
        val api = start()
        val failing = repo(api)
        empty(204)
        json(200, """{"suppliers":[{"id":"s2","name":"Zeta","phone":"","note":""}]}""")

        failing.deleteSupplier("s1")
        val remaining = failing.listSuppliers()

        assertEquals(listOf("s2"), remaining.map { it.id })
    }

    @Test
    fun `delete 404 throws typed error with missing copy`() = runBlocking {
        val api = start()
        json(404, """{"error":{"code":"not_found","message":"unknown supplier"}}""")

        try {
            repo(api).deleteSupplier("ghost")
            fail("expected ApiException(404)")
        } catch (e: ApiException) {
            assertEquals(404, e.status)
            assertEquals("El proveedor ya no existe.", supplierMissingInlineCopy(e.status))
        }
    }

    // -- loader contract ---------------------------------------------------------

    @Test
    fun `load maps data to ready and failures to named reasons`() = runBlocking {
        val ready = loadSuppliersState { listOf(Supplier("s1", "Alfa", "", "")) }
        assertEquals(listOf("s1"), (ready as LoadState.Ready).data.map { it.id })

        val failed = loadSuppliersState { throw IOException("down") }
        assertEquals(LoadFailureReason.SIN_SERVIDOR, (failed as LoadState.Failed).reason)
    }

    @Test
    fun `load rethrows cancellation instead of classifying it`() = runBlocking {
        try {
            loadSuppliersState { throw CancellationException("teardown") }
            fail("expected CancellationException")
        } catch (e: CancellationException) {
            assertEquals(LoadFailureReason.SIN_SERVIDOR, classifyLoadFailure(IOException("down")))
        }
    }

    // -- offline guard + auth -------------------------------------------------------

    @Test
    fun `supplier mutations while offline throw io for sin_servidor`() = runBlocking {
        val api = start()
        val offline = repo(api, online = false)

        assertIo { offline.listSuppliers() }
        assertIo { offline.createSupplier("Hielo", "", "") }
        assertIo { offline.patchSupplier("s1", phone = "1") }
        assertIo { offline.deleteSupplier("s1") }
        assertEquals(0, server!!.requestCount)
        assertEquals(LoadFailureReason.SIN_SERVIDOR, classifyLoadFailure(IOException("offline")))
    }

    @Test
    fun `supplier auth failures classify as token_invalido`() = runBlocking {
        val api = start()
        val failing = repo(api)
        repeat(2) { json(401, """{"error":{"code":"unauthorized","message":"bad token"}}""") }

        assertApiException(401) { failing.listSuppliers() }
        assertApiException(401) { failing.deleteSupplier("s1") }
        assertEquals(
            LoadFailureReason.TOKEN_INVALIDO,
            classifyLoadFailure(ApiException(401, "unauthorized")),
        )
    }

    private suspend fun assertApiException(status: Int, call: suspend () -> Unit) {
        try {
            call()
        } catch (e: ApiException) {
            assertEquals(status, e.status)
            return
        }
        fail("expected ApiException($status)")
    }

    private suspend fun assertIo(call: suspend () -> Unit) {
        try {
            call()
        } catch (e: IOException) {
            return
        }
        fail("expected IOException while offline")
    }
}
