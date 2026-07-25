package com.kira.stockscope.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Yahoo Finance requires a session cookie plus a matching "crumb" token for several
 * endpoints. This primes a cookie against fc.yahoo.com, then exchanges it for a
 * crumb via the getcrumb endpoint, and caches the result in memory. Best-effort:
 * if either step fails, callers fall back to unauthenticated requests, which still
 * work for the chart endpoint.
 */
class CrumbManager(private val client: OkHttpClient) {

    private val mutex = Mutex()
    private var cachedCrumb: String? = null
    private var primed = false

    suspend fun getCrumb(): String? = mutex.withLock {
        if (!primed) {
            primeCookieAndCrumb()
            primed = true
        }
        cachedCrumb
    }

    private suspend fun primeCookieAndCrumb() = withContext(Dispatchers.IO) {
        runCatching {
            client.newCall(Request.Builder().url("https://fc.yahoo.com").build()).execute().close()
        }
        runCatching {
            val response = client.newCall(
                Request.Builder().url("https://query2.finance.yahoo.com/v1/test/getcrumb").build()
            ).execute()
            response.use {
                if (it.isSuccessful) {
                    val crumb = it.body?.string()?.trim()
                    if (!crumb.isNullOrBlank() && !crumb.contains("<html", ignoreCase = true)) {
                        cachedCrumb = crumb
                    }
                }
            }
        }
    }
}
