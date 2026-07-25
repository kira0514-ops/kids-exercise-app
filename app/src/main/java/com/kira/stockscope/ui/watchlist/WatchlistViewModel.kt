package com.kira.stockscope.ui.watchlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kira.stockscope.data.repository.StockRepository
import com.kira.stockscope.data.repository.WatchlistStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface RowState {
    data class Loading(val symbol: String) : RowState
    data class Loaded(
        val symbol: String,
        val name: String,
        val price: Double?,
        val changePercent: Double?,
        val currency: String?,
        val scoreTotal: Int,
        val tier: String
    ) : RowState
    data class Failed(val symbol: String, val message: String) : RowState
}

data class WatchlistUiState(
    val rows: List<RowState> = emptyList(),
    val isRefreshing: Boolean = false
)

class WatchlistViewModel(
    private val repository: StockRepository,
    private val watchlistStore: WatchlistStore
) : ViewModel() {

    private val rowCache = MutableStateFlow<Map<String, RowState>>(emptyMap())
    private val loadedSymbols = mutableSetOf<String>()
    private val refreshing = MutableStateFlow(false)

    val uiState: StateFlow<WatchlistUiState> = combine(
        watchlistStore.entries,
        rowCache,
        refreshing
    ) { entries, cache, isRefreshing ->
        entries.forEach { entry ->
            if (loadedSymbols.add(entry.symbol)) {
                loadRow(entry.symbol)
            }
        }
        WatchlistUiState(
            rows = entries.map { cache[it.symbol] ?: RowState.Loading(it.symbol) },
            isRefreshing = isRefreshing
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WatchlistUiState())

    fun addSymbol(rawSymbol: String) {
        val symbol = rawSymbol.trim().uppercase()
        if (symbol.isBlank()) return
        viewModelScope.launch {
            watchlistStore.add(symbol)
            loadedSymbols.remove(symbol)
            loadRow(symbol)
        }
    }

    fun removeSymbol(symbol: String) {
        viewModelScope.launch {
            watchlistStore.remove(symbol)
            loadedSymbols.remove(symbol)
            rowCache.update { it - symbol }
        }
    }

    fun refreshAll() {
        viewModelScope.launch {
            refreshing.value = true
            val symbols = uiState.value.rows.map { row ->
                when (row) {
                    is RowState.Loading -> row.symbol
                    is RowState.Loaded -> row.symbol
                    is RowState.Failed -> row.symbol
                }
            }
            symbols.forEach { loadRow(it) }
            refreshing.value = false
        }
    }

    private fun loadRow(symbol: String) {
        rowCache.update { it + (symbol to RowState.Loading(symbol)) }
        viewModelScope.launch {
            // A quick quote (one request) rather than the full report (target + up
            // to 5 peers + news + recommendations) — the watchlist just needs price
            // and a score, and fetching the full report per row would multiply into
            // dozens of Yahoo requests as soon as more than one or two tickers are
            // watched, tripping their rate limiting.
            val result = repository.getQuickQuote(symbol)
            val newState = result.fold(
                onSuccess = { quote ->
                    RowState.Loaded(
                        symbol = symbol,
                        name = quote.snapshot.name,
                        price = quote.snapshot.price,
                        changePercent = quote.snapshot.changePercent,
                        currency = quote.snapshot.currency,
                        scoreTotal = quote.score.total,
                        tier = quote.score.tier
                    )
                },
                onFailure = { error ->
                    RowState.Failed(symbol, error.message ?: "Failed to load")
                }
            )
            rowCache.update { it + (symbol to newState) }
        }
    }

    class Factory(
        private val repository: StockRepository,
        private val watchlistStore: WatchlistStore
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return WatchlistViewModel(repository, watchlistStore) as T
        }
    }
}
