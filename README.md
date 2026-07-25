# StockScope

A native Android app (Kotlin + Jetpack Compose) that turns a ticker into the same
kind of report the `stock-analyzer` skill produces: fundamentals, technical
readings, a sector peer comparison, bear/base/bull/stretched-bull price targets,
and a 100-point conviction score.

## What it does

1. **Add a ticker** to your watchlist. Each row shows live price, day change,
   and conviction score.
2. **Tap a ticker** to open the full report:
   - **Chart** — TradingView's free embedded Advanced Chart widget.
   - **Technicals** — RSI(14), MACD(12,26,9), ADX(14) with +DI/-DI, a slow
     Stochastic(14,3,3), the SMA20/50/200 stack against the current price, and
     the most recent confirmed swing high/low (a 3-bar fractal pivot). All
     computed on-device from a year of daily OHLC bars pulled from Yahoo's
     chart endpoint — nothing scraped from the chart widget itself.
   - **Fundamentals** — price, market cap, trailing/forward P/E, EPS, revenue
     growth, margins, cash vs. debt, free cash flow, dividend yield, beta,
     52-week range, 50D/200D moving averages.
   - **Sector comparison** — the ticker ranked against 5 peers (from Yahoo's
     recommendation engine, falling back to a curated per-sector list) on
     forward P/E, margin, and growth.
   - **Asymmetry** — bear/base/bull/stretched-bull price targets computed as
     `anchor EPS × peer P/E multiple`, plus an entry zone, a trim zone, and a
     thesis-break level. All heuristic and clearly labeled as such — not a
     forecast.
   - **Conviction score** — 100 points split across valuation, growth,
     quality/margins, and balance-sheet strength, mapped to a tier (Strong
     Conviction / Buy / Hold-Watch / Avoid).
   - **Narrative** — the company's business summary plus recent headlines.

Data comes from Yahoo Finance's public, unauthenticated endpoints — no API key
required. These endpoints are undocumented and can change or rate-limit
without notice; every network response is optional/nullable in the app so a
missing field degrades gracefully instead of crashing.

## Architecture

- **UI**: Jetpack Compose + Material3, single-activity, Navigation-Compose
  between a watchlist screen and a detail screen.
- **State**: MVVM with `ViewModel` + `StateFlow`; a small hand-rolled
  `AppContainer` service locator instead of a DI framework (the app is small
  enough that Hilt would be pure overhead).
- **Networking**: Retrofit + OkHttp + kotlinx.serialization, hitting Yahoo
  Finance's `v8/finance/chart`, `v10/finance/quoteSummary`,
  `v1/finance/search`, and `v6/finance/recommendationsbysymbol` endpoints. A
  `CrumbManager` primes a session cookie and crumb token (the mechanism Yahoo
  has required since 2024) and an OkHttp `CookieJar` implementation uses
  `Cookie.matches()` for proper RFC 6265 domain matching across Yahoo's
  subdomains.
- **Persistence**: watchlist tickers are stored locally with Jetpack
  DataStore (Preferences) — no backend, no account.
- **Scoring**: `ScoringEngine` (in `domain/`) is pure and unit-testable —
  it turns fundamentals into the conviction score and asymmetry targets with
  no I/O. `TechnicalAnalysisEngine` is the same kind of pure, tested function:
  daily candles in, RSI/MACD/ADX/Stochastic/moving averages/swing points out.

## Building

Requires Android Studio (or the Android SDK + JDK 17) with `compileSdk 35`.

```
./gradlew assembleDebug
```

> This project was scaffolded and reviewed in an environment without network
> access to Google's Maven repository or an installed Android SDK, so the
> Gradle wrapper was generated and the code was carefully hand-reviewed for
> compile correctness, but `assembleDebug` itself has not been run. Build it
> once in Android Studio and fix anything that surfaces before shipping.

## Disclaimer

StockScope is an informational research tool, not financial advice. Data may
be delayed or incomplete — verify independently before trading.
