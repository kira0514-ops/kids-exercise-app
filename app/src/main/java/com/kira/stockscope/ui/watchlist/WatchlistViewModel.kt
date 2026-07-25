package com.kira.stockscope.ui.watchlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kira.stockscope.data.repository.StockRepository
import com.kira.stockscope.data.repository.WatchlistStore
import com.kira.stockscope.model.QuickQuote
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

fun RowState.symbol(): String = when (this) {
    is RowState.Loading -> symbol
    is RowState.Loaded -> symbol
    is RowState.Failed -> symbol
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

    private val _addError = MutableStateFlow<String?>(null)
    val addError: StateFlow<String?> = _addError.asStateFlow()

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

    /**
     * Validates the ticker with a single quick-quote request before persisting it,
     * so a typo shows an inline error instead of sitting in the watchlist forever
     * as a permanently failed row that the user has to notice and remove by hand.
     */
    fun addSymbol(rawSymbol: String) {
        val symbol = rawSymbol.trim().uppercase()
        if (symbol.isBlank()) return
        _addError.value = null
        viewModelScope.launch {
            repository.getQuickQuote(symbol).fold(
                onSuccess = { quote ->
                    watchlistStore.add(symbol)
                    loadedSymbols.add(symbol)
                    rowCache.update { it + (symbol to quote.toLoadedRow(symbol)) }
                },
                onFailure = {
                    _addError.value = "Couldn't find \"$symbol\". Check the ticker symbol."
                }
            )
        }
    }

    fun clearAddError() {
        _addError.value = null
    }

    fun removeSymbol(symbol: String) {
        viewModelScope.launch {
            watchlistStore.remove(symbol)
            loadedSymbols.remove(symbol)
            rowCache.update { it - symbol }
        }
    }

    /** Reloads every row concurrently and only clears [isRefreshing] once all of them finish. */
    fun refreshAll() {
        viewModelScope.launch {
            refreshing.value = true
            val symbols = uiState.value.rows.map { it.symbol() }
            symbols.forEach { symbol -> rowCache.update { it + (symbol to RowState.Loading(symbol)) } }
            coroutineScope {
                symbols.map { symbol -> async { symbol to fetchRow(symbol) } }
                    .awaitAll()
                    .forEach { (symbol, state) -> rowCache.update { it + (symbol to state) } }
            }
            refreshing.value = false
        }
    }

    private fun loadRow(symbol: String) {
        rowCache.update { it + (symbol to RowState.Loading(symbol)) }
        viewModelScope.launch {
            val newState = fetchRow(symbol)
            rowCache.update { it + (symbol to newState) }
        }
    }

    // A quick quote (one request) rather than the full report (target + up to 5
    // peers + news + recommendations) — the watchlist just needs price and a
    // score, and fetching the full report per row would multiply into dozens of
    // Yahoo requests as soon as more than one or two tickers are watched,
    // tripping their rate limiting.
    private suspend fun fetchRow(symbol: String): RowState {
        return repository.getQuickQuote(symbol).fold(
            onSuccess = { quote -> quote.toLoadedRow(symbol) },
            onFailure = { error -> RowState.Failed(symbol, error.message ?: "Failed to load") }
        )
    }

    private fun QuickQuote.toLoadedRow(symbol: String) = RowState.Loaded(
        symbol = symbol,
        name = snapshot.name,
        price = snapshot.price,
        changePercent = snapshot.changePercent,
        currency = snapshot.currency,
        scoreTotal = score.total,
        tier = score.tier
    )

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
