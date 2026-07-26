package com.kira.stockscope.data.network

import com.kira.stockscope.data.network.dto.ChartDto
import com.kira.stockscope.data.network.dto.QuoteSummaryDto
import com.kira.stockscope.data.network.dto.RecommendationsDto
import com.kira.stockscope.data.network.dto.SearchDto
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url

/**
 * Thin client over Yahoo Finance's unofficial public JSON endpoints.
 * These are undocumented and can change without notice; calls are best-effort
 * and every field in the response DTOs is nullable so a missing field degrades
 * gracefully instead of crashing the parser.
 */
interface YahooFinanceApi {

    @GET
    suspend fun getChart(
        @Url url: String,
        @Query("interval") interval: String = "1d",
        @Query("range") range: String = "6mo"
    ): ChartDto

    @GET
    suspend fun getQuoteSummary(
        @Url url: String,
        @Query("modules") modules: String,
        @Query("crumb") crumb: String? = null
    ): QuoteSummaryDto

    @GET
    suspend fun search(
        @Url url: String,
        @Query("q") query: String,
        @Query("newsCount") newsCount: Int = 8,
        @Query("quotesCount") quotesCount: Int = 6
    ): SearchDto

    @GET
    suspend fun recommendationsBySymbol(
        @Url url: String
    ): RecommendationsDto

    companion object {
        const val CHART_URL = "https://query1.finance.yahoo.com/v8/finance/chart/"
        const val QUOTE_SUMMARY_URL = "https://query2.finance.yahoo.com/v10/finance/quoteSummary/"
        const val SEARCH_URL = "https://query1.finance.yahoo.com/v1/finance/search"
        const val RECOMMENDATIONS_URL = "https://query2.finance.yahoo.com/v6/finance/recommendationsbysymbol/"

        const val QUOTE_SUMMARY_MODULES =
            "price,summaryDetail,defaultKeyStatistics,financialData,assetProfile,earningsTrend"
    }
}
