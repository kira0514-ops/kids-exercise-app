package com.kira.stockscope.domain

import com.kira.stockscope.model.Fundamentals
import com.kira.stockscope.model.SectorComparison
import com.kira.stockscope.model.StockSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoringEngineTest {

    private fun snapshot(price: Double? = 100.0, marketCap: Double? = 1_000_000_000.0) = StockSnapshot(
        symbol = "TEST",
        name = "Test Co",
        sector = "Technology",
        industry = "Software",
        currency = "USD",
        price = price,
        changePercent = 1.0,
        marketCap = marketCap
    )

    private fun fundamentals(
        trailingPe: Double? = null,
        forwardPe: Double? = null,
        trailingEps: Double? = null,
        forwardEps: Double? = null,
        revenueGrowth: Double? = null,
        earningsGrowth: Double? = null,
        nextYearGrowthEstimate: Double? = null,
        grossMargin: Double? = null,
        operatingMargin: Double? = null,
        profitMargin: Double? = null,
        totalCash: Double? = null,
        totalDebt: Double? = null,
        freeCashFlow: Double? = null,
        dividendYield: Double? = null,
        beta: Double? = null,
        debtToEquity: Double? = null,
        fiftyTwoWeekLow: Double? = null,
        fiftyTwoWeekHigh: Double? = null,
        fiftyDayAverage: Double? = null,
        twoHundredDayAverage: Double? = null
    ) = Fundamentals(
        trailingPe = trailingPe,
        forwardPe = forwardPe,
        trailingEps = trailingEps,
        forwardEps = forwardEps,
        revenueGrowth = revenueGrowth,
        earningsGrowth = earningsGrowth,
        nextYearGrowthEstimate = nextYearGrowthEstimate,
        grossMargin = grossMargin,
        operatingMargin = operatingMargin,
        profitMargin = profitMargin,
        totalCash = totalCash,
        totalDebt = totalDebt,
        freeCashFlow = freeCashFlow,
        dividendYield = dividendYield,
        beta = beta,
        debtToEquity = debtToEquity,
        fiftyTwoWeekLow = fiftyTwoWeekLow,
        fiftyTwoWeekHigh = fiftyTwoWeekHigh,
        fiftyDayAverage = fiftyDayAverage,
        twoHundredDayAverage = twoHundredDayAverage
    )

    private fun sector(
        peerAverageForwardPe: Double? = null,
        peerAverageTrailingPe: Double? = null,
        peerLowForwardPe: Double? = null,
        peerHighForwardPe: Double? = null
    ) = SectorComparison(
        peers = emptyList(),
        targetSymbol = "TEST",
        peerAverageForwardPe = peerAverageForwardPe,
        peerAverageTrailingPe = peerAverageTrailingPe,
        peerLowForwardPe = peerLowForwardPe,
        peerHighForwardPe = peerHighForwardPe,
        targetRankByForwardPe = 1,
        totalRanked = 5
    )

    // --- valuation ---

    @Test
    fun `cheap valuation relative to peers scores near max`() {
        val f = fundamentals(forwardPe = 10.0)
        val s = sector(peerAverageForwardPe = 20.0)
        val score = ScoringEngine.score(f, s, snapshot())
        assertEquals(25, score.valuationScore)
    }

    @Test
    fun `expensive valuation relative to peers scores zero`() {
        val f = fundamentals(forwardPe = 40.0)
        val s = sector(peerAverageForwardPe = 20.0)
        val score = ScoringEngine.score(f, s, snapshot())
        assertEquals(0, score.valuationScore)
    }

    @Test
    fun `missing PE data falls back to a neutral valuation score`() {
        val f = fundamentals()
        val s = sector()
        val score = ScoringEngine.score(f, s, snapshot())
        assertEquals(12, score.valuationScore)
    }

    // --- growth ---

    @Test
    fun `strong growth scores max`() {
        val f = fundamentals(revenueGrowth = 0.30)
        val score = ScoringEngine.score(f, sector(), snapshot())
        assertEquals(25, score.growthScore)
    }

    @Test
    fun `negative growth scores zero`() {
        val f = fundamentals(revenueGrowth = -0.05)
        val score = ScoringEngine.score(f, sector(), snapshot())
        assertEquals(0, score.growthScore)
    }

    // --- quality / margins ---

    @Test
    fun `high margins score max quality`() {
        val f = fundamentals(profitMargin = 0.25, operatingMargin = 0.30)
        val score = ScoringEngine.score(f, sector(), snapshot())
        assertEquals(25, score.qualityScore)
    }

    @Test
    fun `negative margins score zero quality`() {
        val f = fundamentals(profitMargin = -0.10, operatingMargin = -0.05)
        val score = ScoringEngine.score(f, sector(), snapshot())
        assertEquals(0, score.qualityScore)
    }

    // --- balance sheet ---

    @Test
    fun `large net cash position relative to market cap scores max`() {
        val f = fundamentals(totalCash = 300_000_000.0, totalDebt = 0.0)
        val score = ScoringEngine.score(f, sector(), snapshot(marketCap = 1_000_000_000.0))
        assertEquals(25, score.balanceSheetScore)
    }

    @Test
    fun `heavy net debt relative to market cap scores zero`() {
        val f = fundamentals(totalCash = 0.0, totalDebt = 600_000_000.0)
        val score = ScoringEngine.score(f, sector(), snapshot(marketCap = 1_000_000_000.0))
        assertEquals(0, score.balanceSheetScore)
    }

    @Test
    fun `falls back to debt-to-equity when cash and market cap are unavailable`() {
        val f = fundamentals(debtToEquity = 20.0)
        val score = ScoringEngine.score(f, sector(), snapshot(marketCap = null))
        assertEquals(25, score.balanceSheetScore)
    }

    // --- tier mapping ---

    @Test
    fun `total score of 80 or above is strong conviction`() {
        val f = fundamentals(
            forwardPe = 10.0,
            revenueGrowth = 0.30,
            profitMargin = 0.25,
            operatingMargin = 0.30,
            totalCash = 300_000_000.0,
            totalDebt = 0.0
        )
        val s = sector(peerAverageForwardPe = 20.0)
        val score = ScoringEngine.score(f, s, snapshot(marketCap = 1_000_000_000.0))
        assertEquals(100, score.total)
        assertEquals("Strong Conviction", score.tier)
    }

    @Test
    fun `total score below 40 is avoid`() {
        val f = fundamentals(
            forwardPe = 40.0,
            revenueGrowth = -0.05,
            profitMargin = -0.10,
            operatingMargin = -0.05,
            totalCash = 0.0,
            totalDebt = 600_000_000.0
        )
        val s = sector(peerAverageForwardPe = 20.0)
        val score = ScoringEngine.score(f, s, snapshot(marketCap = 1_000_000_000.0))
        assertEquals(0, score.total)
        assertEquals("Avoid", score.tier)
    }

    // --- asymmetry targets ---

    @Test
    fun `asymmetry targets order bear less than base less than bull less than stretched bull`() {
        val f = fundamentals(forwardEps = 5.0)
        val s = sector(peerAverageForwardPe = 20.0, peerLowForwardPe = 12.0, peerHighForwardPe = 30.0)
        val targets = ScoringEngine.asymmetryTargets(snapshot(price = 100.0), f, s)

        requireNotNull(targets.bear)
        requireNotNull(targets.base)
        requireNotNull(targets.bull)
        requireNotNull(targets.stretchedBull)

        assertTrue(targets.bear!! < targets.base!!)
        assertTrue(targets.base!! < targets.bull!!)
        assertTrue(targets.bull!! < targets.stretchedBull!!)
    }

    @Test
    fun `asymmetry targets are null when there is no EPS to anchor on`() {
        val f = fundamentals(forwardEps = null, trailingEps = null)
        val s = sector(peerAverageForwardPe = 20.0)
        val targets = ScoringEngine.asymmetryTargets(snapshot(), f, s)

        assertNull(targets.bear)
        assertNull(targets.base)
        assertNull(targets.bull)
    }

    @Test
    fun `entry zone is at or below current price`() {
        val f = fundamentals(fiftyDayAverage = 95.0)
        val targets = ScoringEngine.asymmetryTargets(snapshot(price = 100.0), f, sector())

        requireNotNull(targets.entryZone)
        assertTrue(targets.entryZone!! <= 100.0)
    }
}
