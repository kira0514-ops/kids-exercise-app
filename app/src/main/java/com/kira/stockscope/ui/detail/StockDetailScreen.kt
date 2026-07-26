package com.kira.stockscope.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kira.stockscope.R
import com.kira.stockscope.model.AnalystView
import com.kira.stockscope.model.AsymmetryTargets
import com.kira.stockscope.model.ConfirmationStatus
import com.kira.stockscope.model.ConvictionScore
import com.kira.stockscope.model.Fundamentals
import com.kira.stockscope.model.MacdReading
import com.kira.stockscope.model.NewsItem
import com.kira.stockscope.model.SectorComparison
import com.kira.stockscope.model.StockReport
import com.kira.stockscope.model.SwingSeries
import com.kira.stockscope.model.TechnicalReading
import com.kira.stockscope.model.TrendConfirmation
import com.kira.stockscope.model.TrendDirection
import com.kira.stockscope.ui.common.Formatters
import com.kira.stockscope.ui.theme.BearRed
import com.kira.stockscope.ui.theme.BullGreen
import com.kira.stockscope.ui.theme.NeutralAmber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockDetailScreen(
    viewModel: StockDetailViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state is DetailUiState.Loaded) (state as DetailUiState.Loaded).report.snapshot.symbol else "StockScope") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (val s = state) {
                is DetailUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                is DetailUiState.Failed -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(s.message, color = BearRed)
                        Spacer(Modifier.size(12.dp))
                        Button(onClick = { viewModel.load() }) { Text("Retry") }
                    }
                }
                is DetailUiState.Loaded -> ReportBody(s.report)
            }
        }
    }
}

@Composable
private fun ReportBody(report: StockReport) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        HeaderSection(report)
        ChartSection(report.snapshot.symbol)
        TechnicalsSection(report.technicals, report.snapshot.price)
        ConvictionSection(report.score)
        FundamentalsSection(report.fundamentals)
        SectorSection(report.sector)
        AsymmetrySection(report.asymmetry)
        NarrativeSection(report.narrative, report.businessSummary, report.analystView)
        Text(
            stringResource(R.string.disclaimer),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.size(8.dp))
            content()
        }
    }
}

