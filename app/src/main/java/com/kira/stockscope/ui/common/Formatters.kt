package com.kira.stockscope.ui.common

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

object Formatters {

    fun price(value: Double?, currency: String? = null): String {
        if (value == null) return "--"
        val symbol = when (currency) {
            "USD", null -> "$"
            else -> "$currency "
        }
        return "$symbol%.2f".format(value)
    }

    fun percent(value: Double?, alreadyPercentScale: Boolean = false): String {
        if (value == null) return "--"
        val pct = if (alreadyPercentScale) value else value * 100
        val sign = if (pct >= 0) "+" else ""
        return "$sign%.1f%%".format(pct)
    }

    fun compactNumber(value: Double?): String {
        if (value == null) return "--"
        val abs = abs(value)
        return when {
            abs >= 1_000_000_000_000.0 -> "%.2fT".format(value / 1_000_000_000_000.0)
            abs >= 1_000_000_000.0 -> "%.2fB".format(value / 1_000_000_000.0)
            abs >= 1_000_000.0 -> "%.2fM".format(value / 1_000_000.0)
            abs >= 1_000.0 -> "%.2fK".format(value / 1_000.0)
            else -> "%.2f".format(value)
        }
    }

    fun ratio(value: Double?, suffix: String = "x"): String {
        if (value == null) return "--"
        return "%.1f$suffix".format(value)
    }

    fun timeAgo(epochSeconds: Long?): String {
        if (epochSeconds == null) return ""
        val sdf = SimpleDateFormat("MMM d", Locale.US)
        return sdf.format(Date(epochSeconds * 1000))
    }
}
