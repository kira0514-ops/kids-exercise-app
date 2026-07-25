package com.kira.stockscope.model

/** One daily price bar. */
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

data class MacdReading(
    val macd: Double,
    val signal: Double,
    val histogram: Double
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
    val swingHigh: SwingPoint?,
    val swingLow: SwingPoint?
)
