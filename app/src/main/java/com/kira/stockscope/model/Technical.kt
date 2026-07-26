package com.kira.stockscope.model

/** One price bar — a daily bar as fetched, or a weekly bar resampled from daily ones. */
data class Candle(
    val timestamp: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Long?
)

/** A confirmed local price pivot: needs bars on both sides lower/higher than it to count. */
data class SwingPoint(
    val price: Double,
    val timestampEpochSeconds: Long,
    val barsAgo: Int
)

enum class TrendDirection { UP, DOWN, MIXED, UNKNOWN }

/**
 * The most recent confirmed swing highs/lows for one timeframe and pivot width,
 * most-recent-first. Two points of each are enough to read the classic Dow-theory
 * structure: higher highs + higher lows means an uptrend, lower highs + lower
 * lows means a downtrend, anything else is neither.
 */
data class SwingSeries(
    val highs: List<SwingPoint>,
    val lows: List<SwingPoint>
) {
    val structureTrend: TrendDirection
        get() {
            if (highs.size < 2 || lows.size < 2) return TrendDirection.UNKNOWN
            val higherHighs = highs[0].price > highs[1].price
            val higherLows = lows[0].price > lows[1].price
            val lowerHighs = highs[0].price < highs[1].price
            val lowerLows = lows[0].price < lows[1].price
            return when {
                higherHighs && higherLows -> TrendDirection.UP
                lowerHighs && lowerLows -> TrendDirection.DOWN
                else -> TrendDirection.MIXED
            }
        }
}

data class MacdReading(
    val macd: Double,
    val signal: Double,
    val histogram: Double
)

enum class ConfirmationStatus { CONFIRMED_UP, CONFIRMED_DOWN, MIXED, INSUFFICIENT_DATA }

/**
 * A trend only counts as "confirmed" here when two independent kinds of evidence
 * agree: the swing structure across timeframes (price action itself — higher
 * highs/higher lows or the reverse) and a majority of the momentum/trend
 * indicators (RSI vs. 50, MACD histogram sign, +DI/-DI when ADX shows a real
 * trend, price vs. its moving averages). Agreement on both is "confirmed";
 * agreement on only one, or disagreement, is reported as mixed rather than
 * rounded up to a verdict the evidence doesn't fully support.
 */
data class TrendConfirmation(
    val status: ConfirmationStatus,
    val swingVerdict: TrendDirection,
    val indicatorVerdict: TrendDirection,
    val bullishSignals: Int,
    val bearishSignals: Int,
    val totalSignals: Int
)

data class TechnicalReading(
    val lastClose: Double,
    val rsi14: Double?,
    val macd: MacdReading?,
    val adx14: Double?,
    val plusDi14: Double?,
    val minusDi14: Double?,
    val stochK: Double?,
    val stochD: Double?,
    val sma20: Double?,
    val sma50: Double?,
    val sma200: Double?,
    /** 3-bar fractal on daily bars — short-term pivots. */
    val dailySwings: SwingSeries,
    /** Wider 8-bar fractal on daily bars — the same pivots a swing trader would mark. */
    val majorDailySwings: SwingSeries,
    /** 3-bar fractal on weekly-resampled bars — the higher-timeframe structure. */
    val weeklySwings: SwingSeries,
    val trendConfirmation: TrendConfirmation
)
