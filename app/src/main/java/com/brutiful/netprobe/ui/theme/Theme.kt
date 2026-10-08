package com.brutiful.netprobe.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val MatrixColorScheme = darkColorScheme(
    primary = MatrixGreen,
    onPrimary = MatrixBlack,
    secondary = MatrixGreenDark,
    onSecondary = MatrixBlack,
    tertiary = MatrixGreenSoft,
    background = MatrixBlack,
    onBackground = MatrixText,
    surface = MatrixSurface,
    onSurface = MatrixText,
    surfaceVariant = MatrixSurfaceAlt,
    onSurfaceVariant = MatrixTextMuted,
    error = MatrixError
)

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightSurface,
    secondary = LightSecondary,
    onSecondary = LightSurface,
    tertiary = LightTertiary,
    background = LightBackground,
    onBackground = LightText,
    surface = LightSurface,
    onSurface = LightText,
    surfaceVariant = LightSurfaceAlt,
    onSurfaceVariant = LightTextMuted,
    error = LightError
)

@Composable
fun NetProbeTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) MatrixColorScheme else LightColorScheme
    
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
