package dev.mott.app

import dev.mott.app.data.ApiException
import dev.mott.app.data.SalesRepo
import dev.mott.app.data.remote.ApiClient
import dev.mott.app.data.remote.MittApi
import dev.mott.app.ui.LoadFailureReason
import dev.mott.app.ui.LoadState
import dev.mott.app.ui.classifyLoadFailure
import dev.mott.app.ui.loadOpenTabsState
import dev.mott.app.ui.loadPanelState
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException

// T1 loader contract: the Panel/Mesas data loads never throw out of the
// loader; every failure lands in Failed with a named reason so the UI can
// state instead of crashing.
class LoadersTest {
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

    @Test
    fun `panel loader maps repo 401 to token_invalido without throwing`() = runBlocking {
        val api = start()
        json(401, """{"error":{"code":"unauthorized","message":"bad token"}}""")

        val state = loadPanelState(SalesRepo(apiProvider = { api }))

        assertEquals(LoadState.Failed(LoadFailureReason.TOKEN_INVALIDO), state)
    }

    @Test
    fun `tabs loader maps repo 401 to token_invalido without throwing`() = runBlocking {
        val api = start()
        json(401, """{"error":{"code":"unauthorized","message":"bad token"}}""")

        val state = loadOpenTabsState(SalesRepo(apiProvider = { api }))

        assertEquals(LoadState.Failed(LoadFailureReason.TOKEN_INVALIDO), state)
    }

    @Test
    fun `panel loader maps undecodable body to error_inesperado`() = runBlocking {
        val api = start()
        // Captive-portal style garbage under a JSON content type: the
        // kotlinx-serialization converter throws (not IOException), and the
        // loader must classify it instead of letting it escape.
        json(200, """<html><body>Login</body></html>""")

        val state = loadPanelState(SalesRepo(apiProvider = { api }))

        assertEquals(LoadState.Failed(LoadFailureReason.ERROR_INESPERADO), state)
    }

    @Test
    fun `panel loader maps io failure to sin_servidor`() = runBlocking {
        val state = loadPanelState(
            fetchToday = { throw IOException("wifi down") },
            fetchRecent = { emptyList() },
            fetchOpenTabs = { emptyList() },
        )

        assertEquals(LoadState.Failed(LoadFailureReason.SIN_SERVIDOR), state)
    }

    @Test
    fun `panel loader maps runtime failure to error_inesperado`() = runBlocking {
        val state = loadPanelState(
            fetchToday = { throw RuntimeException("boom") },
            fetchRecent = { emptyList() },
            fetchOpenTabs = { emptyList() },
        )

        assertEquals(LoadState.Failed(LoadFailureReason.ERROR_INESPERADO), state)
    }

    @Test
    fun `panel loader returns ready with data on success`() = runBlocking {
        val api = start()
        json(200, """{"date":"2026-10-07","count":3,"total_cents":4500}""")
        json(200, SalesTest.MITT_WIRE_SALES_JSON)
        json(200, SalesTest.MITT_WIRE_OPEN_TABS_JSON)

        val state = loadPanelState(SalesRepo(apiProvider = { api }))

        assertTrue(state is LoadState.Ready)
        val data = (state as LoadState.Ready).data
        assertEquals(3, data.today.count)
        assertEquals(listOf("s2", "s1"), data.recent.map { it.id })
        assertEquals(listOf("tab9"), data.openTabs.map { it.id })
    }

    @Test
    fun `unreachable hub keeps fail-soft ready with empty data`() = runBlocking {
        // SalesRepo serves cache/empty on IOException by contract, so an
        // unreachable hub is Ready(empty) at the loader, never a crash.
        val api = start()
        server!!.shutdown()
        server = null

        val state = loadPanelState(SalesRepo(apiProvider = { api }))

        assertTrue(state is LoadState.Ready)
        assertEquals(0, (state as LoadState.Ready).data.today.count)
    }

    @Test
    fun `classify names 401 and 403 as token_invalido`() {
        assertEquals(LoadFailureReason.TOKEN_INVALIDO, classifyLoadFailure(ApiException(401, "nope")))
        assertEquals(LoadFailureReason.TOKEN_INVALIDO, classifyLoadFailure(ApiException(403, "nope")))
    }

    @Test
    fun `classify names io as sin_servidor and everything else as error_inesperado`() {
        assertEquals(LoadFailureReason.SIN_SERVIDOR, classifyLoadFailure(IOException("x")))
        assertEquals(LoadFailureReason.SIN_SERVIDOR, classifyLoadFailure(SocketTimeoutException("x")))
        assertEquals(LoadFailureReason.ERROR_INESPERADO, classifyLoadFailure(ApiException(500, "hub")))
        assertEquals(LoadFailureReason.ERROR_INESPERADO, classifyLoadFailure(RuntimeException("x")))
    }
}
