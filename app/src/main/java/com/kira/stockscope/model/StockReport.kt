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

    companion object {
        /** Used when quoteSummary is unreachable but a bare price still came back from the chart endpoint. */
        val EMPTY = Fundamentals(
            trailingPe = null, forwardPe = null, trailingEps = null, forwardEps = null,
            revenueGrowth = null, earningsGrowth = null, nextYearGrowthEstimate = null,
            grossMargin = null, operatingMargin = null, profitMargin = null,
            totalCash = null, totalDebt = null, freeCashFlow = null, dividendYield = null,
            beta = null, debtToEquity = null, fiftyTwoWeekLow = null, fiftyTwoWeekHigh = null,
            fiftyDayAverage = null, twoHundredDayAverage = null
        )
    }
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

data class AnalystView(
    val recommendationKey: String?,
    val targetMean: Double?,
    val targetHigh: Double?,
    val targetLow: Double?
) {
    /** e.g. "strong_buy" -> "Strong buy" */
    val recommendationLabel: String?
        get() = recommendationKey
            ?.replace('_', ' ')
            ?.lowercase()
            ?.replaceFirstChar { it.uppercase() }

    val hasData: Boolean
        get() = recommendationKey != null || targetMean != null
}

data class StockReport(
    val snapshot: StockSnapshot,
    val fundamentals: Fundamentals,
    val sector: SectorComparison,
    val asymmetry: AsymmetryTargets,
    val score: ConvictionScore,
    val narrative: List<NewsItem>,
    val analystView: AnalystView,
    val businessSummary: String?,
    /** Null when there's too little price history to compute anything (e.g. a recent IPO). */
    val technicals: TechnicalReading?,
    /** Short reason fundamentals/sector data is degraded, or null when quoteSummary succeeded. */
    val dataIssue: String? = null
)

/** Lightweight row used on the watchlist screen without a full report fetch. */
data class WatchlistEntry(
    val symbol: String,
    val addedAtEpochMillis: Long
)

/**
 * Cheap alternative to [StockReport] for the watchlist: one network call instead
 * of the full report's target + peers + news + recommendations fan-out. The
 * conviction score is still real, just computed without peer data, so its
 * valuation subscore falls back to a neutral value instead of a peer-relative one.
 */
data class QuickQuote(
    val snapshot: StockSnapshot,
    val score: ConvictionScore
)
