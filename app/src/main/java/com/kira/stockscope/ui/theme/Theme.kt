package com.kira.stockscope.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val BullGreen = Color(0xFF22C55E)
val BearRed = Color(0xFFEF4444)
val NeutralAmber = Color(0xFFF59E0B)
val BrandBlue = Color(0xFF2563EB)

private val DarkColors = darkColorScheme(
    primary = BrandBlue,
    secondary = BullGreen,
    background = Color(0xFF0B1220),
    surface = Color(0xFF111A2C),
    error = BearRed
)

private val LightColors = lightColorScheme(
    primary = BrandBlue,
    secondary = BullGreen,
    background = Color(0xFFF7F9FC),
    surface = Color.White,
    error = BearRed
)

@Composable
fun StockScopeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        content = content
    )
}
