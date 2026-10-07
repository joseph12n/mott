package dev.mott.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.mott.app.data.BrandStore
import dev.mott.app.data.refreshBrandIfOnline
import dev.mott.app.ui.AppContainer
import dev.mott.app.ui.order.ConfirmScreen
import dev.mott.app.ui.order.OrderViewModel
import dev.mott.app.ui.order.ProductsScreen
import dev.mott.app.ui.order.TablesScreen
import dev.mott.app.ui.pair.PairingScreen
import dev.mott.app.ui.theme.MottTheme
import kotlinx.coroutines.launch

private const val PairRoute = "pair"
private const val TablesRoute = "tables"
private const val ProductsRoute = "products"
private const val ConfirmRoute = "confirm"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // System theme by owner decision: no in-app toggle, the device
            // picks dark/light and the hub brand paints both schemes.
            val appContext = applicationContext
            // Manual DI: the container owns Room + pairing + queue.
            // applicationContext avoids leaking the activity.
            val container = remember { AppContainer(appContext) }
            val brandStore = remember { BrandStore(appContext) }
            var brand by remember { mutableStateOf(brandStore.get()) }
            val scope = rememberCoroutineScope()
            // Brand refresh on start when online; offline keeps the cached
            // brand (or token defaults) silently.
            LaunchedEffect(Unit) {
                if (refreshBrandIfOnline(appContext, container.pairingStore, brandStore)) {
                    brand = brandStore.get()
                }
            }
            MottTheme(brand = brand) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val orderViewModel = remember { OrderViewModel(catalog = container.orderCatalog(), sync = container.orderSync()) }
                    val state by orderViewModel.state.collectAsState()
                    val navController = rememberNavController()
                    val startRoute = remember {
                        if (container.pairingStore.get() != null) TablesRoute else PairRoute
                    }
                    NavHost(
                        navController = navController,
                        startDestination = startRoute,
                    ) {
                        composable(PairRoute) {
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
                                    navController.navigate(TablesRoute) {
                                        popUpTo(PairRoute) { inclusive = true }
                                    }
                                },
                            )
                        }
                        composable(TablesRoute) {
                            // Hub catalog pull on entry when online; offline
                            // keeps serving the cached snapshot silently.
                            LaunchedEffect(Unit) { orderViewModel.loadCatalog() }
                            TablesScreen(
                                state = state,
                                onSelectTable = orderViewModel::selectTable,
                                onNext = { navController.navigate(ProductsRoute) },
                                shopName = brand?.shopName,
                            )
                        }
                        composable(ProductsRoute) {
                            ProductsScreen(
                                state = state,
                                onIncrement = orderViewModel::increment,
                                onDecrement = orderViewModel::decrement,
                                onConfirm = { navController.navigate(ConfirmRoute) },
                                onBack = { navController.popBackStack() },
                                shopName = brand?.shopName,
                            )
                        }
                        composable(ConfirmRoute) {
                            ConfirmScreen(
                                state = state,
                                onConfirm = { orderViewModel.submit() },
                                onNewOrder = {
                                    orderViewModel.startNewOrder()
                                    navController.popBackStack(TablesRoute, inclusive = false)
                                },
                                onBack = { navController.popBackStack() },
                                shopName = brand?.shopName,
                            )
                        }
                    }
                }
            }
        }
    }
}
