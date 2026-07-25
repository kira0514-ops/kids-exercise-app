package com.kira.stockscope.domain

import com.kira.stockscope.model.Candle
import com.kira.stockscope.model.ConfirmationStatus
import com.kira.stockscope.model.MacdReading
import com.kira.stockscope.model.SwingPoint
import com.kira.stockscope.model.SwingSeries
import com.kira.stockscope.model.TechnicalReading
import com.kira.stockscope.model.TrendConfirmation
import com.kira.stockscope.model.TrendDirection
import kotlin.math.abs

/**
 * Computes standard technical readings from daily OHLC candles: Wilder's RSI(14),
 * MACD(12,26,9), Wilder's ADX(14) with +DI/-DI, a slow Stochastic(14,3,3), simple
 * moving averages, swing structure across three timeframes/widths (daily minor,
 * daily major, weekly), and a trend confirmation verdict that combines them.
 * Every reading is independently nullable/empty — with less history than an
 * indicator needs it simply doesn't report a value rather than guessing.
 */
object TechnicalAnalysisEngine {

    private const val RSI_PERIOD = 14
    private const val MACD_FAST = 12
    private const val MACD_SLOW = 26
    private const val MACD_SIGNAL = 9
    private const val ADX_PERIOD = 14
    private const val STOCH_PERIOD = 14
    private const val STOCH_SMOOTH = 3
    private const val ADX_TREND_THRESHOLD = 20.0

    /** Short-term pivots: a bar more extreme than the 3 bars on each side of it. */
    private const val MINOR_FRACTAL_WIDTH = 3

    /** The wider pivots a swing trader would mark, still on daily bars. */
    private const val MAJOR_FRACTAL_WIDTH = 8

    private const val SWING_HISTORY_COUNT = 3
    private const val SECONDS_PER_DAY = 86_400L

    fun analyze(candles: List<Candle>): TechnicalReading? {
        if (candles.size < 2) return null
        val sorted = candles.sortedBy { it.timestamp }
        val closes = sorted.map { it.close }
        val highs = sorted.map { it.high }
        val lows = sorted.map { it.low }

        val adxResult = computeAdx(highs, lows, closes, ADX_PERIOD)
        val stochResult = computeStochastic(highs, lows, closes, STOCH_PERIOD, STOCH_SMOOTH)
        val rsi = computeRsi(closes, RSI_PERIOD)
        val macd = computeMacd(closes)
        val sma20 = sma(closes, 20)
        val sma50 = sma(closes, 50)
        val sma200 = sma(closes, 200)
        val lastClose = closes.last()

        val dailyMinorSwings = buildSwingSeries(sorted, MINOR_FRACTAL_WIDTH)
        val dailyMajorSwings = buildSwingSeries(sorted, MAJOR_FRACTAL_WIDTH)
        val weeklySwings = buildSwingSeries(resampleToWeekly(sorted), MINOR_FRACTAL_WIDTH)

        val trendConfirmation = computeTrendConfirmation(
            swingSeriesList = listOf(dailyMinorSwings, dailyMajorSwings, weeklySwings),
            rsi = rsi,
            macd = macd,
            adx = adxResult?.adx,
            plusDi = adxResult?.plusDi,
            minusDi = adxResult?.minusDi,
            lastClose = lastClose,
            movingAverages = listOfNotNull(sma20, sma50, sma200)
        )

        return TechnicalReading(
            lastClose = lastClose,
            rsi14 = rsi,
            macd = macd,
            adx14 = adxResult?.adx,
            plusDi14 = adxResult?.plusDi,
            minusDi14 = adxResult?.minusDi,
            stochK = stochResult?.k,
            stochD = stochResult?.d,
            sma20 = sma20,
            sma50 = sma50,
            sma200 = sma200,
            dailySwings = dailyMinorSwings,
            majorDailySwings = dailyMajorSwings,
            weeklySwings = weeklySwings,
            trendConfirmation = trendConfirmation
        )
    }

