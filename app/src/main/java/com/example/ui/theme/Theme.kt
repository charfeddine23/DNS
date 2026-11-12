package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = DeepVoidBlack,
    primaryContainer = GlassSurfaceVariant,
    onPrimaryContainer = ElectricCyan,
    secondary = EmeraldMint,
    onSecondary = DeepVoidBlack,
    secondaryContainer = GlassSurfaceVariant,
    onSecondaryContainer = EmeraldMint,
    tertiary = AmberWarning,
    onTertiary = DeepVoidBlack,
    background = DeepVoidBlack,
    onBackground = TextPrimary,
    surface = DarkSurfaceElevated,
    onSurface = TextPrimary,
    surfaceVariant = GlassSurface,
    onSurfaceVariant = TextSecondary,
    outline = GlassBorder,
    outlineVariant = Color(0x14FFFFFF),
    error = CrimsonPulse,
    onError = TextPrimary
)

@Composable
fun DnsPulseTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
