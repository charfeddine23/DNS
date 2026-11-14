package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private fun colorSchemeForPalette(palette: PulsePalette) = if (palette.isDark) {
    darkColorScheme(
        primary = palette.primaryAccent,
        onPrimary = palette.onAccent,
        primaryContainer = palette.glassSurfaceVariant,
        onPrimaryContainer = palette.primaryAccent,
        secondary = palette.emeraldAccent,
        onSecondary = palette.onAccent,
        secondaryContainer = palette.glassSurfaceVariant,
        onSecondaryContainer = palette.emeraldAccent,
        tertiary = palette.tertiaryAccent,
        onTertiary = palette.onAccent,
        background = palette.background,
        onBackground = palette.textPrimary,
        surface = palette.surfaceElevated,
        onSurface = palette.textPrimary,
        surfaceVariant = palette.glassSurface,
        onSurfaceVariant = palette.textSecondary,
        outline = palette.glassBorder,
        outlineVariant = palette.subtleBorder,
        error = palette.crimsonAccent,
        onError = palette.textPrimary
    )
} else {
    lightColorScheme(
        primary = palette.primaryAccent,
        onPrimary = palette.onAccent,
        primaryContainer = palette.glassSurfaceVariant,
        onPrimaryContainer = palette.primaryAccent,
        secondary = palette.emeraldAccent,
        onSecondary = palette.onAccent,
        secondaryContainer = palette.glassSurfaceVariant,
        onSecondaryContainer = palette.emeraldAccent,
        tertiary = palette.tertiaryAccent,
        onTertiary = palette.onAccent,
        background = palette.background,
        onBackground = palette.textPrimary,
        surface = palette.surfaceElevated,
        onSurface = palette.textPrimary,
        surfaceVariant = palette.glassSurface,
        onSurfaceVariant = palette.textSecondary,
        outline = palette.glassBorder,
        outlineVariant = palette.subtleBorder,
        error = palette.crimsonAccent,
        onError = palette.onAccent
    )
}

@Composable
fun DnsPulseTheme(
    themeStyle: AppThemeStyle = AppThemeStyle.CHROMATIC_FROSTED_LIQUID,
    appearanceMode: AppAppearanceMode = AppAppearanceMode.DARK,
    simpleMode: Boolean = false,
    content: @Composable () -> Unit
) {
    val isDark = when (appearanceMode) {
        AppAppearanceMode.DARK -> true
        AppAppearanceMode.LIGHT -> false
        AppAppearanceMode.SYSTEM -> isSystemInDarkTheme()
    }
    val effectiveStyle = if (simpleMode) AppThemeStyle.GLASS_PSD_MOCKUP else themeStyle
    val palette = paletteForTheme(effectiveStyle, isDark = isDark)

    CompositionLocalProvider(
        LocalAppThemeStyle provides effectiveStyle,
        LocalSimpleMode provides simpleMode,
        LocalIsDarkTheme provides isDark,
        LocalPulsePalette provides palette
    ) {
        MaterialTheme(
            colorScheme = colorSchemeForPalette(palette),
            typography = Typography,
            content = content
        )
    }
}