    private fun sma(values: List<Double>, period: Int): Double? {
        if (values.size < period) return null
        return values.takeLast(period).average()
    }

    /**
     * EMA aligned to [values]' indices: entries before the seed index are null,
     * the seed (index period-1) is a simple average, everything after is the
     * standard EMA recurrence. Returning nulls in place (rather than a shorter
     * list) makes it safe to line up two EMAs of different periods index-by-index.
     */
    private fun emaFull(values: List<Double>, period: Int): List<Double?> {
        if (values.size < period) return List(values.size) { null }
        val k = 2.0 / (period + 1)
        val result = MutableList<Double?>(values.size) { null }
        var prev = values.take(period).average()
        result[period - 1] = prev
        for (i in period until values.size) {
            val ema = values[i] * k + prev * (1 - k)
            result[i] = ema
            prev = ema
        }
        return result
    }

    private fun computeRsi(closes: List<Double>, period: Int): Double? {
        if (closes.size < period + 1) return null
        val changes = (1 until closes.size).map { closes[it] - closes[it - 1] }

        var avgGain = changes.take(period).map { if (it > 0) it else 0.0 }.average()
        var avgLoss = changes.take(period).map { if (it < 0) -it else 0.0 }.average()

        for (i in period until changes.size) {
            val change = changes[i]
            val gain = if (change > 0) change else 0.0
            val loss = if (change < 0) -change else 0.0
            avgGain = (avgGain * (period - 1) + gain) / period
            avgLoss = (avgLoss * (period - 1) + loss) / period
        }

        if (avgLoss == 0.0) return 100.0
        val rs = avgGain / avgLoss
        return 100.0 - 100.0 / (1.0 + rs)
    }

    private fun computeMacd(closes: List<Double>): MacdReading? {
        if (closes.size < MACD_SLOW + MACD_SIGNAL) return null
        val fastFull = emaFull(closes, MACD_FAST)
        val slowFull = emaFull(closes, MACD_SLOW)

        val macdSeries = closes.indices.mapNotNull { i ->
            val fast = fastFull[i]
            val slow = slowFull[i]
            if (fast != null && slow != null) fast - slow else null
        }
        if (macdSeries.size < MACD_SIGNAL) return null

        val signalSeries = emaFull(macdSeries, MACD_SIGNAL).filterNotNull()
        if (signalSeries.isEmpty()) return null

        val macdValue = macdSeries.last()
        val signalValue = signalSeries.last()
        return MacdReading(macd = macdValue, signal = signalValue, histogram = macdValue - signalValue)
    }

    private data class AdxResult(val adx: Double, val plusDi: Double, val minusDi: Double)

    private fun computeAdx(highs: List<Double>, lows: List<Double>, closes: List<Double>, period: Int): AdxResult? {
        val n = highs.size
        if (n < period * 2 + 1) return null

        val plusDm = DoubleArray(n)
        val minusDm = DoubleArray(n)
        val tr = DoubleArray(n)

        for (i in 1 until n) {
            val upMove = highs[i] - highs[i - 1]
            val downMove = lows[i - 1] - lows[i]
            plusDm[i] = if (upMove > downMove && upMove > 0) upMove else 0.0
            minusDm[i] = if (downMove > upMove && downMove > 0) downMove else 0.0
            tr[i] = maxOf(
                highs[i] - lows[i],
                abs(highs[i] - closes[i - 1]),
                abs(lows[i] - closes[i - 1])
            )
        }

        fun diAndDx(plusDmSum: Double, minusDmSum: Double, trSum: Double): Triple<Double, Double, Double> {
            if (trSum == 0.0) return Triple(0.0, 0.0, 0.0)
            val plusDi = 100 * plusDmSum / trSum
            val minusDi = 100 * minusDmSum / trSum
            val dx = if (plusDi + minusDi == 0.0) 0.0 else 100 * abs(plusDi - minusDi) / (plusDi + minusDi)
            return Triple(plusDi, minusDi, dx)
        }

        var smoothedPlusDm = (1..period).sumOf { plusDm[it] }
        var smoothedMinusDm = (1..period).sumOf { minusDm[it] }
        var smoothedTr = (1..period).sumOf { tr[it] }

        var (plusDi, minusDi, dx) = diAndDx(smoothedPlusDm, smoothedMinusDm, smoothedTr)
        val dxList = mutableListOf(dx)

        for (i in (period + 1) until n) {
            smoothedPlusDm = smoothedPlusDm - smoothedPlusDm / period + plusDm[i]
            smoothedMinusDm = smoothedMinusDm - smoothedMinusDm / period + minusDm[i]
            smoothedTr = smoothedTr - smoothedTr / period + tr[i]
            val (pDi, mDi, dxValue) = diAndDx(smoothedPlusDm, smoothedMinusDm, smoothedTr)
            plusDi = pDi
            minusDi = mDi
            dx = dxValue
            dxList.add(dx)
        }

        if (dxList.size < period) return null

        var adx = dxList.take(period).average()
        for (i in period until dxList.size) {
            adx = (adx * (period - 1) + dxList[i]) / period
        }

        return AdxResult(adx = adx, plusDi = plusDi, minusDi = minusDi)
    }

