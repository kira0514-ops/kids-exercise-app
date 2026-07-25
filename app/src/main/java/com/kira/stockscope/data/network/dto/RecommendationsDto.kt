package com.kira.stockscope.data.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class RecommendationsDto(
    val finance: RecommendationsFinance
)

@Serializable
data class RecommendationsFinance(
    val result: List<RecommendationsResult>? = null,
    val error: ChartError? = null
)

@Serializable
data class RecommendationsResult(
    val symbol: String? = null,
    val recommendedSymbols: List<RecommendedSymbol>? = null
)

@Serializable
data class RecommendedSymbol(
    val symbol: String? = null,
    val score: Double? = null
)
