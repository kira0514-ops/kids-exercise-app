package com.kira.stockscope.data.network

import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

object NetworkModule {

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/128.0.0.0 Mobile Safari/537.36"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /**
     * All cookies in one pool, filtered per-request with [Cookie.matches], which implements
     * RFC 6265 domain matching. That's what lets a cookie set on fc.yahoo.com (with a
     * Domain=.yahoo.com attribute) also get sent to query2.finance.yahoo.com for the crumb
     * exchange; a plain per-host map (matching exact hostnames) would not do that.
     */
    private val inMemoryCookieJar = object : CookieJar {
        private val cookies = mutableListOf<Cookie>()

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            synchronized(this) {
                for (cookie in cookies) {
                    this.cookies.removeAll { it.name == cookie.name && it.domain == cookie.domain }
                    this.cookies.add(cookie)
                }
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> = synchronized(this) {
            val now = System.currentTimeMillis()
            cookies.removeAll { it.expiresAt <= now }
            cookies.filter { it.matches(url) }
        }
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .cookieJar(inMemoryCookieJar)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .build()
                chain.proceed(request)
            }
            .addInterceptor(loggingInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    val crumbManager: CrumbManager by lazy { CrumbManager(okHttpClient) }

    val api: YahooFinanceApi by lazy {
        val contentType = "application/json".toMediaType()
        Retrofit.Builder()
            .baseUrl("https://query1.finance.yahoo.com/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(YahooFinanceApi::class.java)
    }
}