    private data class StochResult(val k: Double, val d: Double)

    private fun computeStochastic(
        highs: List<Double>,
        lows: List<Double>,
        closes: List<Double>,
        period: Int,
        smooth: Int
    ): StochResult? {
        val n = closes.size
        if (n < period + smooth * 2) return null

        val fastK = (period - 1 until n).map { i ->
            val windowHighs = highs.subList(i - period + 1, i + 1)
            val windowLows = lows.subList(i - period + 1, i + 1)
            val highestHigh = windowHighs.max()
            val lowestLow = windowLows.min()
            if (highestHigh == lowestLow) 50.0 else 100 * (closes[i] - lowestLow) / (highestHigh - lowestLow)
        }
        if (fastK.size < smooth) return null

        val slowK = (smooth - 1 until fastK.size).map { i ->
            fastK.subList(i - smooth + 1, i + 1).average()
        }
        if (slowK.size < smooth) return null

        val slowD = slowK.takeLast(smooth).average()
        return StochResult(k = slowK.last(), d = slowD)
    }

    private fun buildSwingSeries(candles: List<Candle>, fractalWidth: Int): SwingSeries {
        val highIndices = findSwingIndices(candles, fractalWidth) { candidate, other -> other.high < candidate.high }
        val lowIndices = findSwingIndices(candles, fractalWidth) { candidate, other -> other.low > candidate.low }
        val highs = highIndices.map { i -> SwingPoint(candles[i].high, candles[i].timestamp, candles.size - 1 - i) }
        val lows = lowIndices.map { i -> SwingPoint(candles[i].low, candles[i].timestamp, candles.size - 1 - i) }
        return SwingSeries(highs, lows)
    }

    /**
     * Scans backward from the most recent confirmable bar, collecting the indices
     * of up to [SWING_HISTORY_COUNT] fractal pivots, most-recent first. A pivot at
     * index i needs [fractalWidth] bars on each side that are all less extreme —
     * i.e. it can't be confirmed until [fractalWidth] bars have closed after it.
     */
    private inline fun findSwingIndices(
        candles: List<Candle>,
        fractalWidth: Int,
        isMoreExtreme: (candidate: Candle, other: Candle) -> Boolean
    ): List<Int> {
        val n = candles.size
        val lastConfirmable = n - 1 - fractalWidth
        if (lastConfirmable < fractalWidth) return emptyList()

        val result = mutableListOf<Int>()
        for (i in lastConfirmable downTo fractalWidth) {
            if (result.size >= SWING_HISTORY_COUNT) break
            val candidate = candles[i]
            val isPivot = (i - fractalWidth until i).all { isMoreExtreme(candidate, candles[it]) } &&
                (i + 1..i + fractalWidth).all { isMoreExtreme(candidate, candles[it]) }
            if (isPivot) result.add(i)
        }
        return result
    }

