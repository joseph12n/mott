package dev.mott.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.mott.app.data.BrandStore
import dev.mott.app.data.refreshBrandIfOnline
import dev.mott.app.ui.AppContainer
import dev.mott.app.ui.CatalogSection
import dev.mott.app.ui.ConnectionSection
import dev.mott.app.ui.ExpensesSection
import dev.mott.app.ui.MesasSection
import dev.mott.app.ui.PanelSection
import dev.mott.app.ui.PersonalizarSection
import dev.mott.app.ui.ProveedoresSection
import dev.mott.app.ui.SectionNav
import dev.mott.app.ui.ShellHeader
import dev.mott.app.ui.sectionForRoute
import dev.mott.app.ui.order.OrderViewModel
import dev.mott.app.ui.pair.PairingScreen
import dev.mott.app.ui.theme.MottTheme
import dev.mott.app.ui.theme.ThemeModeStore
import kotlinx.coroutines.launch

// Unified app: seven Figma sections behind a bottom NavigationBar under a
// shell header (title + subtitle + CONECTADO pill + Refrescar + theme
// toggle, mirroring the master Header + sidebar extras). The pairing gate
// stays first when unpaired: Conexión doubles as the pairing entry
// (QR-first PairingScreen), and the tab scaffold only mounts once a
// pairing exists. Each tab keeps its own back stack via save/restore.
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Theme mode is a persisted local choice (Sistema default =
            // today's system-theme behavior); T8 owns the full Claro/Oscuro
            // UI in Personalizar, the shell only carries this toggle.
            val appContext = applicationContext
            // Manual DI: the container owns Room + pairing + queue.
            // applicationContext avoids leaking the activity.
            val container = remember { AppContainer(appContext) }
            val brandStore = remember { BrandStore(appContext) }
            val themeModeStore = remember { ThemeModeStore(appContext) }
            var brand by remember { mutableStateOf(brandStore.get()) }
            var themeMode by remember { mutableStateOf(themeModeStore.get()) }
            var pairing by remember { mutableStateOf(container.pairingStore.get()) }
            val scope = rememberCoroutineScope()
            // Brand refresh on start when online; offline keeps the cached
            // brand (or token defaults) silently.
            LaunchedEffect(Unit) {
                if (refreshBrandIfOnline(appContext, container.pairingStore, brandStore)) {
                    brand = brandStore.get()
                }
            }
            MottTheme(mode = themeMode, brand = brand) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val currentPairing = pairing
                    if (currentPairing == null) {
                        PairingScreen(
                            store = container.pairingStore,
                            onPaired = {
                                // Refresh the hub brand after pairing
                                // without blocking navigation; offline
                                // keeps the cached brand silently.
                                scope.launch {
                                    if (refreshBrandIfOnline(appContext, container.pairingStore, brandStore)) {
                                        brand = brandStore.get()
                                    }
                                }
                                pairing = container.pairingStore.get()
                            },
                        )
                        return@Surface
                    }
                    val orderViewModel = remember {
                        OrderViewModel(catalog = container.orderCatalog(), sync = container.orderSync())
                    }
                    val orderState by orderViewModel.state.collectAsState()
                    val salesRepo = remember { container.salesRepo() }
                    val expensesRepo = remember { container.expensesRepo() }
                    var pendingProductId by remember { mutableStateOf<String?>(null) }
                    // Hub catalog pull once per paired session when online;
                    // offline keeps serving the cached snapshot silently.
                    LaunchedEffect(Unit) { orderViewModel.loadCatalog() }
                    val navController = rememberNavController()
                    val backStackEntry by navController.currentBackStackEntryAsState()
                    val selected = sectionForRoute(backStackEntry?.destination?.route)
                    // Shell Refrescar: re-triggers the current section's
                    // load. Panel/Mesas/Gastos listen to refreshTick; the
                    // catalog reloads through the ViewModel; Conexión and
                    // the T3 scaffolds hold no load so the tick is a no-op.
                    var refreshTick by remember { mutableStateOf(0) }
                    Scaffold(
                        topBar = {
                            ShellHeader(
                                section = selected,
                                connected = currentPairing != null,
                                themeMode = themeMode,
                                onRefresh = {
                                    refreshTick++
                                    if (selected == dev.mott.app.ui.AppSection.CATALOGO) {
                                        scope.launch { orderViewModel.loadCatalog() }
                                    }
                                },
                                onThemeModeChange = { next ->
                                    themeModeStore.save(next)
                                    themeMode = next
                                },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        },
                        bottomBar = {
                            SectionNav(
                                selected = selected,
                                onSelect = { section ->
                                    navController.navigate(section.route) {
                                        popUpTo(navController.graph.startDestinationId) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                mesasOpenCount = orderState.tables.count { it.occupied },
                            )
                        },
                    ) { innerPadding ->
                        NavHost(
                            navController = navController,
                            startDestination = dev.mott.app.ui.AppSection.PANEL.route,
                            modifier = Modifier.padding(innerPadding),
                        ) {
                            composable(dev.mott.app.ui.AppSection.PANEL.route) {
                                PanelSection(
                                    salesRepo = salesRepo,
                                    orderState = orderState,
                                    shopName = brand?.shopName,
                                    onNavigateConnection = {
                                        navController.navigate(dev.mott.app.ui.AppSection.CONEXION.route) {
                                            launchSingleTop = true
                                        }
                                    },
                                    onNavigateSuppliers = {
                                        navController.navigate(dev.mott.app.ui.AppSection.PROVEEDORES.route) {
                                            launchSingleTop = true
                                        }
                                    },
                                    expensesRepo = expensesRepo,
                                    showHeader = false,
                                    refreshSignal = refreshTick,
                                )
                            }
                            composable(dev.mott.app.ui.AppSection.MESAS.route) {
                                MesasSection(
                                    viewModel = orderViewModel,
                                    salesRepo = salesRepo,
                                    pendingProductId = pendingProductId,
                                    onConsumePending = { pendingProductId = null },
                                    shopName = brand?.shopName,
                                    onNavigateConnection = {
                                        navController.navigate(dev.mott.app.ui.AppSection.CONEXION.route) {
                                            launchSingleTop = true
                                        }
                                    },
                                    showHeader = false,
                                    refreshSignal = refreshTick,
                                )
                            }
                            composable(dev.mott.app.ui.AppSection.CATALOGO.route) {
                                CatalogSection(
                                    state = orderState,
                                    onPick = { productId ->
                                        pendingProductId = productId
                                        navController.navigate(dev.mott.app.ui.AppSection.MESAS.route) {
                                            popUpTo(navController.graph.startDestinationId) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    shopName = brand?.shopName,
                                    showHeader = false,
                                    onCatalogChanged = { scope.launch { orderViewModel.loadCatalog() } },
                                )
                            }
                            composable(dev.mott.app.ui.AppSection.PROVEEDORES.route) {
                                ProveedoresSection()
                            }
                            composable(dev.mott.app.ui.AppSection.GASTOS.route) {
                                ExpensesSection(
                                    repo = expensesRepo,
                                    onNavigateConnection = {
                                        navController.navigate(dev.mott.app.ui.AppSection.CONEXION.route) {
                                            launchSingleTop = true
                                        }
                                    },
                                    showHeader = false,
                                    refreshSignal = refreshTick,
                                )
                            }
                            composable(dev.mott.app.ui.AppSection.CONEXION.route) {
                                ConnectionSection(
                                    pairing = currentPairing,
                                    shopName = brand?.shopName,
                                    onUnpair = {
                                        container.pairingStore.clear()
                                        pairing = null
                                    },
                                    showHeader = false,
                                    kpis = dev.mott.app.ui.ConnectionKpis(
                                        tableCount = orderState.tables.size,
                                        productCount = orderState.products.size,
                                    ),
                                )
                            }
                            composable(dev.mott.app.ui.AppSection.PERSONALIZAR.route) {
                                PersonalizarSection(
                                    onBrandChanged = { brand = brandStore.get() },
                                    onThemeModeChanged = { themeMode = themeModeStore.get() },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
