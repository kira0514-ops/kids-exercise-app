package com.kira.stockscope.data.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class SearchDto(
    val quotes: List<SearchQuote>? = null,
    val news: List<SearchNews>? = null
)

@Serializable
data class SearchQuote(
    val symbol: String? = null,
    val shortname: String? = null,
    val longname: String? = null,
    val quoteType: String? = null,
    val exchDisp: String? = null
)

@Serializable
data class SearchNews(
    val title: String? = null,
    val publisher: String? = null,
    val link: String? = null,
    val providerPublishTime: Long? = null
)
