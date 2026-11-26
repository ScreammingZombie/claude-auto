package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val ClaudeAutoRunColorScheme = darkColorScheme(
    primary = ClaudeCoral,
    onPrimary = ObsidianBg,
    primaryContainer = ClaudeCoralContainer,
    onPrimaryContainer = ClaudeCoralOnContainer,
    secondary = TechCyan,
    onSecondary = ObsidianBg,
    secondaryContainer = TechCyanContainer,
    onSecondaryContainer = TechCyanOnContainer,
    tertiary = EmeraldSuccess,
    onTertiary = ObsidianBg,
    tertiaryContainer = EmeraldContainer,
    onTertiaryContainer = EmeraldSuccess,
    error = CoralError,
    onError = ObsidianBg,
    errorContainer = CoralErrorContainer,
    onErrorContainer = CoralError,
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = SlateSurface,
    onSurface = TextPrimary,
    surfaceVariant = SlateSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = ObsidianBg,
    surfaceContainerLow = SlateSurface,
    surfaceContainer = SlateSurfaceElevated,
    surfaceContainerHigh = SlateSurfaceHigh,
    outline = SlateOutline,
    outlineVariant = SlateOutlineSubtle,
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun ClaudeAutoRunTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = ClaudeAutoRunColorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