@Composable
private fun HeaderSection(report: StockReport) {
    val snapshot = report.snapshot
    Column {
        Text("${snapshot.symbol} · ${snapshot.name}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        listOfNotNull(snapshot.sector, snapshot.industry).let {
            if (it.isNotEmpty()) {
                Text(it.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.size(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(Formatters.price(snapshot.price, snapshot.currency), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.size(12.dp))
            val changeColor = when {
                snapshot.changePercent == null -> MaterialTheme.colorScheme.onSurfaceVariant
                snapshot.changePercent >= 0 -> BullGreen
                else -> BearRed
            }
            Text(Formatters.percent(snapshot.changePercent, alreadyPercentScale = true), color = changeColor, fontWeight = FontWeight.Bold)
        }
        Text("Market cap ${Formatters.compactNumber(snapshot.marketCap)}", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ChartSection(symbol: String) {
    // The chart WebView loads only on request rather than automatically: if it's
    // ever the thing going wrong on a given device, the rest of the report (which
    // has nothing to do with WebView) stays usable regardless, instead of the
    // whole screen riding on whether an embedded browser engine behaves.
    var showChart by remember(symbol) { mutableStateOf(false) }
    SectionCard(title = "Chart") {
        if (showChart) {
            TradingViewChartCard(symbol = symbol, darkTheme = isSystemInDarkTheme())
        } else {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Chart loads on request.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.size(10.dp))
                Button(onClick = { showChart = true }) { Text("Load chart") }
            }
        }
    }
}

@Composable
private fun TechnicalsSection(technicals: TechnicalReading?, currentPrice: Double?) {
    SectionCard(title = "Technicals") {
        if (technicals == null) {
            Text(
                "Not enough price history to compute indicators yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            TrendConfirmationBanner(technicals.trendConfirmation)
            Spacer(Modifier.size(12.dp))
            HorizontalDivider()
            Spacer(Modifier.size(8.dp))

            Text("Momentum", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            RsiRow(technicals.rsi14)
            StochasticRow(technicals.stochK, technicals.stochD)

            Spacer(Modifier.size(10.dp))
            HorizontalDivider()
            Spacer(Modifier.size(8.dp))

            Text("Trend", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            MacdRow(technicals.macd)
            AdxRow(technicals.adx14, technicals.plusDi14, technicals.minusDi14)

            Spacer(Modifier.size(10.dp))
            HorizontalDivider()
            Spacer(Modifier.size(8.dp))

            Text("Moving averages", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            MovingAverageRow("SMA 20", technicals.sma20, currentPrice)
            MovingAverageRow("SMA 50", technicals.sma50, currentPrice)
            MovingAverageRow("SMA 200", technicals.sma200, currentPrice)

            Spacer(Modifier.size(10.dp))
            HorizontalDivider()
            Spacer(Modifier.size(8.dp))

            Text("Swing structure", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            SwingSeriesRow("Daily (minor, 3-bar)", technicals.dailySwings, unit = "d")
            SwingSeriesRow("Daily (major, 8-bar)", technicals.majorDailySwings, unit = "d")
            SwingSeriesRow("Weekly", technicals.weeklySwings, unit = "w")
        }
    }
}

@Composable
private fun TrendConfirmationBanner(confirmation: TrendConfirmation) {
    val (label, color) = when (confirmation.status) {
        ConfirmationStatus.CONFIRMED_UP -> "Uptrend confirmed" to BullGreen
        ConfirmationStatus.CONFIRMED_DOWN -> "Downtrend confirmed" to BearRed
        ConfirmationStatus.MIXED -> "Mixed signals" to NeutralAmber
        ConfirmationStatus.INSUFFICIENT_DATA -> "Not enough data to confirm a trend" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    val indicatorText = if (confirmation.totalSignals > 0) {
        "${confirmation.bullishSignals}/${confirmation.totalSignals} bullish"
    } else {
        "not enough data"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Text(label, color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.size(4.dp))
        Text(
            "Swing structure: ${trendDirectionLabel(confirmation.swingVerdict)} · Indicators: $indicatorText",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun trendDirectionLabel(direction: TrendDirection): String = when (direction) {
    TrendDirection.UP -> "higher highs & higher lows"
    TrendDirection.DOWN -> "lower highs & lower lows"
    TrendDirection.MIXED -> "mixed"
    TrendDirection.UNKNOWN -> "not enough data"
}

@Composable
private fun SwingSeriesRow(label: String, series: SwingSeries, unit: String) {
    val latestHigh = series.highs.firstOrNull()
    val latestLow = series.lows.firstOrNull()
    if (latestHigh == null && latestLow == null) {
        KeyValueRow(label, "Not enough data")
        return
    }
    val (trendLabel, trendColor) = when (series.structureTrend) {
        TrendDirection.UP -> "Higher highs/lows" to BullGreen
        TrendDirection.DOWN -> "Lower highs/lows" to BearRed
        TrendDirection.MIXED -> "Mixed" to NeutralAmber
        TrendDirection.UNKNOWN -> "--" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(trendLabel, style = MaterialTheme.typography.bodySmall, color = trendColor, fontWeight = FontWeight.Bold)
        }
        Text(
            latestHigh?.let { "High ${Formatters.price(it.price)} (${it.barsAgo}$unit ago)" } ?: "High --",
            style = MaterialTheme.typography.labelSmall,
            color = BullGreen
        )
        Text(
            latestLow?.let { "Low ${Formatters.price(it.price)} (${it.barsAgo}$unit ago)" } ?: "Low --",
            style = MaterialTheme.typography.labelSmall,
            color = BearRed
        )
    }
}

private fun rsiZone(rsi: Double): Pair<String, androidx.compose.ui.graphics.Color> = when {
    rsi >= 70 -> "Overbought" to BearRed
    rsi <= 30 -> "Oversold" to BullGreen
    else -> "Neutral" to NeutralAmber
}

@Composable
private fun RsiRow(rsi: Double?) {
    if (rsi == null) {
        KeyValueRow("RSI (14)", "--")
        return
    }
    val (label, color) = rsiZone(rsi)
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("RSI (14)", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row {
                Text("%.1f".format(rsi), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Spacer(Modifier.size(6.dp))
                Text(label, style = MaterialTheme.typography.bodySmall, color = color, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.size(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(3.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = (rsi / 100.0).toFloat().coerceIn(0f, 1f))
                    .height(6.dp)
                    .background(color, RoundedCornerShape(3.dp))
            )
        }
    }
}

@Composable
private fun StochasticRow(k: Double?, d: Double?) {
    if (k == null || d == null) {
        KeyValueRow("Stochastic (14,3,3)", "--")
        return
    }
    val (label, color) = when {
        k >= 80 -> "Overbought" to BearRed
        k <= 20 -> "Oversold" to BullGreen
        else -> "Neutral" to NeutralAmber
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("Stochastic (14,3,3)", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row {
            Text("%K %.1f / %D %.1f".format(k, d), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Spacer(Modifier.size(6.dp))
            Text(label, style = MaterialTheme.typography.bodySmall, color = color, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun MacdRow(macd: MacdReading?) {
    if (macd == null) {
        KeyValueRow("MACD (12,26,9)", "--")
        return
    }
    val bullish = macd.histogram > 0
    val label = if (bullish) "Bullish" else "Bearish"
    val color = if (bullish) BullGreen else BearRed
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("MACD (12,26,9)", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(label, style = MaterialTheme.typography.bodySmall, color = color, fontWeight = FontWeight.Bold)
        }
        Text(
            "Line %.2f · Signal %.2f · Hist %.2f".format(macd.macd, macd.signal, macd.histogram),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AdxRow(adx: Double?, plusDi: Double?, minusDi: Double?) {
    if (adx == null) {
        KeyValueRow("ADX (14)", "--")
        return
    }
    val strength = when {
        adx >= 25 -> "Strong trend"
        adx >= 20 -> "Developing trend"
        else -> "Weak / range-bound"
    }
    val plusLeads = plusDi != null && minusDi != null && plusDi >= minusDi
    val directionColor = if (plusLeads) BullGreen else BearRed

    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("ADX (14)", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("%.1f · $strength".format(adx), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
        }
        if (plusDi != null && minusDi != null) {
            Text(
                "${if (plusLeads) "+DI leading" else "-DI leading"} (+DI %.1f / -DI %.1f)".format(plusDi, minusDi),
                style = MaterialTheme.typography.labelSmall,
                color = directionColor
            )
        }
    }
}

@Composable
private fun MovingAverageRow(label: String, average: Double?, currentPrice: Double?) {
    if (average == null) {
        KeyValueRow(label, "--")
        return
    }
    val above = currentPrice?.let { it >= average }
    val color = when (above) {
        true -> BullGreen
        false -> BearRed
        null -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row {
            Text(Formatters.price(average), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            if (above != null) {
                Spacer(Modifier.size(6.dp))
                Text(if (above) "Price above" else "Price below", style = MaterialTheme.typography.labelSmall, color = color)
            }
        }
    }
}

@Composable
private fun ConvictionSection(score: ConvictionScore) {
    SectionCard(title = "Conviction score") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${score.total}", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Text("/100", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.size(12.dp))
            TierBadge(score.total, score.tier)
        }
        Spacer(Modifier.size(12.dp))
        ScoreBar("Valuation", score.valuationScore)
        ScoreBar("Growth", score.growthScore)
        ScoreBar("Quality / margins", score.qualityScore)
        ScoreBar("Balance sheet", score.balanceSheetScore)
    }
}

@Composable
private fun TierBadge(total: Int, tier: String) {
    val color = tierColor(total)
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(tier, color = color, fontWeight = FontWeight.Bold)
    }
}

private fun tierColor(total: Int) = when {
    total >= 60 -> BullGreen
    total >= 40 -> NeutralAmber
    else -> BearRed
}

@Composable
private fun ScoreBar(label: String, value: Int, max: Int = 25) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Text("$value / $max", style = MaterialTheme.typography.bodySmall)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(3.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = (value.toFloat() / max).coerceIn(0f, 1f))
                    .height(6.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp))
            )
        }
    }
}

@Composable
private fun FundamentalsSection(f: Fundamentals) {
    SectionCard(title = "Fundamentals") {
        val rows = listOf(
            "Trailing P/E" to Formatters.ratio(f.trailingPe),
            "Forward P/E" to Formatters.ratio(f.forwardPe),
            "Trailing EPS" to Formatters.price(f.trailingEps),
            "Forward EPS" to Formatters.price(f.forwardEps),
            "Revenue growth (YoY)" to Formatters.percent(f.revenueGrowth),
            "Next-year growth est." to Formatters.percent(f.nextYearGrowthEstimate),
            "Gross margin" to Formatters.percent(f.grossMargin),
            "Operating margin" to Formatters.percent(f.operatingMargin),
            "Profit margin" to Formatters.percent(f.profitMargin),
            "Total cash" to Formatters.compactNumber(f.totalCash),
            "Total debt" to Formatters.compactNumber(f.totalDebt),
            "Net cash position" to Formatters.compactNumber(f.netCash),
            "Free cash flow" to Formatters.compactNumber(f.freeCashFlow),
            "Dividend yield" to Formatters.percent(f.dividendYield),
            "Beta" to (f.beta?.let { "%.2f".format(it) } ?: "--"),
            "52-week range" to "${Formatters.price(f.fiftyTwoWeekLow)} – ${Formatters.price(f.fiftyTwoWeekHigh)}",
            "50D / 200D avg" to "${Formatters.price(f.fiftyDayAverage)} / ${Formatters.price(f.twoHundredDayAverage)}"
        )
        rows.forEach { (label, value) -> KeyValueRow(label, value) }
    }
}

@Composable
private fun KeyValueRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SectorSection(sector: SectorComparison) {
    SectionCard(title = "Sector comparison") {
        val rankText = sector.targetRankByForwardPe?.let { rank ->
            "Ranked #$rank of ${sector.totalRanked} by forward P/E (cheapest first)"
        } ?: "Not enough peer data to rank"
        Text(rankText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.size(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            HeaderCell("Ticker", 1.4f)
            HeaderCell("Fwd P/E", 1f)
            HeaderCell("Margin", 1f)
            HeaderCell("Growth", 1f)
        }
        HorizontalDivider()
        sector.peers.forEach { peer ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (peer.isTarget) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else androidx.compose.ui.graphics.Color.Transparent)
                    .padding(vertical = 6.dp)
            ) {
                DataCell(peer.symbol, 1.4f, peer.isTarget)
                DataCell(Formatters.ratio(peer.forwardPe), 1f, peer.isTarget)
                DataCell(Formatters.percent(peer.profitMargin), 1f, peer.isTarget)
                DataCell(Formatters.percent(peer.revenueGrowth), 1f, peer.isTarget)
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.HeaderCell(text: String, weight: Float) {
    Text(
        text,
        modifier = Modifier.weight(weight),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.DataCell(text: String, weight: Float, emphasize: Boolean) {
    Text(
        text,
        modifier = Modifier.weight(weight),
        style = MaterialTheme.typography.bodySmall,
        fontWeight = if (emphasize) FontWeight.Bold else FontWeight.Normal
    )
}

@Composable
private fun AsymmetrySection(a: AsymmetryTargets) {
    SectionCard(title = "Asymmetry") {
        Text(
            "Scenario targets = anchor EPS × peer P/E multiple. Heuristic, not a forecast.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.size(8.dp))
        TargetRow("Bear", a.bear, a.currentPrice, BearRed)
        TargetRow("Base", a.base, a.currentPrice, MaterialTheme.colorScheme.onSurface)
        TargetRow("Bull", a.bull, a.currentPrice, BullGreen)
        TargetRow("Stretched bull", a.stretchedBull, a.currentPrice, BullGreen)
        Spacer(Modifier.size(12.dp))
        HorizontalDivider()
        Spacer(Modifier.size(8.dp))
        KeyValueRow("Entry zone", Formatters.price(a.entryZone))
        KeyValueRow("Trim zone", Formatters.price(a.trimZone))
        KeyValueRow("Thesis-break level", Formatters.price(a.thesisBreak))
    }
}

@Composable
private fun TargetRow(label: String, target: Double?, current: Double?, color: androidx.compose.ui.graphics.Color) {
    val upside = if (target != null && current != null && current != 0.0) (target / current - 1) * 100 else null
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(Formatters.price(target), fontWeight = FontWeight.Bold, color = color)
            if (upside != null) {
                Spacer(Modifier.size(6.dp))
                Text("(${Formatters.percent(upside, alreadyPercentScale = true)})", style = MaterialTheme.typography.bodySmall, color = color)
            }
        }
    }
}

@Composable
private fun AnalystViewRow(analystView: AnalystView) {
    Column {
        analystView.recommendationLabel?.let { label ->
            KeyValueRow("Analyst consensus", label)
        }
        if (analystView.targetLow != null || analystView.targetMean != null || analystView.targetHigh != null) {
            KeyValueRow(
                "Analyst price target (low / mean / high)",
                "${Formatters.price(analystView.targetLow)} / ${Formatters.price(analystView.targetMean)} / ${Formatters.price(analystView.targetHigh)}"
            )
        }
    }
}

@Composable
private fun NarrativeSection(news: List<NewsItem>, businessSummary: String?, analystView: AnalystView) {
    SectionCard(title = "Narrative") {
        if (analystView.hasData) {
            AnalystViewRow(analystView)
            Spacer(Modifier.size(12.dp))
            HorizontalDivider()
            Spacer(Modifier.size(8.dp))
        }
        if (!businessSummary.isNullOrBlank()) {
            Text(businessSummary, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.size(12.dp))
            HorizontalDivider()
            Spacer(Modifier.size(8.dp))
        }
        if (news.isEmpty()) {
            Text("No recent headlines found.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            val uriHandler = LocalUriHandler.current
            news.forEach { item ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = item.link != null) { item.link?.let { uriHandler.openUri(it) } }
                        .padding(vertical = 6.dp)
                ) {
                    Text(item.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    val meta = listOfNotNull(item.publisher, Formatters.timeAgo(item.publishedAtEpochSeconds)).joinToString(" · ")
                    if (meta.isNotBlank()) {
                        Text(meta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
