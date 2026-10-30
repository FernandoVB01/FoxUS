package com.foxus.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val FoxusDarkColorScheme = darkColorScheme(
    primary = FoxusTokens.FoxOrange,
    onPrimary = FoxusTokens.DarkSurface,
    primaryContainer = FoxusTokens.DeepNavy,
    onPrimaryContainer = FoxusTokens.WarmCream,
    secondary = FoxusTokens.FoxGold,
    onSecondary = FoxusTokens.DarkSurface,
    secondaryContainer = FoxusTokens.SurfaceElevated,
    onSecondaryContainer = FoxusTokens.WarmCream,
    tertiary = FoxusTokens.SlateBlue,
    background = FoxusTokens.DarkSurface,
    onBackground = FoxusTokens.TextPrimary,
    surface = FoxusTokens.SurfaceCard,
    onSurface = FoxusTokens.TextPrimary,
    surfaceVariant = FoxusTokens.SurfaceElevated,
    onSurfaceVariant = FoxusTokens.TextSecondary,
    outline = FoxusTokens.BorderSubtle,
    error = FoxusTokens.ErrorCoral
)

@Composable
fun FoxusTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = FoxusDarkColorScheme,
        typography = FoxusTypography,
        content = content
    )
}
