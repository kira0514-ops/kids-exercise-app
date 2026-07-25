package com.kira.stockscope.ui.watchlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kira.stockscope.R
import com.kira.stockscope.ui.theme.BearRed
import com.kira.stockscope.ui.theme.BullGreen
import com.kira.stockscope.ui.theme.NeutralAmber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchlistScreen(
    viewModel: WatchlistViewModel,
    onOpenSymbol: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var query by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("StockScope") },
                actions = {
                    IconButton(onClick = { viewModel.refreshAll() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it.uppercase() },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("Add ticker (e.g. AAPL)") }
                )
                Spacer(Modifier.size(8.dp))
                IconButton(onClick = {
                    if (query.isNotBlank()) {
                        viewModel.addSymbol(query)
                        query = ""
                    }
                }) {
                    Icon(Icons.Filled.Add, contentDescription = "Add")
                }
            }

            if (state.rows.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Add a ticker to build your watchlist.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.rows, key = { rowKey(it) }) { row ->
                        WatchlistRowCard(
                            row = row,
                            onClick = { onOpenSymbol(rowKey(row)) },
                            onRemove = { viewModel.removeSymbol(rowKey(row)) }
                        )
                    }
                }
            }

            Text(
                stringResource(R.string.disclaimer),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(12.dp)
            )
        }
    }
}

private fun rowKey(row: RowState): String = when (row) {
    is RowState.Loading -> row.symbol
    is RowState.Loaded -> row.symbol
    is RowState.Failed -> row.symbol
}

@Composable
private fun WatchlistRowCard(row: RowState, onClick: () -> Unit, onRemove: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                when (row) {
                    is RowState.Loading -> {
                        Text(row.symbol, fontWeight = FontWeight.Bold)
                        Text("Loading…", style = MaterialTheme.typography.bodySmall)
                    }
                    is RowState.Failed -> {
                        Text(row.symbol, fontWeight = FontWeight.Bold)
                        Text(row.message, style = MaterialTheme.typography.bodySmall, color = BearRed)
                    }
                    is RowState.Loaded -> {
                        Text("${row.symbol} · ${row.name}", fontWeight = FontWeight.Bold)
                        val priceText = row.price?.let { "%.2f".format(it) } ?: "--"
                        val changeText = row.changePercent?.let {
                            (if (it >= 0) "+" else "") + "%.2f".format(it) + "%"
                        } ?: ""
                        val changeColor = when {
                            row.changePercent == null -> MaterialTheme.colorScheme.onSurfaceVariant
                            row.changePercent >= 0 -> BullGreen
                            else -> BearRed
                        }
                        Row {
                            Text("${row.currency ?: ""} $priceText", style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.size(8.dp))
                            Text(changeText, style = MaterialTheme.typography.bodySmall, color = changeColor)
                        }
                    }
                }
            }
            if (row is RowState.Loading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
            if (row is RowState.Loaded) {
                ScorePill(total = row.scoreTotal)
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Close, contentDescription = "Remove")
            }
        }
    }
}

@Composable
private fun ScorePill(total: Int) {
    val color = when {
        total >= 80 -> BullGreen
        total >= 60 -> BullGreen.copy(alpha = 0.8f)
        total >= 40 -> NeutralAmber
        else -> BearRed
    }
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$total", color = color, fontWeight = FontWeight.Bold)
        }
    }
}
