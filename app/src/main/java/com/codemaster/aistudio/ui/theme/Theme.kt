package com.codemaster.aistudio.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Dark theme - GitHub-inspired dark with blue/orange accents
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF58A6FF),          // Blue accent
    onPrimary = Color(0xFF0D1117),
    primaryContainer = Color(0xFF1F3A5C),
    onPrimaryContainer = Color(0xFFADD8FF),
    secondary = Color(0xFFFF8C42),        // Orange accent
    onSecondary = Color(0xFF1A0800),
    secondaryContainer = Color(0xFF5C2800),
    onSecondaryContainer = Color(0xFFFFDBCC),
    tertiary = Color(0xFF3FB950),         // Green for success/run
    background = Color(0xFF0D1117),
    onBackground = Color(0xFFE6EDF3),
    surface = Color(0xFF161B22),
    onSurface = Color(0xFFE6EDF3),
    surfaceVariant = Color(0xFF21262D),
    onSurfaceVariant = Color(0xFF8B949E),
    outline = Color(0xFF30363D),
    error = Color(0xFFF85149),
    onError = Color(0xFF1A0000),
)

// Light theme
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF0969DA),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDEEFFF),
    onPrimaryContainer = Color(0xFF001D36),
    secondary = Color(0xFFE36200),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDCC8),
    onSecondaryContainer = Color(0xFF2D1500),
    tertiary = Color(0xFF1A7F37),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF1F2328),
    surface = Color(0xFFF6F8FA),
    onSurface = Color(0xFF1F2328),
    surfaceVariant = Color(0xFFEAEEF2),
    onSurfaceVariant = Color(0xFF57606A),
    outline = Color(0xFFD0D7DE),
    error = Color(0xFFCF222E),
    onError = Color(0xFFFFFFFF),
)

@Composable
fun CodeMasterTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = CodeMasterTypography,
        content = content
    )
}
