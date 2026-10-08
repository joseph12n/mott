package dev.mott.app.ui

import dev.mott.app.data.ApiException
import dev.mott.app.data.Sale
import dev.mott.app.data.SalesRepo
import dev.mott.app.data.TodayResult
import dev.mott.app.domain.Tab
import kotlinx.coroutines.CancellationException
import java.io.IOException

// Named load-failure reasons for the section loaders. The names mirror
// the hub error vocabulary: token_invalido (expired pairing after a hub
// restart), sin_servidor (connectivity), error_inesperado (anything else,
// including serialization failures). English identifiers, Spanish copy.
enum class LoadFailureReason {
    TOKEN_INVALIDO,
    SIN_SERVIDOR,
    ERROR_INESPERADO,
}

// User-facing copy per reason, in the app's existing Spanish tone.
fun LoadFailureReason.title(): String = when (this) {
    LoadFailureReason.TOKEN_INVALIDO -> "Token inválido"
    LoadFailureReason.SIN_SERVIDOR -> "Sin servidor"
    LoadFailureReason.ERROR_INESPERADO -> "Error inesperado"
}

fun LoadFailureReason.message(): String = when (this) {
    LoadFailureReason.TOKEN_INVALIDO ->
        "El servidor se reinició o cambió su token. Volvé a vincular la app desde Conexión."
    LoadFailureReason.SIN_SERVIDOR ->
        "No se llega al servidor. Revisá que el celular esté en el mismo Wi-Fi del bar y reintentá."
    LoadFailureReason.ERROR_INESPERADO ->
        "Algo salió mal al cargar los datos. Intentá de nuevo."
}

// Re-pair shortcut visibility for the shared error card: only a rejected
// pairing token (hub restart rotates it) is fixable from Conexión.
fun LoadFailureReason.showRePair(): Boolean = this == LoadFailureReason.TOKEN_INVALIDO

// Tri-state result of a section load: the UI renders Loading, Ready(data)
// or Failed(reason) and never sees a raw exception.
sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data class Ready<T>(val data: T) : LoadState<T>
    data class Failed(val reason: LoadFailureReason) : LoadState<Nothing>
}

// Panel payload: everything the Panel section renders in one load.
data class PanelData(
    val today: TodayResult,
    val recent: List<Sale>,
    val openTabs: List<Tab>,
)

// Maps a thrown failure onto its named reason. ApiException already
// carries the HTTP status: 401/403 mean the pairing token was rejected
// (hub restart rotates it), IO means connectivity, everything else
// (serialization, unexpected runtime) is error_inesperado.
fun classifyLoadFailure(t: Throwable): LoadFailureReason = when {
    t is ApiException && (t.status == 401 || t.status == 403) -> LoadFailureReason.TOKEN_INVALIDO
    t is IOException -> LoadFailureReason.SIN_SERVIDOR
    else -> LoadFailureReason.ERROR_INESPERADO
}

// Runs a load catching every failure except coroutine cancellation
// (cancellation must keep propagating so LaunchedEffect teardown works).
private inline fun <T> loadCatching(load: () -> T): LoadState<T> {
    val result = runCatching(load)
    if (result.isFailure) {
        val cause = result.exceptionOrNull()
        if (cause is CancellationException) throw cause
    }
    return result.fold(
        onSuccess = { LoadState.Ready(it) },
        onFailure = { LoadState.Failed(classifyLoadFailure(it)) },
    )
}

// JVM-testable Panel load: the lambda seam keeps the mapping honest for
// failures SalesRepo itself would never throw (its IO fail-soft serves
// cache/empty); the repo overload below is the production entry.
suspend fun loadPanelState(
    fetchToday: suspend () -> TodayResult,
    fetchRecent: suspend () -> List<Sale>,
    fetchOpenTabs: suspend () -> List<Tab>,
): LoadState<PanelData> = loadCatching {
    PanelData(
        today = fetchToday(),
        recent = fetchRecent(),
        openTabs = fetchOpenTabs(),
    )
}

suspend fun loadPanelState(repo: SalesRepo): LoadState<PanelData> = loadCatching {
    PanelData(
        today = repo.today(),
        recent = repo.recent(50),
        openTabs = repo.openTabs(),
    )
}

// Mesas operate load: the open tabs behind the per-table totals.
suspend fun loadOpenTabsState(fetch: suspend () -> List<Tab>): LoadState<List<Tab>> =
    loadCatching { fetch() }

suspend fun loadOpenTabsState(repo: SalesRepo): LoadState<List<Tab>> =
    loadCatching { repo.openTabs() }
