package dev.mott.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.mott.app.ui.AppContainer
import dev.mott.app.ui.order.ConfirmScreen
import dev.mott.app.ui.order.OrderViewModel
import dev.mott.app.ui.order.ProductsScreen
import dev.mott.app.ui.order.TablesScreen
import dev.mott.app.ui.pair.PairingScreen
import dev.mott.app.ui.theme.MottTheme

private const val PairRoute = "pair"
private const val TablesRoute = "tables"
private const val ProductsRoute = "products"
private const val ConfirmRoute = "confirm"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Night-bar default: dark theme is forced, never follows system.
            MottTheme(darkTheme = true) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    // Manual DI: the container owns Room + pairing + queue.
                    // applicationContext avoids leaking the activity.
                    val container = remember { AppContainer(applicationContext) }
                    val orderViewModel = remember { OrderViewModel(sync = container.orderSync()) }
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
                                    navController.navigate(TablesRoute) {
                                        popUpTo(PairRoute) { inclusive = true }
                                    }
                                },
                            )
                        }
                        composable(TablesRoute) {
                            TablesScreen(
                                state = state,
                                onSelectTable = orderViewModel::selectTable,
                                onNext = { navController.navigate(ProductsRoute) },
                            )
                        }
                        composable(ProductsRoute) {
                            ProductsScreen(
                                state = state,
                                onIncrement = orderViewModel::increment,
                                onDecrement = orderViewModel::decrement,
                                onConfirm = { navController.navigate(ConfirmRoute) },
                                onBack = { navController.popBackStack() },
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
                            )
                        }
                    }
                }
            }
        }
    }
}
