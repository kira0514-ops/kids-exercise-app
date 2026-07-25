package com.kira.stockscope.data.repository

import com.kira.stockscope.data.network.CrumbManager
import com.kira.stockscope.data.network.YahooFinanceApi
import com.kira.stockscope.data.network.YahooFinanceApi.Companion.CHART_URL
import com.kira.stockscope.data.network.YahooFinanceApi.Companion.QUOTE_SUMMARY_MODULES
import com.kira.stockscope.data.network.YahooFinanceApi.Companion.QUOTE_SUMMARY_URL
import com.kira.stockscope.data.network.YahooFinanceApi.Companion.RECOMMENDATIONS_URL
import com.kira.stockscope.data.network.YahooFinanceApi.Companion.SEARCH_URL
import com.kira.stockscope.data.network.dto.QuoteSummaryResult
import com.kira.stockscope.data.network.dto.RawFmt
import com.kira.stockscope.domain.ScoringEngine
import com.kira.stockscope.domain.TechnicalAnalysisEngine
import com.kira.stockscope.model.AnalystView
import com.kira.stockscope.model.Candle
import com.kira.stockscope.model.Fundamentals
import com.kira.stockscope.model.NewsItem
import com.kira.stockscope.model.PeerRow
import com.kira.stockscope.model.QuickQuote
import com.kira.stockscope.model.SectorComparison
import com.kira.stockscope.model.StockReport
import com.kira.stockscope.model.StockSnapshot
import com.kira.stockscope.model.TechnicalReading
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

