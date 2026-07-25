package com.kira.stockscope.model

data class StockSnapshot(
    val symbol: String,
    val name: String,
    val sector: String?,
    val industry: String?,
    val currency: String?,
    val price: Double?,
    val changePercent: Double?,
    val marketCap: Double?
)

data class Fundamentals(
    val trailingPe: Double?,
    val forwardPe: Double?,
    val trailingEps: Double?,
    val forwardEps: Double?,
    val revenueGrowth: Double?,
    val earningsGrowth: Double?,
    val nextYearGrowthEstimate: Double?,
    val grossMargin: Double?,
    val operatingMargin: Double?,
    val profitMargin: Double?,
    val totalCash: Double?,
    val totalDebt: Double?,
    val freeCashFlow: Double?,
    val dividendYield: Double?,
    val beta: Double?,
    val debtToEquity: Double?,
    val fiftyTwoWeekLow: Double?,
    val fiftyTwoWeekHigh: Double?,
    val fiftyDayAverage: Double?,
    val twoHundredDayAverage: Double?
) {
    val netCash: Double?
        get() = if (totalCash != null && totalDebt != null) totalCash - totalDebt else null
}

data class PeerRow(
    val symbol: String,
    val name: String,
    val price: Double?,
    val marketCap: Double?,
    val trailingPe: Double?,
    val forwardPe: Double?,
    val profitMargin: Double?,
    val revenueGrowth: Double?,
    val isTarget: Boolean = false
)

data class SectorComparison(
    val peers: List<PeerRow>,
    val targetSymbol: String,
    val peerAverageForwardPe: Double?,
    val peerAverageTrailingPe: Double?,
    val peerLowForwardPe: Double?,
    val peerHighForwardPe: Double?,
    val targetRankByForwardPe: Int?,
    val totalRanked: Int
)

data class AsymmetryTargets(
    val currentPrice: Double?,
    val bear: Double?,
    val base: Double?,
    val bull: Double?,
    val stretchedBull: Double?,
    val entryZone: Double?,
    val trimZone: Double?,
    val thesisBreak: Double?,
    val anchorMultiple: Double?,
    val anchorEps: Double?
)

data class ConvictionScore(
    val valuationScore: Int,
    val growthScore: Int,
    val qualityScore: Int,
    val balanceSheetScore: Int
) {
    val total: Int get() = valuationScore + growthScore + qualityScore + balanceSheetScore

    val tier: String
        get() = when {
            total >= 80 -> "Strong Conviction"
            total >= 60 -> "Buy"
            total >= 40 -> "Hold / Watch"
            else -> "Avoid"
        }
}

data class NewsItem(
    val title: String,
    val publisher: String?,
    val link: String?,
    val publishedAtEpochSeconds: Long?
)

data class StockReport(
    val snapshot: StockSnapshot,
    val fundamentals: Fundamentals,
    val sector: SectorComparison,
    val asymmetry: AsymmetryTargets,
    val score: ConvictionScore,
    val narrative: List<NewsItem>,
    val businessSummary: String?
)

/** Lightweight row used on the watchlist screen without a full report fetch. */
data class WatchlistEntry(
    val symbol: String,
    val addedAtEpochMillis: Long
)
