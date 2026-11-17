package com.hydrachat.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val lightColors = lightColorScheme(
    primary = Color(0xFF1E88E5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6EBFF),
    onPrimaryContainer = Color(0xFF06315C),
    secondary = Color(0xFF00897B),
    secondaryContainer = Color(0xFFD3F2EC),
    onSecondaryContainer = Color(0xFF00382F),
    tertiary = Color(0xFFF57C00),
    background = Color(0xFFF6FAFE),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE6EEF6),
)

private val darkColors = darkColorScheme(
    primary = Color(0xFF8CC8FF),
    onPrimary = Color(0xFF00325A),
    primaryContainer = Color(0xFF12456F),
    onPrimaryContainer = Color(0xFFD6EBFF),
    secondary = Color(0xFF7FD6C8),
    secondaryContainer = Color(0xFF0F4F46),
    onSecondaryContainer = Color(0xFFD3F2EC),
    tertiary = Color(0xFFFFB866),
    background = Color(0xFF0F1419),
    surface = Color(0xFF171D24),
    surfaceVariant = Color(0xFF263039),
)

@Composable
fun HydraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) darkColors else lightColors,
        content = content,
    )
}
