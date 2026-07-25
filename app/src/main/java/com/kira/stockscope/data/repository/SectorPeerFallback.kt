package com.kira.stockscope.data.repository

/**
 * Yahoo's "recommendationsbysymbol" endpoint occasionally returns nothing (or is
 * rate-limited). When that happens the sector pass falls back to a small curated
 * list of large, liquid names per GICS-style sector so the comparison table is
 * never empty.
 */
object SectorPeerFallback {

    private val bySector: Map<String, List<String>> = mapOf(
        "Technology" to listOf("AAPL", "MSFT", "NVDA", "GOOGL", "ORCL"),
        "Communication Services" to listOf("META", "GOOGL", "NFLX", "DIS", "TMUS"),
        "Consumer Cyclical" to listOf("AMZN", "TSLA", "HD", "MCD", "NKE"),
        "Consumer Defensive" to listOf("WMT", "PG", "KO", "PEP", "COST"),
        "Financial Services" to listOf("JPM", "BAC", "WFC", "GS", "MA"),
        "Healthcare" to listOf("UNH", "JNJ", "LLY", "ABBV", "PFE"),
        "Industrials" to listOf("CAT", "GE", "HON", "UPS", "BA"),
        "Energy" to listOf("XOM", "CVX", "COP", "SLB", "EOG"),
        "Utilities" to listOf("NEE", "DUK", "SO", "AEP", "D"),
        "Real Estate" to listOf("PLD", "AMT", "EQIX", "SPG", "O"),
        "Basic Materials" to listOf("LIN", "SHW", "APD", "ECL", "FCX")
    )

    fun peersFor(sector: String?, exclude: String): List<String> {
        val list = bySector[sector] ?: return emptyList()
        return list.filter { !it.equals(exclude, ignoreCase = true) }.take(5)
    }
}
