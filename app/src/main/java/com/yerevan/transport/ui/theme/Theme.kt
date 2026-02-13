package com.yerevan.transport.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF0057B8),
    secondary = Color(0xFFFFD700),
    background = Color(0xFFF7F9FC),
    surface = Color(0xFFFFFFFF),
    onPrimary = Color.White
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7EB2FF),
    secondary = Color(0xFFFFE36E)
)

@Composable
fun YerevanTransportTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = MaterialTheme.typography,
        content = content
    )
}
