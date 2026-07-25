package com.kira.stockscope.domain

import com.kira.stockscope.model.Candle
import com.kira.stockscope.model.MacdReading
import com.kira.stockscope.model.SwingPoint
import com.kira.stockscope.model.TechnicalReading
import kotlin.math.abs

/**
 * Computes standard technical readings from daily OHLC candles: Wilder's RSI(14),
 * MACD(12,26,9), Wilder's ADX(14) with +DI/-DI, a slow Stochastic(14,3,3), simple
 * moving averages, and the most recent confirmed swing high/low (a 3-bar fractal:
 * a bar whose high/low is more extreme than the 3 bars on each side of it). Every
 * reading is independently nullable — with less history than an indicator needs it
 * simply doesn't report a value rather than guessing.
 */
object TechnicalAnalysisEngine {

    private const val RSI_PERIOD = 14
    private const val MACD_FAST = 12
    private const val MACD_SLOW = 26
    private const val MACD_SIGNAL = 9
    private const val ADX_PERIOD = 14
    private const val STOCH_PERIOD = 14
    private const val STOCH_SMOOTH = 3
    private const val SWING_FRACTAL_WIDTH = 3

    fun analyze(candles: List<Candle>): TechnicalReading? {
        if (candles.size < 2) return null
        val sorted = candles.sortedBy { it.timestamp }
        val closes = sorted.map { it.close }
        val highs = sorted.map { it.high }
        val lows = sorted.map { it.low }

        val adxResult = computeAdx(highs, lows, closes, ADX_PERIOD)
        val stochResult = computeStochastic(highs, lows, closes, STOCH_PERIOD, STOCH_SMOOTH)

        return TechnicalReading(
            lastClose = closes.last(),
            rsi14 = computeRsi(closes, RSI_PERIOD),
            macd = computeMacd(closes),
            adx14 = adxResult?.adx,
            plusDi14 = adxResult?.plusDi,
            minusDi14 = adxResult?.minusDi,
            stochK = stochResult?.k,
            stochD = stochResult?.d,
            sma20 = sma(closes, 20),
            sma50 = sma(closes, 50),
            sma200 = sma(closes, 200),
            swingHigh = findSwingHigh(sorted),
            swingLow = findSwingLow(sorted)
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

    private fun findSwingHigh(candles: List<Candle>): SwingPoint? {
        val index = findSwingIndex(candles) { candidate, other -> other.high < candidate.high } ?: return null
        val candle = candles[index]
        return SwingPoint(candle.high, candle.timestamp, candles.size - 1 - index)
    }

    private fun findSwingLow(candles: List<Candle>): SwingPoint? {
        val index = findSwingIndex(candles) { candidate, other -> other.low > candidate.low } ?: return null
        val candle = candles[index]
        return SwingPoint(candle.low, candle.timestamp, candles.size - 1 - index)
    }

    /** Scans backward from the most recent confirmable bar for the nearest fractal pivot's index. */
    private inline fun findSwingIndex(candles: List<Candle>, isMoreExtreme: (candidate: Candle, other: Candle) -> Boolean): Int? {
        val n = candles.size
        val lastConfirmable = n - 1 - SWING_FRACTAL_WIDTH
        if (lastConfirmable < SWING_FRACTAL_WIDTH) return null

        for (i in lastConfirmable downTo SWING_FRACTAL_WIDTH) {
            val candidate = candles[i]
            val isPivot = (i - SWING_FRACTAL_WIDTH until i).all { isMoreExtreme(candidate, candles[it]) } &&
                (i + 1..i + SWING_FRACTAL_WIDTH).all { isMoreExtreme(candidate, candles[it]) }
            if (isPivot) return i
        }
        return null
    }
}