    /**
     * Aggregates daily bars into calendar weeks (Monday start): open = the week's
     * first bar's open, high/low = the week's extremes, close = the week's last
     * bar's close, volume = summed. Used to run the same fractal pivot detection
     * on a higher timeframe instead of just widening the daily lookback.
     */
    private fun resampleToWeekly(daily: List<Candle>): List<Candle> {
        if (daily.isEmpty()) return emptyList()
        return daily.groupBy { weekStartEpochDay(it.timestamp / SECONDS_PER_DAY) }
            .toSortedMap()
            .map { (weekStartDay, bars) ->
                val sortedBars = bars.sortedBy { it.timestamp }
                Candle(
                    timestamp = weekStartDay * SECONDS_PER_DAY,
                    open = sortedBars.first().open,
                    high = sortedBars.maxOf { it.high },
                    low = sortedBars.minOf { it.low },
                    close = sortedBars.last().close,
                    volume = sortedBars.mapNotNull { it.volume }.takeIf { it.isNotEmpty() }?.sum()
                )
            }
    }

    /** Jan 1 1970 (epoch day 0) was a Thursday, i.e. weekday index 3 in a Monday=0 scheme. */
    private fun weekStartEpochDay(epochDay: Long): Long {
        val weekday = ((epochDay + 3) % 7 + 7) % 7
        return epochDay - weekday
    }

    private fun computeTrendConfirmation(
        swingSeriesList: List<SwingSeries>,
        rsi: Double?,
        macd: MacdReading?,
        adx: Double?,
        plusDi: Double?,
        minusDi: Double?,
        lastClose: Double,
        movingAverages: List<Double>
    ): TrendConfirmation {
        val swingTrends = swingSeriesList.map { it.structureTrend }.filter { it != TrendDirection.UNKNOWN }
        val swingUpVotes = swingTrends.count { it == TrendDirection.UP }
        val swingDownVotes = swingTrends.count { it == TrendDirection.DOWN }
        val swingVerdict = when {
            swingTrends.isEmpty() -> TrendDirection.UNKNOWN
            swingUpVotes > swingDownVotes -> TrendDirection.UP
            swingDownVotes > swingUpVotes -> TrendDirection.DOWN
            else -> TrendDirection.MIXED
        }

        var bullish = 0
        var bearish = 0

        if (rsi != null) {
            if (rsi > 50) bullish++ else if (rsi < 50) bearish++
        }
        if (macd != null) {
            if (macd.histogram > 0) bullish++ else if (macd.histogram < 0) bearish++
        }
        if (adx != null && adx >= ADX_TREND_THRESHOLD && plusDi != null && minusDi != null) {
            if (plusDi > minusDi) bullish++ else if (minusDi > plusDi) bearish++
        }
        if (movingAverages.isNotEmpty()) {
            val above = movingAverages.count { lastClose >= it }
            val below = movingAverages.size - above
            if (above > below) bullish++ else if (below > above) bearish++
        }

        val totalSignals = bullish + bearish
        val indicatorVerdict = when {
            totalSignals == 0 -> TrendDirection.UNKNOWN
            bullish > bearish -> TrendDirection.UP
            bearish > bullish -> TrendDirection.DOWN
            else -> TrendDirection.MIXED
        }

        val status = when {
            swingVerdict == TrendDirection.UNKNOWN && indicatorVerdict == TrendDirection.UNKNOWN ->
                ConfirmationStatus.INSUFFICIENT_DATA
            swingVerdict == TrendDirection.UP && indicatorVerdict == TrendDirection.UP -> ConfirmationStatus.CONFIRMED_UP
            swingVerdict == TrendDirection.DOWN && indicatorVerdict == TrendDirection.DOWN -> ConfirmationStatus.CONFIRMED_DOWN
            else -> ConfirmationStatus.MIXED
        }

        return TrendConfirmation(
            status = status,
            swingVerdict = swingVerdict,
            indicatorVerdict = indicatorVerdict,
            bullishSignals = bullish,
            bearishSignals = bearish,
            totalSignals = totalSignals
        )
    }
}
