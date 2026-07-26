package com.kira.stockscope.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kira.stockscope.data.repository.StockRepository
import com.kira.stockscope.model.StockReport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface DetailUiState {
    data object Loading : DetailUiState
    data class Loaded(val report: StockReport) : DetailUiState
    data class Failed(val message: String) : DetailUiState
}

class StockDetailViewModel(
    private val repository: StockRepository,
    private val symbol: String
) : ViewModel() {

    private val _state = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = DetailUiState.Loading
        viewModelScope.launch {
            val result = repository.getReport(symbol)
            _state.value = result.fold(
                onSuccess = { DetailUiState.Loaded(it) },
                onFailure = { DetailUiState.Failed(it.message ?: "Something went wrong") }
            )
        }
    }

    class Factory(
        private val repository: StockRepository,
        private val symbol: String
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return StockDetailViewModel(repository, symbol) as T
        }
    }
}
