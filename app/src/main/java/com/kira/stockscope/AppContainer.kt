package com.kira.stockscope

import android.content.Context
import com.kira.stockscope.data.network.NetworkModule
import com.kira.stockscope.data.repository.StockRepository
import com.kira.stockscope.data.repository.WatchlistStore

/** Minimal hand-rolled service locator; the app is small enough that a DI framework is overkill. */
class AppContainer(context: Context) {
    val repository = StockRepository(NetworkModule.api, NetworkModule.crumbManager)
    val watchlistStore = WatchlistStore(context.applicationContext)
}
