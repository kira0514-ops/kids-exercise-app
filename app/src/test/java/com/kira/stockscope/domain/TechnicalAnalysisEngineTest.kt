package com.kira.stockscope.domain

import com.kira.stockscope.model.Candle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TechnicalAnalysisEngineTest {

    private fun candlesFromCloses(closes: List<Double>, spread: Double = 0.5): List<Candle> =
        closes.mapIndexed { index, close ->
            Candle(
                timestamp = 1_700_000_000L + index * 86_400L,
                open = close,
                high = close + spread,
                low = close - spread,
                close = close,
                volume = 1_000_000L
            )
        }

    private fun linearSeries(count: Int, start: Double = 1.0, step: Double = 1.0): List<Double> =
        (0 until count).map { start + it * step }

    // --- guard rails ---

    @Test
    fun `analyze returns null with fewer than two candles`() {
        assertNull(TechnicalAnalysisEngine.analyze(candlesFromCloses(listOf(1.0))))
        assertNull(TechnicalAnalysisEngine.analyze(emptyList()))
    }

    @Test
    fun `each indicator degrades to null independently as history gets shorter, not all at once`() {
        // 20 bars: enough for RSI(14+1) and Stochastic(14+2*3), not enough for
        // MACD(26+9) or ADX(2*14+1) or SMA50/200.
        val candles = candlesFromCloses(linearSeries(20))
        val reading = requireNotNull(TechnicalAnalysisEngine.analyze(candles))

        assertTrue("RSI should be available with 20 bars", reading.rsi14 != null)
        assertTrue("SMA20 should be available with exactly 20 bars", reading.sma20 != null)
        assertTrue("Stochastic should be available with 20 bars", reading.stochK != null)

        assertNull("SMA50 needs 50 bars", reading.sma50)
        assertNull("SMA200 needs 200 bars", reading.sma200)
        assertNull("MACD needs 35 bars", reading.macd)
        assertNull("ADX needs 29 bars", reading.adx14)

        // A strictly monotonic series has no interior point whose neighbors are
        // more extreme on both sides, so it should never report a fractal pivot.
        assertNull(reading.swingHigh)
        assertNull(reading.swingLow)
    }

    // --- RSI ---

    @Test
    fun `RSI is 100 for a strictly rising series with no down days`() {
        val candles = candlesFromCloses(linearSeries(30))
        val reading = requireNotNull(TechnicalAnalysisEngine.analyze(candles))
        assertEquals(100.0, reading.rsi14!!, 0.001)
    }

    @Test
    fun `RSI is 0 for a strictly falling series with no up days`() {
        val candles = candlesFromCloses(linearSeries(30, start = 100.0, step = -1.0))
        val reading = requireNotNull(TechnicalAnalysisEngine.analyze(candles))
        assertEquals(0.0, reading.rsi14!!, 0.001)
    }

    // --- SMA ---

    @Test
    fun `SMA20 is the plain average of the last 20 closes`() {
        val closes = linearSeries(25) // 1..25
        val candles = candlesFromCloses(closes)
        val reading = requireNotNull(TechnicalAnalysisEngine.analyze(candles))
        // last 20 values are 6..25, average = (6 + 25) / 2 = 15.5
        assertEquals(15.5, reading.sma20!!, 0.0001)
        assertNull(reading.sma50)
    }

    // --- ADX / DI ---

    @Test
    fun `ADX is null with fewer than 29 bars`() {
        val candles = candlesFromCloses(linearSeries(28))
        val reading = requireNotNull(TechnicalAnalysisEngine.analyze(candles))
        assertNull(reading.adx14)
    }

    @Test
    fun `a clean uptrend drives ADX to 100 with plusDI dominant and minusDI at zero`() {
        // With a constant +1 step and a constant 0.5 high/low spread, every bar has
        // +DM = 1, -DM = 0, TR = 1.5, which is already at the Wilder-smoothing
        // equilibrium from the very first bar — so DX = 100 on every bar and ADX
        // converges to exactly 100, not just approximately.
        val candles = candlesFromCloses(linearSeries(60))
        val reading = requireNotNull(TechnicalAnalysisEngine.analyze(candles))

        assertEquals(100.0, reading.adx14!!, 0.01)
        assertEquals(0.0, reading.minusDi14!!, 0.0001)
        assertEquals(100.0 * 14 / 21, reading.plusDi14!!, 0.01)
        assertTrue(reading.plusDi14!! > reading.minusDi14!!)
    }

    // --- MACD ---

    // Only the MACD line's sign is asserted here, not the histogram's: the fast
    // EMA(12) puts more weight on recent, larger values than the slow EMA(26) does
    // for any strictly increasing series, from the very first bar both are defined
    // on — not just in steady state — so fast > slow (MACD > 0) is safe to assert
    // by construction. The histogram (MACD vs. its own EMA9 signal) depends on
    // where MACD sits in its warm-up transient, which isn't something to pin down
    // by hand without running the numbers, so it's left untested here.

    @Test
    fun `MACD line is positive during a sustained uptrend`() {
        val candles = candlesFromCloses(linearSeries(60))
        val reading = requireNotNull(TechnicalAnalysisEngine.analyze(candles))
        val macd = requireNotNull(reading.macd)
        assertTrue(macd.macd > 0)
    }

    @Test
    fun `MACD line is negative during a sustained downtrend`() {
        val candles = candlesFromCloses(linearSeries(60, start = 200.0, step = -1.0))
        val reading = requireNotNull(TechnicalAnalysisEngine.analyze(candles))
        val macd = requireNotNull(reading.macd)
        assertTrue(macd.macd < 0)
    }

    // --- Stochastic ---

    @Test
    fun `Stochastic sits near the top of its range during a sustained uptrend`() {
        val candles = candlesFromCloses(linearSeries(60))
        val reading = requireNotNull(TechnicalAnalysisEngine.analyze(candles))
        assertTrue(reading.stochK!! > 90.0)
        assertTrue(reading.stochD!! > 90.0)
    }

    // --- swing high / low ---

    @Test
    fun `swing high is found at a confirmed local peak`() {
        // Rises 1..10, spikes to 20, then falls 9..1 — the spike at index 10 has
        // three lower-high bars on each side, so it's a confirmed fractal peak.
        val closes = linearSeries(10) + listOf(20.0) + linearSeries(9, start = 9.0, step = -1.0)
        val candles = candlesFromCloses(closes)

        val reading = requireNotNull(TechnicalAnalysisEngine.analyze(candles))
        val swingHigh = requireNotNull(reading.swingHigh)

        assertEquals(20.5, swingHigh.price, 0.0001) // stored as the bar's high, not its close
        assertEquals(candles.size - 1 - 10, swingHigh.barsAgo)
        assertEquals(candles[10].timestamp, swingHigh.timestampEpochSeconds)
    }

    @Test
    fun `swing low is found at a confirmed local trough`() {
        // Falls 10..1, dips to -5, then rises 2..10 — mirror image of the peak case.
        val closes = linearSeries(10, start = 10.0, step = -1.0) + listOf(-5.0) + linearSeries(9, start = 2.0, step = 1.0)
        val candles = candlesFromCloses(closes)

        val reading = requireNotNull(TechnicalAnalysisEngine.analyze(candles))
        val swingLow = requireNotNull(reading.swingLow)

        assertEquals(-5.5, swingLow.price, 0.0001) // stored as the bar's low, not its close
        assertEquals(candles.size - 1 - 10, swingLow.barsAgo)
        assertEquals(candles[10].timestamp, swingLow.timestampEpochSeconds)
    }

    @Test
    fun `swing detection needs enough confirming bars on both sides`() {
        // Only 2 bars after the spike instead of the 3 the fractal width requires,
        // so the peak can't be confirmed yet.
        val closes = linearSeries(10) + listOf(20.0) + listOf(9.0, 8.0)
        val candles = candlesFromCloses(closes)
        val reading = requireNotNull(TechnicalAnalysisEngine.analyze(candles))
        assertNull(reading.swingHigh)
    }
}
