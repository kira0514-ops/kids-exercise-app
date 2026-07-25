package com.kira.stockscope.data.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class ChartDto(
    val chart: ChartWrapper
)

@Serializable
data class ChartWrapper(
    val result: List<ChartResult>? = null,
    val error: ChartError? = null
)

@Serializable
data class ChartError(
    val code: String? = null,
    val description: String? = null
)

@Serializable
data class ChartResult(
    val meta: ChartMeta,
    val timestamp: List<Long>? = null,
    val indicators: ChartIndicators? = null
)

@Serializable
data class ChartMeta(
    val currency: String? = null,
    val symbol: String? = null,
    val exchangeName: String? = null,
    val regularMarketPrice: Double? = null,
    val previousClose: Double? = null,
    val chartPreviousClose: Double? = null,
    val fiftyTwoWeekHigh: Double? = null,
    val fiftyTwoWeekLow: Double? = null,
    val regularMarketVolume: Long? = null,
    val regularMarketTime: Long? = null,
    val longName: String? = null,
    val shortName: String? = null
)

@Serializable
data class ChartIndicators(
    val quote: List<ChartQuote>? = null
)

@Serializable
data class ChartQuote(
    val close: List<Double?>? = null,
    val volume: List<Long?>? = null
)
