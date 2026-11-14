package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

private fun colorSchemeForStyle(style: AppThemeStyle) = when (style) {
    AppThemeStyle.NEUMORPHISM -> darkColorScheme(
        primary = ElectricCyan,
        onPrimary = NeuBaseDark,
        primaryContainer = NeuSurfaceLight,
        onPrimaryContainer = ElectricCyan,
        secondary = EmeraldMint,
        onSecondary = NeuBaseDark,
        secondaryContainer = NeuSurfaceLight,
        onSecondaryContainer = EmeraldMint,
        tertiary = AmberWarning,
        onTertiary = NeuBaseDark,
        background = NeuBaseDark,
        onBackground = TextPrimary,
        surface = NeuSurfaceLight,
        onSurface = TextPrimary,
        surfaceVariant = NeuSurfaceLight,
        onSurfaceVariant = TextSecondary,
        outline = NeuHighlightEdge,
        outlineVariant = NeuShadowEdge,
        error = CrimsonPulse,
        onError = TextPrimary
    )
    AppThemeStyle.HOLOGRAPHIC_LIQUID_GLASS -> darkColorScheme(
        primary = ElectricCyan,
        onPrimary = DeepVoidBlack,
        primaryContainer = GlassSurfaceVariant,
        onPrimaryContainer = HoloViolet,
        secondary = HoloMagenta,
        onSecondary = TextPrimary,
        secondaryContainer = GlassSurfaceVariant,
        onSecondaryContainer = EmeraldMint,
        tertiary = HoloViolet,
        onTertiary = TextPrimary,
        background = DeepVoidBlack,
        onBackground = TextPrimary,
        surface = DarkSurfaceElevated,
        onSurface = TextPrimary,
        surfaceVariant = GlassSurface,
        onSurfaceVariant = TextSecondary,
        outline = GlassBorder,
        outlineVariant = Color(0x24FFFFFF),
        error = CrimsonPulse,
        onError = TextPrimary
    )
    AppThemeStyle.FROSTED_GLASSMORPHISM,
    AppThemeStyle.GLASS_PSD_MOCKUP,
    AppThemeStyle.CHROMATIC_FROSTED_LIQUID -> darkColorScheme(
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
}

@Composable
fun DnsPulseTheme(
    themeStyle: AppThemeStyle = AppThemeStyle.CHROMATIC_FROSTED_LIQUID,
    simpleMode: Boolean = false,
    content: @Composable () -> Unit
) {
    val effectiveStyle = if (simpleMode) AppThemeStyle.GLASS_PSD_MOCKUP else themeStyle
    CompositionLocalProvider(
        LocalAppThemeStyle provides effectiveStyle,
        LocalSimpleMode provides simpleMode
    ) {
        MaterialTheme(
            colorScheme = colorSchemeForStyle(effectiveStyle),
            typography = Typography,
            content = content
        )
    }
}
