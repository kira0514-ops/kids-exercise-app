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
 * work for the chart endpoint — but every call retries the exchange until it
 * actually succeeds once. A single early failure (e.g. the network not being up
 * yet right after app launch) must not permanently disable the crumb for the rest
 * of the process's life, which is what happened when this only tried once ever.
 */
class CrumbManager(private val client: OkHttpClient) {

    private val mutex = Mutex()
    private var cachedCrumb: String? = null

    /** What happened on the most recent exchange attempt, surfaced by callers when [getCrumb] returns null. */
    var lastAttemptDebug: String? = null
        private set

    suspend fun getCrumb(): String? = mutex.withLock {
        if (cachedCrumb == null) {
            primeCookieAndCrumb()
        }
        cachedCrumb
    }

    private suspend fun primeCookieAndCrumb() = withContext(Dispatchers.IO) {
        val debug = StringBuilder()

        runCatching {
            client.newCall(
                Request.Builder().url("https://fc.yahoo.com").header("Accept", "*/*").build()
            ).execute().use {
                debug.append("fc_http=${it.code}")
            }
        }.onFailure { e ->
            debug.append("fc_err=${e.javaClass.simpleName}:${e.message}")
        }

        runCatching {
            client.newCall(
                // getcrumb returns a bare text/plain crumb, not JSON; explicitly overriding
                // the shared client's default Accept: application/json here is what makes
                // Yahoo actually return the crumb instead of a 406.
                Request.Builder()
                    .url("https://query2.finance.yahoo.com/v1/test/getcrumb")
                    .header("Accept", "*/*")
                    .build()
            ).execute()
        }.onFailure { e ->
            debug.append(" getcrumb_err=${e.javaClass.simpleName}:${e.message}")
        }.getOrNull()?.use { response ->
            debug.append(" getcrumb_http=${response.code}")
            if (response.isSuccessful) {
                val crumb = response.body?.string()?.trim()
                when {
                    crumb.isNullOrBlank() -> debug.append(" body=blank")
                    crumb.contains("<html", ignoreCase = true) -> debug.append(" body=html_page")
                    else -> {
                        cachedCrumb = crumb
                        debug.append(" body_len=${crumb.length}")
                    }
                }
            } else {
                debug.append(" body=${response.body?.string()?.take(200)}")
            }
        }

        lastAttemptDebug = debug.toString()
    }
}
