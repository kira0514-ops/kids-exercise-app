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
import com.kira.stockscope.model.Fundamentals
import com.kira.stockscope.model.NewsItem
import com.kira.stockscope.model.PeerRow
import com.kira.stockscope.model.SectorComparison
import com.kira.stockscope.model.StockReport
import com.kira.stockscope.model.StockSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

class StockRepository(
    private val api: YahooFinanceApi,
    private val crumbManager: CrumbManager
) {

    suspend fun getReport(symbol: String): Result<StockReport> = withContext(Dispatchers.IO) {
        runCatching {
            val ticker = symbol.trim().uppercase()
            val crumb = runCatching { crumbManager.getCrumb() }.getOrNull()

            val targetResult = api.getQuoteSummary(QUOTE_SUMMARY_URL + ticker, QUOTE_SUMMARY_MODULES, crumb)
                .quoteSummary.result?.firstOrNull()
                ?: error("No data returned for \"$ticker\". Check the ticker symbol.")

            var snapshot = mapSnapshot(ticker, targetResult)
            val fundamentals = mapFundamentals(targetResult)

            if (snapshot.price == null) {
                val chartMeta = runCatching { api.getChart(CHART_URL + ticker) }.getOrNull()
                    ?.chart?.result?.firstOrNull()?.meta
                if (chartMeta != null) {
                    snapshot = snapshot.copy(
                        price = chartMeta.regularMarketPrice ?: chartMeta.previousClose,
                        name = snapshot.name.ifBlank { chartMeta.longName ?: chartMeta.shortName ?: ticker }
                    )
                }
            }

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

            StockReport(
                snapshot = snapshot,
                fundamentals = fundamentals,
                sector = sector,
                asymmetry = asymmetry,
                score = score,
                narrative = narrative,
                businessSummary = targetResult.assetProfile?.longBusinessSummary
            )
        }
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
