package com.example.iptvarmadillo.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Sand,
    onPrimary = SandInk,
    primaryContainer = Color(0xFF3A3018),
    onPrimaryContainer = Sand,
    secondary = Moss,
    onSecondary = Night,
    background = Night,
    onBackground = Ink,
    surface = NightSurface,
    onSurface = Ink,
    surfaceVariant = NightCard,
    onSurfaceVariant = Muted,
    outline = Line,
    error = Danger,
    onError = SandInk,
)

@Composable
fun IPTVArmadilloTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content,
    )
}
