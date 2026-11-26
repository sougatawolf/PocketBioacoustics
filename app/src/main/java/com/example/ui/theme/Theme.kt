package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val BioacousticsDarkColorScheme = darkColorScheme(
    primary = CoralMic,
    onPrimary = Color.White,
    primaryContainer = CoralMicContainer,
    onPrimaryContainer = OnCoralMicContainer,
    secondary = PeachSand,
    onSecondary = Color(0xFF281608),
    secondaryContainer = PeachSandContainer,
    onSecondaryContainer = OnPeachSandContainer,
    tertiary = BotanicalTeal,
    onTertiary = Color(0xFF062025),
    tertiaryContainer = BotanicalTealContainer,
    onTertiaryContainer = OnBotanicalTealContainer,
    background = PineNightBg,
    onBackground = IvoryInk,
    surface = PineNightSurface,
    onSurface = IvoryInk,
    surfaceVariant = PineNightSurfaceHigh,
    onSurfaceVariant = SageMuted,
    surfaceContainerLowest = PineNightBg,
    surfaceContainerLow = PineNightSurfaceLow,
    surfaceContainer = PineNightSurface,
    surfaceContainerHigh = PineNightSurfaceHigh,
    surfaceContainerHighest = PineNightSurfaceHighest,
    outline = PineNightOutline,
    outlineVariant = PineNightOutlineSubtle,
    error = CoralMic,
    onError = Color.White,
    errorContainer = CoralMicContainer,
    onErrorContainer = OnCoralMicContainer
)

private val BioacousticsLightColorScheme = lightColorScheme(
    primary = CoralMicLight,
    onPrimary = Color.White,
    primaryContainer = CoralMicLightContainer,
    onPrimaryContainer = OnCoralMicLightContainer,
    secondary = WarmAmberLight,
    onSecondary = Color.White,
    secondaryContainer = WarmAmberLightContainer,
    onSecondaryContainer = OnWarmAmberLightContainer,
    tertiary = BotanicalTealLight,
    onTertiary = Color.White,
    tertiaryContainer = BotanicalTealLightContainer,
    onTertiaryContainer = OnBotanicalTealLightContainer,
    background = IvoryPaperBg,
    onBackground = PineInk,
    surface = IvoryPaperSurface,
    onSurface = PineInk,
    surfaceVariant = IvoryPaperHigh,
    onSurfaceVariant = PineMuted,
    surfaceContainerLowest = Color(0xFFFFFDFA),
    surfaceContainerLow = IvoryPaperBg,
    surfaceContainer = IvoryPaperSurface,
    surfaceContainerHigh = IvoryPaperHigh,
    surfaceContainerHighest = IvoryPaperHighest,
    outline = IvoryPaperOutline,
    outlineVariant = IvoryPaperOutlineSubtle,
    error = CoralMicLight,
    onError = Color.White,
    errorContainer = CoralMicLightContainer,
    onErrorContainer = OnCoralMicLightContainer
)

val WaveLoopShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun WaveLoopTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) BioacousticsDarkColorScheme else BioacousticsLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = WaveLoopTypography,
        shapes = WaveLoopShapes,
        content = content
    )
}
