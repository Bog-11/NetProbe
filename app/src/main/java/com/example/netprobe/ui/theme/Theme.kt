package com.example.netprobe.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
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

@Composable
fun NetProbeTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = MatrixColorScheme,
        typography = AppTypography,
        content = content
    )
}