class StockRepository(
    private val api: YahooFinanceApi,
    private val crumbManager: CrumbManager
) {

    /**
     * The full 5-pass report: target quote + up to 5 peer quotes + news + a
     * recommendations lookup. Reserved for the detail screen; the watchlist uses
     * [getQuickQuote] instead so adding several tickers doesn't fan out into
     * dozens of Yahoo requests and trip their rate limiting.
     */
    suspend fun getReport(symbol: String): Result<StockReport> = withContext(Dispatchers.IO) {
        runCatching {
            val ticker = symbol.trim().uppercase()
            val crumb = runCatching { crumbManager.getCrumb() }.getOrNull()
            val target = fetchTarget(ticker, crumb)
            val snapshot = target.snapshot
            val fundamentals = target.fundamentals

            val peerSymbols = resolvePeerSymbols(ticker, snapshot.sector, crumb)
            val peerRows = coroutineScope {
                peerSymbols.map { peerSymbol ->
                    async { runCatching { fetchPeerRow(peerSymbol, crumb) }.getOrNull() }
                }.awaitAll()
            }.filterNotNull()

            val targetRow = PeerRow(
                symbol = ticker,
                name = snapshot.name,
                price = snapshot.price,
                marketCap = snapshot.marketCap,
                trailingPe = fundamentals.trailingPe,
                forwardPe = fundamentals.forwardPe,
                profitMargin = fundamentals.profitMargin,
                revenueGrowth = fundamentals.revenueGrowth,
                isTarget = true
            )

            val sector = buildSectorComparison(ticker, targetRow, peerRows)

            val narrative = runCatching { api.search(SEARCH_URL, ticker, newsCount = 8) }.getOrNull()
                ?.news.orEmpty()
                .mapNotNull { n ->
                    val title = n.title ?: return@mapNotNull null
                    NewsItem(
                        title = title,
                        publisher = n.publisher,
                        link = n.link,
                        publishedAtEpochSeconds = n.providerPublishTime
                    )
                }

            val score = ScoringEngine.score(fundamentals, sector, snapshot)
            val asymmetry = ScoringEngine.asymmetryTargets(snapshot, fundamentals, sector)
            val analystView = AnalystView(
                recommendationKey = target.raw?.financialData?.recommendationKey,
                targetMean = target.raw?.financialData?.targetMeanPrice.raw(),
                targetHigh = target.raw?.financialData?.targetHighPrice.raw(),
                targetLow = target.raw?.financialData?.targetLowPrice.raw()
            )
            val technicals = fetchTechnicals(ticker)

            StockReport(
                snapshot = snapshot,
                fundamentals = fundamentals,
                sector = sector,
                asymmetry = asymmetry,
                score = score,
                narrative = narrative,
                analystView = analystView,
                businessSummary = target.raw?.assetProfile?.longBusinessSummary,
                technicals = technicals
            )
        }
    }

    /**
     * One network call (two if the price needs the chart fallback): target
     * quote only, no peers/news/recommendations. The conviction score it
     * returns is real but its valuation subscore falls back to neutral since
     * there's no peer P/E to compare against.
     */
    suspend fun getQuickQuote(symbol: String): Result<QuickQuote> = withContext(Dispatchers.IO) {
        runCatching {
            val ticker = symbol.trim().uppercase()
            val crumb = runCatching { crumbManager.getCrumb() }.getOrNull()
            val target = fetchTarget(ticker, crumb)

            val noPeerData = SectorComparison(
                peers = emptyList(),
                targetSymbol = ticker,
                peerAverageForwardPe = null,
                peerAverageTrailingPe = null,
                peerLowForwardPe = null,
                peerHighForwardPe = null,
                targetRankByForwardPe = null,
                totalRanked = 0
            )
            val score = ScoringEngine.score(target.fundamentals, noPeerData, target.snapshot)
            QuickQuote(target.snapshot, score)
        }
    }

    private data class TargetFetch(
        val snapshot: StockSnapshot,
        val fundamentals: Fundamentals,
        /** Null when quoteSummary itself failed but the chart endpoint still resolved a price. */
        val raw: QuoteSummaryResult?
    )

    /**
     * quoteSummary needs the crumb and is the one endpoint Yahoo is most likely to
     * block; the chart endpoint historically doesn't. So quoteSummary failing
     * entirely — not just a field inside it being missing — no longer fails the
     * whole fetch: it falls back to a bare snapshot built from chart data (price,
     * name) with empty fundamentals, and only genuinely errors if *neither*
     * endpoint could resolve the ticker at all.
     */
    private suspend fun fetchTarget(ticker: String, crumb: String?): TargetFetch {
        val quoteSummaryResult = runCatching {
            api.getQuoteSummary(QUOTE_SUMMARY_URL + ticker, QUOTE_SUMMARY_MODULES, crumb)
                .quoteSummary.result?.firstOrNull()
        }.getOrNull()

        var snapshot = quoteSummaryResult?.let { mapSnapshot(ticker, it) }
        val fundamentals = quoteSummaryResult?.let { mapFundamentals(it) } ?: Fundamentals.EMPTY

        if (snapshot?.price == null) {
            val chartMeta = runCatching { api.getChart(CHART_URL + ticker) }.getOrNull()
                ?.chart?.result?.firstOrNull()?.meta
            if (chartMeta != null) {
                val fallbackName = chartMeta.longName ?: chartMeta.shortName ?: ticker
                val fallbackPrice = chartMeta.regularMarketPrice ?: chartMeta.previousClose
                snapshot = snapshot?.copy(
                    price = snapshot.price ?: fallbackPrice,
                    name = snapshot.name.ifBlank { fallbackName }
                ) ?: StockSnapshot(
                    symbol = ticker,
                    name = fallbackName,
                    sector = null,
                    industry = null,
                    currency = chartMeta.currency,
                    price = fallbackPrice,
                    changePercent = null,
                    marketCap = null
                )
            }
        }

        val finalSnapshot = snapshot
            ?: error("Couldn't reach Yahoo Finance for \"$ticker\" — check your connection and try again.")

        return TargetFetch(finalSnapshot, fundamentals, quoteSummaryResult)
    }

    /**
     * A year of daily OHLC bars, used only to compute RSI/MACD/ADX/Stochastic/
     * moving averages and the most recent swing high/low locally — Yahoo doesn't
     * expose these as precomputed fields, so [TechnicalAnalysisEngine] derives
     * them from raw candles. Best-effort: null on any failure or on too little
     * history (e.g. a recent IPO), same as every other optional part of the report.
     */
    private suspend fun fetchTechnicals(ticker: String): TechnicalReading? {
        val result = runCatching { api.getChart(CHART_URL + ticker, range = "1y") }.getOrNull()
            ?.chart?.result?.firstOrNull() ?: return null
        val timestamps = result.timestamp ?: return null
        val quote = result.indicators?.quote?.firstOrNull() ?: return null
        val opens = quote.open ?: return null
        val highs = quote.high ?: return null
        val lows = quote.low ?: return null
        val closes = quote.close ?: return null

        val candles = timestamps.indices.mapNotNull { i ->
            val open = opens.getOrNull(i)
            val high = highs.getOrNull(i)
            val low = lows.getOrNull(i)
            val close = closes.getOrNull(i)
            if (open == null || high == null || low == null || close == null) {
                null
            } else {
                Candle(
                    timestamp = timestamps[i],
                    open = open,
                    high = high,
                    low = low,
                    close = close,
                    volume = quote.volume?.getOrNull(i)
                )
            }
        }

        return TechnicalAnalysisEngine.analyze(candles)
    }

    private suspend fun resolvePeerSymbols(ticker: String, sector: String?, crumb: String?): List<String> {
        val recommended = runCatching {
            api.recommendationsBySymbol(RECOMMENDATIONS_URL + ticker)
                .finance.result?.firstOrNull()?.recommendedSymbols?.mapNotNull { it.symbol }
        }.getOrNull().orEmpty()

        val symbols = recommended.ifEmpty { SectorPeerFallback.peersFor(sector, ticker) }
        return symbols.filter { !it.equals(ticker, ignoreCase = true) }.distinct().take(5)
    }

    private suspend fun fetchPeerRow(symbol: String, crumb: String?): PeerRow {
        val result = api.getQuoteSummary(QUOTE_SUMMARY_URL + symbol, QUOTE_SUMMARY_MODULES, crumb)
            .quoteSummary.result?.firstOrNull() ?: error("no data")
        val snapshot = mapSnapshot(symbol, result)
        val fundamentals = mapFundamentals(result)
        return PeerRow(
            symbol = symbol,
            name = snapshot.name,
            price = snapshot.price,
            marketCap = snapshot.marketCap,
            trailingPe = fundamentals.trailingPe,
            forwardPe = fundamentals.forwardPe,
            profitMargin = fundamentals.profitMargin,
            revenueGrowth = fundamentals.revenueGrowth
        )
    }

    private fun buildSectorComparison(ticker: String, target: PeerRow, peers: List<PeerRow>): SectorComparison {
        val all = (peers + target)
        val ranked = all.sortedWith(compareBy(nullsLast<Double>()) { it.forwardPe ?: it.trailingPe })
        val targetRank = ranked.indexOfFirst { it.symbol.equals(ticker, ignoreCase = true) }
            .let { if (it >= 0) it + 1 else null }

        val forwardPes = peers.mapNotNull { it.forwardPe }
        val trailingPes = peers.mapNotNull { it.trailingPe }

        return SectorComparison(
            peers = ranked,
            targetSymbol = ticker,
            peerAverageForwardPe = forwardPes.takeIf { it.isNotEmpty() }?.average(),
            peerAverageTrailingPe = trailingPes.takeIf { it.isNotEmpty() }?.average(),
            peerLowForwardPe = forwardPes.minOrNull(),
            peerHighForwardPe = forwardPes.maxOrNull(),
            targetRankByForwardPe = targetRank,
            totalRanked = ranked.size
        )
    }

    private fun mapSnapshot(symbol: String, result: QuoteSummaryResult): StockSnapshot {
        val price = result.price
        return StockSnapshot(
            symbol = symbol,
            name = price?.longName ?: price?.shortName ?: symbol,
            sector = result.assetProfile?.sector,
            industry = result.assetProfile?.industry,
            currency = price?.currency,
            price = price?.regularMarketPrice.raw(),
            changePercent = price?.regularMarketChangePercent.raw(),
            marketCap = price?.marketCap.raw()
        )
    }

    private fun mapFundamentals(result: QuoteSummaryResult): Fundamentals {
        val summary = result.summaryDetail
        val stats = result.defaultKeyStatistics
        val financials = result.financialData
        val nextYearGrowth = result.earningsTrend?.trend
            ?.firstOrNull { it.period == "+1y" }
            ?.growth.raw()

        return Fundamentals(
            trailingPe = summary?.trailingPE.raw(),
            forwardPe = summary?.forwardPE.raw(),
            trailingEps = stats?.trailingEps.raw(),
            forwardEps = stats?.forwardEps.raw(),
            revenueGrowth = financials?.revenueGrowth.raw(),
            earningsGrowth = financials?.earningsGrowth.raw(),
            nextYearGrowthEstimate = nextYearGrowth,
            grossMargin = financials?.grossMargins.raw(),
            operatingMargin = financials?.operatingMargins.raw(),
            profitMargin = financials?.profitMargins.raw(),
            totalCash = financials?.totalCash.raw(),
            totalDebt = financials?.totalDebt.raw(),
            freeCashFlow = financials?.freeCashflow.raw(),
            dividendYield = summary?.dividendYield.raw(),
            beta = summary?.beta.raw(),
            debtToEquity = financials?.debtToEquity.raw(),
            fiftyTwoWeekLow = summary?.fiftyTwoWeekLow.raw(),
            fiftyTwoWeekHigh = summary?.fiftyTwoWeekHigh.raw(),
            fiftyDayAverage = summary?.fiftyDayAverage.raw(),
            twoHundredDayAverage = summary?.twoHundredDayAverage.raw()
        )
    }

    private fun RawFmt?.raw(): Double? = this?.raw
}
