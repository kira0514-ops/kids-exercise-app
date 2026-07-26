package com.kira.stockscope.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kira.stockscope.model.WatchlistEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.watchlistDataStore by preferencesDataStore(name = "stockscope_watchlist")

/** Persists the user's watched tickers as "SYMBOL:addedAtEpochMillis" strings. */
class WatchlistStore(private val context: Context) {

    private val symbolsKey = stringSetPreferencesKey("watchlist_entries")

    val entries: Flow<List<WatchlistEntry>> = context.watchlistDataStore.data.map { prefs ->
        prefs[symbolsKey].orEmpty()
            .mapNotNull { raw ->
                val parts = raw.split(":")
                val symbol = parts.getOrNull(0) ?: return@mapNotNull null
                val addedAt = parts.getOrNull(1)?.toLongOrNull() ?: 0L
                WatchlistEntry(symbol, addedAt)
            }
            .sortedByDescending { it.addedAtEpochMillis }
    }

    suspend fun add(symbol: String) {
        val ticker = symbol.trim().uppercase()
        if (ticker.isBlank()) return
        context.watchlistDataStore.edit { prefs ->
            val current = prefs[symbolsKey].orEmpty().toMutableSet()
            current.removeAll { it.startsWith("$ticker:") }
            current.add("$ticker:${System.currentTimeMillis()}")
            prefs[symbolsKey] = current
        }
    }

    suspend fun remove(symbol: String) {
        val ticker = symbol.trim().uppercase()
        context.watchlistDataStore.edit { prefs ->
            val current = prefs[symbolsKey].orEmpty().toMutableSet()
            current.removeAll { it.startsWith("$ticker:") }
            prefs[symbolsKey] = current
        }
    }
}
