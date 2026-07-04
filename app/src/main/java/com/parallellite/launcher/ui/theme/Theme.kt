package com.parallellite.launcher.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val DarkColors = darkColorScheme(
    primary = BrandColors.MarioGold,
    onPrimary = androidx.compose.ui.graphics.Color.Black,
    secondary = BrandColors.MarioOrange,
    background = BrandColors.Background,
    onBackground = BrandColors.TextPrimary,
    surface = BrandColors.Surface,
    onSurface = BrandColors.TextPrimary,
    surfaceVariant = BrandColors.SurfaceVariant,
    outline = BrandColors.Outline,
    error = BrandColors.Error,
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
)

@Composable
fun ParallelLiteTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
