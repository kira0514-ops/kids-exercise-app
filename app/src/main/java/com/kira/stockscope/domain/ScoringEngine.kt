package com.kira.stockscope.domain

import com.kira.stockscope.model.AsymmetryTargets
import com.kira.stockscope.model.ConvictionScore
import com.kira.stockscope.model.Fundamentals
import com.kira.stockscope.model.SectorComparison
import com.kira.stockscope.model.StockSnapshot

/**
 * Heuristic scoring shared by the "asymmetry" and "conviction score" passes of the
 * report. All formulas are intentionally simple and transparent rather than
 * predictive: they turn observable fundamentals into a comparable, explainable
 * signal, not a forecast.
 */
object ScoringEngine {

    fun score(fundamentals: Fundamentals, sector: SectorComparison, snapshot: StockSnapshot): ConvictionScore {
        return ConvictionScore(
            valuationScore = valuationScore(fundamentals, sector),
            growthScore = growthScore(fundamentals),
            qualityScore = qualityScore(fundamentals),
            balanceSheetScore = balanceSheetScore(fundamentals, snapshot)
        )
    }

    private fun valuationScore(f: Fundamentals, sector: SectorComparison): Int {
        val stockPe = f.forwardPe ?: f.trailingPe ?: return 12
        val peerAvg = sector.peerAverageForwardPe ?: sector.peerAverageTrailingPe ?: return 12
        if (peerAvg <= 0 || stockPe <= 0) return 12
        val ratio = stockPe / peerAvg
        return when {
            ratio <= 0.7 -> 25
            ratio <= 0.85 -> 20
            ratio <= 1.0 -> 15
            ratio <= 1.2 -> 10
            ratio <= 1.5 -> 5
            else -> 0
        }
    }

    private fun growthScore(f: Fundamentals): Int {
        val candidates = listOfNotNull(f.revenueGrowth, f.nextYearGrowthEstimate, f.earningsGrowth)
        if (candidates.isEmpty()) return 12
        val avgGrowth = candidates.average()
        return when {
            avgGrowth >= 0.25 -> 25
            avgGrowth >= 0.15 -> 20
            avgGrowth >= 0.08 -> 15
            avgGrowth >= 0.03 -> 10
            avgGrowth >= 0.0 -> 5
            else -> 0
        }
    }

    private fun qualityScore(f: Fundamentals): Int {
        val margins = listOfNotNull(f.profitMargin, f.operatingMargin)
        if (margins.isEmpty()) return 12
        val avgMargin = margins.average()
        return when {
            avgMargin >= 0.20 -> 25
            avgMargin >= 0.12 -> 20
            avgMargin >= 0.06 -> 15
            avgMargin >= 0.0 -> 8
            else -> 0
        }
    }

    private fun balanceSheetScore(f: Fundamentals, snapshot: StockSnapshot): Int {
        val marketCap = snapshot.marketCap
        val netCash = f.netCash
        if (netCash != null && marketCap != null && marketCap > 0) {
            val netCashRatio = netCash / marketCap
            return when {
                netCashRatio >= 0.10 -> 25
                netCashRatio >= 0.0 -> 20
                netCashRatio >= -0.15 -> 12
                netCashRatio >= -0.35 -> 6
                else -> 0
            }
        }
        val dte = f.debtToEquity ?: return 12
        return when {
            dte < 30 -> 25
            dte < 60 -> 20
            dte < 100 -> 15
            dte < 200 -> 8
            else -> 0
        }
    }

    fun asymmetryTargets(
        snapshot: StockSnapshot,
        fundamentals: Fundamentals,
        sector: SectorComparison
    ): AsymmetryTargets {
        val price = snapshot.price
        val eps = fundamentals.forwardEps ?: fundamentals.trailingEps
        val avgMultiple = sector.peerAverageForwardPe ?: sector.peerAverageTrailingPe
            ?: fundamentals.forwardPe ?: fundamentals.trailingPe
        val lowMultiple = sector.peerLowForwardPe ?: avgMultiple?.times(0.75)
        val highMultiple = sector.peerHighForwardPe ?: avgMultiple?.times(1.35)

        val bear = if (eps != null && eps > 0 && lowMultiple != null) eps * lowMultiple * 0.85 else null
        val base = if (eps != null && eps > 0 && avgMultiple != null) eps * avgMultiple else null
        val bull = if (eps != null && eps > 0 && highMultiple != null) eps * highMultiple else null
        val stretchedBull = bull?.times(1.25)

        val entryCandidate = listOfNotNull(
            price?.times(0.93),
            fundamentals.fiftyDayAverage
        ).minOrNull()

        val trim = if (base != null && bull != null) (base + bull) / 2 else bull ?: base

        val thesisBreak = listOfNotNull(
            fundamentals.fiftyTwoWeekLow,
            fundamentals.twoHundredDayAverage?.times(0.95),
            price?.times(0.78)
        ).minOrNull()

        return AsymmetryTargets(
            currentPrice = price,
            bear = bear,
            base = base,
            bull = bull,
            stretchedBull = stretchedBull,
            entryZone = entryCandidate,
            trimZone = trim,
            thesisBreak = thesisBreak,
            anchorMultiple = avgMultiple,
            anchorEps = eps
        )
    }
}
