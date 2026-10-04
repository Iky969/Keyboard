package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val TerminalColorScheme = darkColorScheme(
    primary = TerminalCyan,
    onPrimary = Color(0xFF003844),
    primaryContainer = Color(0xFF004D5C),
    onPrimaryContainer = Color(0xFF70F5FF),
    secondary = TerminalGreen,
    onSecondary = Color(0xFF00391A),
    secondaryContainer = Color(0xFF005327),
    onSecondaryContainer = Color(0xFF6BFF9E),
    tertiary = TerminalAmber,
    onTertiary = Color(0xFF452B00),
    background = TerminalBackground,
    onBackground = TextPrimary,
    surface = TerminalSurface,
    onSurface = TextPrimary,
    surfaceVariant = TerminalSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = TerminalBorder
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Terminal apps look best with intentional dark cyberpunk theme
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = TerminalColorScheme,
        typography = Typography,
        content = content
    )
}
