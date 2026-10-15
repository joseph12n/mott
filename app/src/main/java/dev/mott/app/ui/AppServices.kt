package dev.mott.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import dev.mott.app.data.AppMode
import dev.mott.app.data.Capabilities
import dev.mott.app.data.SalesRepo
import dev.mott.app.data.capabilitiesFor
import dev.mott.app.data.remote.MittApi

// What a section may do and which backend it talks to, resolved once by
// AppContainer from the current AppMode. Sections read this instead of
// building their own Retrofit client from PairingStore, so switching
// between the PC hub and standalone mode is one container change and no
// section ever asks the network whether it is allowed to work.
interface AppServices {
    fun mode(): AppMode

    fun capabilities(): Capabilities

    // True when the backend for this mode can take requests: always true in
    // MOBILE (the api is local, so no Wi-Fi is needed), the network check in
    // HUB.
    fun reachable(): Boolean

    // Catalog admin (create product, toggle availability, tables, tables'
    // suppliers) against whichever catalog is authoritative for the mode.
    fun catalogAdmin(): CatalogAdminRepo

    // Supplier CRUD seam (SalesRepo hosts it).
    fun suppliers(): SalesRepo

    // Branding read/write api; null when the mode has no backend yet (UNSET).
    fun brandingApi(): MittApi?

    // Logo as a data URL when it lives on the phone (MOBILE), null when the
    // logo is the hub's (HUB reads it over HTTP as before).
    suspend fun localLogoDataUrl(): String?
}

// Default for previews and any render without the container: no backend, no
// admin, nothing reachable. Fail-closed on purpose — a section that cannot
// see the services must not offer writes.
object OfflineAppServices : AppServices {
    override fun mode(): AppMode = AppMode.UNSET

    override fun capabilities(): Capabilities = capabilitiesFor(AppMode.UNSET)

    override fun reachable(): Boolean = false

    override fun catalogAdmin(): CatalogAdminRepo =
        CatalogAdminRepo(apiProvider = { null }, isOnline = { false })

    override fun suppliers(): SalesRepo =
        SalesRepo(apiProvider = { null }, isOnline = { false })

    override fun brandingApi(): MittApi? = null

    override suspend fun localLogoDataUrl(): String? = null
}

val LocalAppServices = staticCompositionLocalOf<AppServices> { OfflineAppServices }

// One-line notice for actions the PC owns in HUB mode: the phone keeps
// operating (orders, tabs, expenses), but catalog administration lives on
// the server.
@Composable
fun HubAdminNotice(modifier: Modifier = Modifier) {
    Text(
        text = "Se administra desde la PC",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}
