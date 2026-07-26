package com.kira.stockscope.ui.detail

import android.annotation.SuppressLint
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Embeds TradingView's official, free "Advanced Chart" widget (the same one
 * generated at tradingview.com/widget) for the given ticker. TradingView has
 * no public data API for third-party apps, so this is display-only: all
 * fundamentals and scoring in the rest of the report still come from Yahoo
 * Finance. Taps on the widget's own links (e.g. "open in TradingView") are
 * routed to an external browser rather than navigated to inside this
 * fixed-height card.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TradingViewChartCard(symbol: String, darkTheme: Boolean) {
    val context = LocalContext.current
    val safeSymbol = remember(symbol) { sanitizeTradingViewSymbol(symbol) }
    val html = remember(safeSymbol, darkTheme) { buildTradingViewWidgetHtml(safeSymbol, darkTheme) }

    // AndroidView<View> rather than <WebView>: constructing a WebView can throw on
    // devices with no working WebView provider installed (a real, documented
    // Android failure mode, not hypothetical) — runCatching here means that takes
    // down just this chart card instead of crashing the whole detail screen.
    // The availability check comes first because a *missing* WebView provider can
    // fail at the native layer below what a JVM try/catch can intercept at all;
    // checking first avoids ever calling the WebView constructor on such a device.
    AndroidView(
        modifier = Modifier.fillMaxWidth().height(420.dp),
        factory = { ctx ->
            if (runCatching { WebView.getCurrentWebViewPackage() }.getOrNull() == null) {
                return@AndroidView FrameLayout(ctx)
            }
            runCatching {
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest
                        ): Boolean {
                            // The initial load goes through loadDataWithBaseURL and never hits
                            // this callback; anything that does is a real navigation (a link
                            // tapped inside the widget), so send it to an external browser
                            // instead of breaking out of the fixed-height chart card.
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, request.url)) }
                            return true
                        }
                    }
                    loadDataWithBaseURL("https://s.tradingview.com", html, "text/html", "utf-8", null)
                    tag = html
                }
            }.getOrElse { FrameLayout(ctx) }
        },
        update = { view ->
            val webView = view as? WebView ?: return@AndroidView
            if (webView.tag != html) {
                webView.loadDataWithBaseURL("https://s.tradingview.com", html, "text/html", "utf-8", null)
                webView.tag = html
            }
        },
        onRelease = { (it as? WebView)?.destroy() }
    )
}

private fun sanitizeTradingViewSymbol(raw: String): String {
    val cleaned = raw.uppercase().filter { it.isLetterOrDigit() || it == '.' || it == ':' || it == '-' }
    return cleaned.ifBlank { "AAPL" }
}

private fun buildTradingViewWidgetHtml(symbol: String, darkTheme: Boolean): String {
    val theme = if (darkTheme) "dark" else "light"
    return """
        <!DOCTYPE html>
        <html>
        <head>
          <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
          <style>html,body{margin:0;padding:0;height:100%;background:transparent;}</style>
        </head>
        <body>
          <div class="tradingview-widget-container" style="height:100%;width:100%">
            <div id="tv_chart" style="height:100%;width:100%"></div>
            <script src="https://s3.tradingview.com/tv.js"></script>
            <script>
              new TradingView.widget({
                "autosize": true,
                "symbol": "$symbol",
                "interval": "D",
                "timezone": "Etc/UTC",
                "theme": "$theme",
                "style": "1",
                "locale": "en",
                "enable_publishing": false,
                "allow_symbol_change": true,
                "hide_top_toolbar": false,
                "hide_legend": false,
                "save_image": false,
                "container_id": "tv_chart"
              });
            </script>
          </div>
        </body>
        </html>
    """.trimIndent()
}
