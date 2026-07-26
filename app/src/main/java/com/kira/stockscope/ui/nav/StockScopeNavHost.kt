package com.kira.stockscope.ui.nav

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kira.stockscope.AppContainer
import com.kira.stockscope.ui.detail.StockDetailScreen
import com.kira.stockscope.ui.detail.StockDetailViewModel
import com.kira.stockscope.ui.watchlist.WatchlistScreen
import com.kira.stockscope.ui.watchlist.WatchlistViewModel

private const val ROUTE_WATCHLIST = "watchlist"
private const val ROUTE_DETAIL = "detail/{symbol}"
private const val ARG_SYMBOL = "symbol"

@Composable
fun StockScopeNavHost(container: AppContainer) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = ROUTE_WATCHLIST) {
        composable(ROUTE_WATCHLIST) {
            val viewModel: WatchlistViewModel = viewModel(
                factory = WatchlistViewModel.Factory(container.repository, container.watchlistStore)
            )
            WatchlistScreen(
                viewModel = viewModel,
                onOpenSymbol = { symbol -> navController.navigate("detail/$symbol") }
            )
        }
        composable(
            route = ROUTE_DETAIL,
            arguments = listOf(navArgument(ARG_SYMBOL) { type = NavType.StringType })
        ) { backStackEntry ->
            val symbol = backStackEntry.arguments?.getString(ARG_SYMBOL).orEmpty()
            val viewModel: StockDetailViewModel = viewModel(
                key = symbol,
                factory = StockDetailViewModel.Factory(container.repository, symbol)
            )
            StockDetailScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
