package com.kira.stockscope.data.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class QuoteSummaryDto(
    val quoteSummary: QuoteSummaryWrapper
)

@Serializable
data class QuoteSummaryWrapper(
    val result: List<QuoteSummaryResult>? = null,
    val error: ChartError? = null
)

@Serializable
data class QuoteSummaryResult(
    val price: PriceModule? = null,
    val summaryDetail: SummaryDetailModule? = null,
    val defaultKeyStatistics: KeyStatisticsModule? = null,
    val financialData: FinancialDataModule? = null,
    val assetProfile: AssetProfileModule? = null,
    val earningsTrend: EarningsTrendModule? = null
)

@Serializable
data class PriceModule(
    val symbol: String? = null,
    val shortName: String? = null,
    val longName: String? = null,
    val currency: String? = null,
    val regularMarketPrice: RawFmt? = null,
    val regularMarketChangePercent: RawFmt? = null,
    val marketCap: RawFmt? = null
)

@Serializable
data class SummaryDetailModule(
    val trailingPE: RawFmt? = null,
    val forwardPE: RawFmt? = null,
    val dividendYield: RawFmt? = null,
    val fiftyTwoWeekLow: RawFmt? = null,
    val fiftyTwoWeekHigh: RawFmt? = null,
    val fiftyDayAverage: RawFmt? = null,
    val twoHundredDayAverage: RawFmt? = null,
    val beta: RawFmt? = null,
    val volume: RawFmt? = null,
    val averageVolume: RawFmt? = null,
    val priceToSalesTrailing12Months: RawFmt? = null
)

@Serializable
data class KeyStatisticsModule(
    val trailingEps: RawFmt? = null,
    val forwardEps: RawFmt? = null,
    val priceToBook: RawFmt? = null,
    val sharesOutstanding: RawFmt? = null,
    val floatShares: RawFmt? = null,
    val pegRatio: RawFmt? = null,
    val shortRatio: RawFmt? = null
)

@Serializable
data class FinancialDataModule(
    val totalCash: RawFmt? = null,
    val totalDebt: RawFmt? = null,
    val totalRevenue: RawFmt? = null,
    val revenueGrowth: RawFmt? = null,
    val earningsGrowth: RawFmt? = null,
    val grossMargins: RawFmt? = null,
    val operatingMargins: RawFmt? = null,
    val profitMargins: RawFmt? = null,
    val returnOnEquity: RawFmt? = null,
    val freeCashflow: RawFmt? = null,
    val targetMeanPrice: RawFmt? = null,
    val targetHighPrice: RawFmt? = null,
    val targetLowPrice: RawFmt? = null,
    val recommendationKey: String? = null,
    val debtToEquity: RawFmt? = null,
    val currentRatio: RawFmt? = null
)

@Serializable
data class AssetProfileModule(
    val sector: String? = null,
    val industry: String? = null,
    val fullTimeEmployees: Long? = null,
    val longBusinessSummary: String? = null
)

@Serializable
data class EarningsTrendModule(
    val trend: List<EarningsTrendPoint>? = null
)

@Serializable
data class EarningsTrendPoint(
    val period: String? = null,
    val growth: RawFmt? = null
)
