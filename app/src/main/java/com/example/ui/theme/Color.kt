package com.example.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class PulsePalette(
    val isDark: Boolean,
    val background: Color,
    val surfaceElevated: Color,
    val glassSurface: Color,
    val glassSurfaceVariant: Color,
    val stackCardFront: Color,
    val stackCardBack: Color,
    val glassBorder: Color,
    val glassBorderLuminous: Color,
    val glassTopSpecular: Color,
    val neuBase: Color,
    val neuSurfaceLight: Color,
    val neuHighlightEdge: Color,
    val neuShadowEdge: Color,
    val primaryAccent: Color,
    val primaryAccentGlow: Color,
    val emeraldAccent: Color,
    val emeraldAccentGlow: Color,
    val crimsonAccent: Color,
    val crimsonAccentGlow: Color,
    val amberAccent: Color,
    val tertiaryAccent: Color,
    val secondaryGlowAccent: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val onAccent: Color,
    val subtleFill: Color,
    val subtleBorder: Color,
    val frostedTint: Color,
    val frostedBorder: Color
)

fun paletteForTheme(style: AppThemeStyle, isDark: Boolean): PulsePalette {
    if (!isDark) {
        return lightPaletteForTheme(style)
    }
    return when (style) {
        AppThemeStyle.CHROMATIC_FROSTED_LIQUID,
        AppThemeStyle.HOLOGRAPHIC_LIQUID_GLASS,
        AppThemeStyle.FROSTED_GLASSMORPHISM,
        AppThemeStyle.GLASS_PSD_MOCKUP -> PulsePalette(
            isDark = true,
            background = Color(0xFF06080D),
            surfaceElevated = Color(0xFF0C1018),
            glassSurface = Color(0xCC131924),
            glassSurfaceVariant = Color(0xE61B2332),
            stackCardFront = Color(0xFF141B29),
            stackCardBack = Color(0xFF0E131D),
            glassBorder = Color(0x24FFFFFF),
            glassBorderLuminous = Color(0x4D00E5FF),
            glassTopSpecular = Color(0x33FFFFFF),
            neuBase = Color(0xFF171B24),
            neuSurfaceLight = Color(0xFF1F2430),
            neuHighlightEdge = Color(0xFF2B3242),
            neuShadowEdge = Color(0xFF0A0C12),
            primaryAccent = Color(0xFF00E5FF),
            primaryAccentGlow = Color(0x4D00E5FF),
            emeraldAccent = Color(0xFF00E676),
            emeraldAccentGlow = Color(0x4D00E676),
            crimsonAccent = Color(0xFFFF5252),
            crimsonAccentGlow = Color(0x4DFF5252),
            amberAccent = Color(0xFFFFB300),
            tertiaryAccent = Color(0xFF9D4EDD),
            secondaryGlowAccent = Color(0xFFFF2E93),
            textPrimary = Color(0xFFF0F6FC),
            textSecondary = Color(0xFF8B949E),
            textTertiary = Color(0xFF6E7681),
            onAccent = Color(0xFF06080D),
            subtleFill = Color.White.copy(alpha = 0.08f),
            subtleBorder = Color.White.copy(alpha = 0.14f),
            frostedTint = Color(0x36DCEEFF),
            frostedBorder = Color(0x52E6F4FF)
        )

        AppThemeStyle.NEUMORPHISM -> PulsePalette(
            isDark = true,
            background = Color(0xFF171B24),
            surfaceElevated = Color(0xFF1C212C),
            glassSurface = Color(0xFF1F2430),
            glassSurfaceVariant = Color(0xFF252B39),
            stackCardFront = Color(0xFF222835),
            stackCardBack = Color(0xFF191E28),
            glassBorder = Color(0xFF2B3242),
            glassBorderLuminous = Color(0x4D00E5FF),
            glassTopSpecular = Color(0x29FFFFFF),
            neuBase = Color(0xFF171B24),
            neuSurfaceLight = Color(0xFF1F2430),
            neuHighlightEdge = Color(0xFF2B3242),
            neuShadowEdge = Color(0xFF0A0C12),
            primaryAccent = Color(0xFF00E5FF),
            primaryAccentGlow = Color(0x4D00E5FF),
            emeraldAccent = Color(0xFF00E676),
            emeraldAccentGlow = Color(0x4D00E676),
            crimsonAccent = Color(0xFFFF5252),
            crimsonAccentGlow = Color(0x4DFF5252),
            amberAccent = Color(0xFFFFB300),
            tertiaryAccent = Color(0xFF9D4EDD),
            secondaryGlowAccent = Color(0xFF00E5FF),
            textPrimary = Color(0xFFF0F6FC),
            textSecondary = Color(0xFF8B949E),
            textTertiary = Color(0xFF6E7681),
            onAccent = Color(0xFF06080D),
            subtleFill = Color.White.copy(alpha = 0.07f),
            subtleBorder = Color.White.copy(alpha = 0.12f),
            frostedTint = Color(0x2ADCEEFF),
            frostedBorder = Color(0x44E6F4FF)
        )

        AppThemeStyle.TOKYO_NIGHT -> PulsePalette(
            isDark = true,
            background = Color(0xFF13141E),
            surfaceElevated = Color(0xFF1A1B26),
            glassSurface = Color(0xCC1E2030),
            glassSurfaceVariant = Color(0xE624283B),
            stackCardFront = Color(0xFF1F2335),
            stackCardBack = Color(0xFF161824),
            glassBorder = Color(0x337AA2F7),
            glassBorderLuminous = Color(0x667DCFFF),
            glassTopSpecular = Color(0x387AA2F7),
            neuBase = Color(0xFF1A1B26),
            neuSurfaceLight = Color(0xFF24283B),
            neuHighlightEdge = Color(0xFF3B4261),
            neuShadowEdge = Color(0xFF0C0D14),
            primaryAccent = Color(0xFF7DCFFF),
            primaryAccentGlow = Color(0x4D7DCFFF),
            emeraldAccent = Color(0xFF73DACA),
            emeraldAccentGlow = Color(0x4D73DACA),
            crimsonAccent = Color(0xFFF7768E),
            crimsonAccentGlow = Color(0x4DF7768E),
            amberAccent = Color(0xFFE0AF68),
            tertiaryAccent = Color(0xFFBB9AF7),
            secondaryGlowAccent = Color(0xFF7AA2F7),
            textPrimary = Color(0xFFC0CAF5),
            textSecondary = Color(0xFF9AA5CE),
            textTertiary = Color(0xFF565F89),
            onAccent = Color(0xFF0B0D14),
            subtleFill = Color(0xFF7AA2F7).copy(alpha = 0.10f),
            subtleBorder = Color(0xFF7AA2F7).copy(alpha = 0.24f),
            frostedTint = Color(0x2E7AA2F7),
            frostedBorder = Color(0x527DCFFF)
        )

        AppThemeStyle.CATPPUCCIN_MOCHA -> PulsePalette(
            isDark = true,
            background = Color(0xFF181825),
            surfaceElevated = Color(0xFF1E1E2E),
            glassSurface = Color(0xCC26263A),
            glassSurfaceVariant = Color(0xE6313244),
            stackCardFront = Color(0xFF28283D),
            stackCardBack = Color(0xFF1E1E2E),
            glassBorder = Color(0x30CBA6F7),
            glassBorderLuminous = Color(0x6689B4FA),
            glassTopSpecular = Color(0x33F5E0DC),
            neuBase = Color(0xFF1E1E2E),
            neuSurfaceLight = Color(0xFF29293E),
            neuHighlightEdge = Color(0xFF45475A),
            neuShadowEdge = Color(0xFF11111B),
            primaryAccent = Color(0xFF89B4FA),
            primaryAccentGlow = Color(0x4D89B4FA),
            emeraldAccent = Color(0xFFA6E3A1),
            emeraldAccentGlow = Color(0x4DA6E3A1),
            crimsonAccent = Color(0xFFF38BA8),
            crimsonAccentGlow = Color(0x4DF38BA8),
            amberAccent = Color(0xFFFAB387),
            tertiaryAccent = Color(0xFFCBA6F7),
            secondaryGlowAccent = Color(0xFFF5C2E7),
            textPrimary = Color(0xFFCDD6F4),
            textSecondary = Color(0xFFA6ADC8),
            textTertiary = Color(0xFF7F849C),
            onAccent = Color(0xFF11111B),
            subtleFill = Color(0xFFCBA6F7).copy(alpha = 0.10f),
            subtleBorder = Color(0xFFCBA6F7).copy(alpha = 0.22f),
            frostedTint = Color(0x28CBA6F7),
            frostedBorder = Color(0x4D89B4FA)
        )

        AppThemeStyle.SYNTHWAVE_84 -> PulsePalette(
            isDark = true,
            background = Color(0xFF171122),
            surfaceElevated = Color(0xFF241B2F),
            glassSurface = Color(0xCC2C213D),
            glassSurfaceVariant = Color(0xE637294C),
            stackCardFront = Color(0xFF2E2042),
            stackCardBack = Color(0xFF211631),
            glassBorder = Color(0x38FF7EDB),
            glassBorderLuminous = Color(0x7036F9F6),
            glassTopSpecular = Color(0x42FF7EDB),
            neuBase = Color(0xFF241B2F),
            neuSurfaceLight = Color(0xFF312542),
            neuHighlightEdge = Color(0xFF4C3A66),
            neuShadowEdge = Color(0xFF110C19),
            primaryAccent = Color(0xFF36F9F6),
            primaryAccentGlow = Color(0x4D36F9F6),
            emeraldAccent = Color(0xFF72F1B8),
            emeraldAccentGlow = Color(0x4D72F1B8),
            crimsonAccent = Color(0xFFFE4450),
            crimsonAccentGlow = Color(0x4DFE4450),
            amberAccent = Color(0xFFFEDE5D),
            tertiaryAccent = Color(0xFFFF7EDB),
            secondaryGlowAccent = Color(0xFFFF7EDB),
            textPrimary = Color(0xFFF8F0FF),
            textSecondary = Color(0xFFC2B3DA),
            textTertiary = Color(0xFF8E7BA8),
            onAccent = Color(0xFF140D1F),
            subtleFill = Color(0xFFFF7EDB).copy(alpha = 0.12f),
            subtleBorder = Color(0xFFFF7EDB).copy(alpha = 0.28f),
            frostedTint = Color(0x2EFF7EDB),
            frostedBorder = Color(0x5236F9F6)
        )

        AppThemeStyle.NORD -> PulsePalette(
            isDark = true,
            background = Color(0xFF242933),
            surfaceElevated = Color(0xFF2E3440),
            glassSurface = Color(0xCC353C4A),
            glassSurfaceVariant = Color(0xE63B4252),
            stackCardFront = Color(0xFF3B4252),
            stackCardBack = Color(0xFF2E3440),
            glassBorder = Color(0x30D8DEE9),
            glassBorderLuminous = Color(0x6688C0D0),
            glassTopSpecular = Color(0x30ECEFF4),
            neuBase = Color(0xFF2E3440),
            neuSurfaceLight = Color(0xFF3B4252),
            neuHighlightEdge = Color(0xFF4C566A),
            neuShadowEdge = Color(0xFF1E222A),
            primaryAccent = Color(0xFF88C0D0),
            primaryAccentGlow = Color(0x4D88C0D0),
            emeraldAccent = Color(0xFFA3BE8C),
            emeraldAccentGlow = Color(0x4DA3BE8C),
            crimsonAccent = Color(0xFFBF616A),
            crimsonAccentGlow = Color(0x4DBF616A),
            amberAccent = Color(0xFFEBCB8B),
            tertiaryAccent = Color(0xFF81A1C1),
            secondaryGlowAccent = Color(0xFFB48EAD),
            textPrimary = Color(0xFFECEFF4),
            textSecondary = Color(0xFFD8DEE9),
            textTertiary = Color(0xFF9AA7B8),
            onAccent = Color(0xFF1D222B),
            subtleFill = Color(0xFFECEFF4).copy(alpha = 0.08f),
            subtleBorder = Color(0xFF88C0D0).copy(alpha = 0.24f),
            frostedTint = Color(0x2888C0D0),
            frostedBorder = Color(0x4D8FBCBB)
        )

        AppThemeStyle.DRACULA_PRO -> PulsePalette(
            isDark = true,
            background = Color(0xFF17161F),
            surfaceElevated = Color(0xFF22212C),
            glassSurface = Color(0xCC2A2838),
            glassSurfaceVariant = Color(0xE6343146),
            stackCardFront = Color(0xFF2D2A3E),
            stackCardBack = Color(0xFF1F1D2B),
            glassBorder = Color(0x339580FF),
            glassBorderLuminous = Color(0x6680FFEA),
            glassTopSpecular = Color(0x36F8F8F2),
            neuBase = Color(0xFF22212C),
            neuSurfaceLight = Color(0xFF2D2B3D),
            neuHighlightEdge = Color(0xFF454158),
            neuShadowEdge = Color(0xFF111017),
            primaryAccent = Color(0xFF9580FF),
            primaryAccentGlow = Color(0x4D9580FF),
            emeraldAccent = Color(0xFF8AFF80),
            emeraldAccentGlow = Color(0x4D8AFF80),
            crimsonAccent = Color(0xFFFF5555),
            crimsonAccentGlow = Color(0x4DFF5555),
            amberAccent = Color(0xFFFFFF80),
            tertiaryAccent = Color(0xFF80FFEA),
            secondaryGlowAccent = Color(0xFFFF80BF),
            textPrimary = Color(0xFFF8F8F2),
            textSecondary = Color(0xFFB9B5D3),
            textTertiary = Color(0xFF7970A9),
            onAccent = Color(0xFF111018),
            subtleFill = Color(0xFF9580FF).copy(alpha = 0.12f),
            subtleBorder = Color(0xFF9580FF).copy(alpha = 0.26f),
            frostedTint = Color(0x289580FF),
            frostedBorder = Color(0x5280FFEA)
        )
    }
}

private fun lightPaletteForTheme(style: AppThemeStyle): PulsePalette {
    return when (style) {
        AppThemeStyle.CHROMATIC_FROSTED_LIQUID,
        AppThemeStyle.HOLOGRAPHIC_LIQUID_GLASS,
        AppThemeStyle.FROSTED_GLASSMORPHISM,
        AppThemeStyle.GLASS_PSD_MOCKUP -> PulsePalette(
            isDark = false,
            background = Color(0xFFF1F5FA),
            surfaceElevated = Color(0xFFFFFFFF),
            glassSurface = Color(0xDCFFFFFF),
            glassSurfaceVariant = Color(0xFFE7EFF9),
            stackCardFront = Color(0xFFFFFFFF),
            stackCardBack = Color(0xFFEBF1F8),
            glassBorder = Color(0x240F172A),
            glassBorderLuminous = Color(0x660077B6),
            glassTopSpecular = Color(0xD9FFFFFF),
            neuBase = Color(0xFFE7ECF3),
            neuSurfaceLight = Color(0xFFF3F6FB),
            neuHighlightEdge = Color(0xFFFFFFFF),
            neuShadowEdge = Color(0xFFC6D0DE),
            primaryAccent = Color(0xFF0077B6),
            primaryAccentGlow = Color(0x3D0096C7),
            emeraldAccent = Color(0xFF058C54),
            emeraldAccentGlow = Color(0x3D058C54),
            crimsonAccent = Color(0xFFD92D20),
            crimsonAccentGlow = Color(0x3DD92D20),
            amberAccent = Color(0xFFB77900),
            tertiaryAccent = Color(0xFF7B2CBF),
            secondaryGlowAccent = Color(0xFFD91E75),
            textPrimary = Color(0xFF0F172A),
            textSecondary = Color(0xFF475569),
            textTertiary = Color(0xFF64748B),
            onAccent = Color.White,
            subtleFill = Color(0xFF0F172A).copy(alpha = 0.06f),
            subtleBorder = Color(0xFF0F172A).copy(alpha = 0.14f),
            frostedTint = Color(0xCCFFFFFF),
            frostedBorder = Color(0x9990CAF9)
        )

        AppThemeStyle.NEUMORPHISM -> PulsePalette(
            isDark = false,
            background = Color(0xFFE6EBF2),
            surfaceElevated = Color(0xFFEDF1F7),
            glassSurface = Color(0xFFEDF1F7),
            glassSurfaceVariant = Color(0xFFE0E6F0),
            stackCardFront = Color(0xFFF2F5FA),
            stackCardBack = Color(0xFFE1E7F0),
            glassBorder = Color(0xFFCBD5E1),
            glassBorderLuminous = Color(0x660077B6),
            glassTopSpecular = Color(0xE6FFFFFF),
            neuBase = Color(0xFFE6EBF2),
            neuSurfaceLight = Color(0xFFF1F5FA),
            neuHighlightEdge = Color(0xFFFFFFFF),
            neuShadowEdge = Color(0xFFBAC4D4),
            primaryAccent = Color(0xFF0077B6),
            primaryAccentGlow = Color(0x380077B6),
            emeraldAccent = Color(0xFF058C54),
            emeraldAccentGlow = Color(0x38058C54),
            crimsonAccent = Color(0xFFD92D20),
            crimsonAccentGlow = Color(0x38D92D20),
            amberAccent = Color(0xFFB77900),
            tertiaryAccent = Color(0xFF7B2CBF),
            secondaryGlowAccent = Color(0xFF0077B6),
            textPrimary = Color(0xFF141C2B),
            textSecondary = Color(0xFF4A5568),
            textTertiary = Color(0xFF718096),
            onAccent = Color.White,
            subtleFill = Color(0xFF0F172A).copy(alpha = 0.06f),
            subtleBorder = Color(0xFF0F172A).copy(alpha = 0.14f),
            frostedTint = Color(0xCCFFFFFF),
            frostedBorder = Color(0xFFCBD5E1)
        )

        AppThemeStyle.TOKYO_NIGHT -> PulsePalette(
            isDark = false,
            background = Color(0xFFE6E8F0),
            surfaceElevated = Color(0xFFF4F5FA),
            glassSurface = Color(0xE0F4F5FA),
            glassSurfaceVariant = Color(0xFFE1E4F2),
            stackCardFront = Color(0xFFF9FAFE),
            stackCardBack = Color(0xFFE3E6F3),
            glassBorder = Color(0x382E7DE9),
            glassBorderLuminous = Color(0x702E7DE9),
            glassTopSpecular = Color(0xE6FFFFFF),
            neuBase = Color(0xFFE6E8F0),
            neuSurfaceLight = Color(0xFFF4F5FA),
            neuHighlightEdge = Color(0xFFFFFFFF),
            neuShadowEdge = Color(0xFFC2C8DB),
            primaryAccent = Color(0xFF2E7DE9),
            primaryAccentGlow = Color(0x3D2E7DE9),
            emeraldAccent = Color(0xFF118C74),
            emeraldAccentGlow = Color(0x3D118C74),
            crimsonAccent = Color(0xFFC64343),
            crimsonAccentGlow = Color(0x3DC64343),
            amberAccent = Color(0xFF965027),
            tertiaryAccent = Color(0xFF7847BD),
            secondaryGlowAccent = Color(0xFF007197),
            textPrimary = Color(0xFF1A1B26),
            textSecondary = Color(0xFF4A5068),
            textTertiary = Color(0xFF6B7394),
            onAccent = Color.White,
            subtleFill = Color(0xFF2E7DE9).copy(alpha = 0.09f),
            subtleBorder = Color(0xFF2E7DE9).copy(alpha = 0.20f),
            frostedTint = Color(0xCCF4F5FA),
            frostedBorder = Color(0x882E7DE9)
        )

        AppThemeStyle.CATPPUCCIN_MOCHA -> PulsePalette(
            isDark = false,
            background = Color(0xFFEFF1F5),
            surfaceElevated = Color(0xFFFAFBFD),
            glassSurface = Color(0xE6FAFBFD),
            glassSurfaceVariant = Color(0xFFE6E9EF),
            stackCardFront = Color(0xFFFFFFFF),
            stackCardBack = Color(0xFFE6E9EF),
            glassBorder = Color(0x338839EF),
            glassBorderLuminous = Color(0x661E66F5),
            glassTopSpecular = Color(0xE6FFFFFF),
            neuBase = Color(0xFFEFF1F5),
            neuSurfaceLight = Color(0xFFFAFBFD),
            neuHighlightEdge = Color(0xFFFFFFFF),
            neuShadowEdge = Color(0xFFCCD0DA),
            primaryAccent = Color(0xFF1E66F5),
            primaryAccentGlow = Color(0x381E66F5),
            emeraldAccent = Color(0xFF40A02B),
            emeraldAccentGlow = Color(0x3840A02B),
            crimsonAccent = Color(0xFFD20F39),
            crimsonAccentGlow = Color(0x38D20F39),
            amberAccent = Color(0xFFDF8E1D),
            tertiaryAccent = Color(0xFF8839EF),
            secondaryGlowAccent = Color(0xFFEA76CB),
            textPrimary = Color(0xFF4C4F69),
            textSecondary = Color(0xFF5C5F77),
            textTertiary = Color(0xFF7C7F93),
            onAccent = Color.White,
            subtleFill = Color(0xFF4C4F69).copy(alpha = 0.07f),
            subtleBorder = Color(0xFF8839EF).copy(alpha = 0.22f),
            frostedTint = Color(0xCCFFFFFF),
            frostedBorder = Color(0x808839EF)
        )

        AppThemeStyle.SYNTHWAVE_84 -> PulsePalette(
            isDark = false,
            background = Color(0xFFF5EDFC),
            surfaceElevated = Color(0xFFFCF8FF),
            glassSurface = Color(0xE6FCF8FF),
            glassSurfaceVariant = Color(0xFFEDE0F8),
            stackCardFront = Color(0xFFFFFFFF),
            stackCardBack = Color(0xFFEAD9F7),
            glassBorder = Color(0x3DC61B89),
            glassBorderLuminous = Color(0x70008BA3),
            glassTopSpecular = Color(0xE6FFFFFF),
            neuBase = Color(0xFFF5EDFC),
            neuSurfaceLight = Color(0xFFFCF8FF),
            neuHighlightEdge = Color(0xFFFFFFFF),
            neuShadowEdge = Color(0xFFD7BEEB),
            primaryAccent = Color(0xFF008BA3),
            primaryAccentGlow = Color(0x3D008BA3),
            emeraldAccent = Color(0xFF0D8F63),
            emeraldAccentGlow = Color(0x3D0D8F63),
            crimsonAccent = Color(0xFFD41E45),
            crimsonAccentGlow = Color(0x3DD41E45),
            amberAccent = Color(0xFFC27400),
            tertiaryAccent = Color(0xFFC61B89),
            secondaryGlowAccent = Color(0xFFC61B89),
            textPrimary = Color(0xFF241634),
            textSecondary = Color(0xFF563D73),
            textTertiary = Color(0xFF7A6199),
            onAccent = Color.White,
            subtleFill = Color(0xFFC61B89).copy(alpha = 0.08f),
            subtleBorder = Color(0xFFC61B89).copy(alpha = 0.22f),
            frostedTint = Color(0xCCFCF8FF),
            frostedBorder = Color(0x80C61B89)
        )

        AppThemeStyle.NORD -> PulsePalette(
            isDark = false,
            background = Color(0xFFECEFF4),
            surfaceElevated = Color(0xFFF8FAFC),
            glassSurface = Color(0xE6F8FAFC),
            glassSurfaceVariant = Color(0xFFE5E9F0),
            stackCardFront = Color(0xFFFFFFFF),
            stackCardBack = Color(0xFFE5E9F0),
            glassBorder = Color(0x334C566A),
            glassBorderLuminous = Color(0x703B6EA5),
            glassTopSpecular = Color(0xE6FFFFFF),
            neuBase = Color(0xFFECEFF4),
            neuSurfaceLight = Color(0xFFF8FAFC),
            neuHighlightEdge = Color(0xFFFFFFFF),
            neuShadowEdge = Color(0xFFD0D7E3),
            primaryAccent = Color(0xFF3B6EA5),
            primaryAccentGlow = Color(0x383B6EA5),
            emeraldAccent = Color(0xFF478556),
            emeraldAccentGlow = Color(0x38478556),
            crimsonAccent = Color(0xFFB3444E),
            crimsonAccentGlow = Color(0x38B3444E),
            amberAccent = Color(0xFFA97926),
            tertiaryAccent = Color(0xFF5E81AC),
            secondaryGlowAccent = Color(0xFF8C6286),
            textPrimary = Color(0xFF2E3440),
            textSecondary = Color(0xFF4C566A),
            textTertiary = Color(0xFF6B778D),
            onAccent = Color.White,
            subtleFill = Color(0xFF2E3440).copy(alpha = 0.07f),
            subtleBorder = Color(0xFF3B6EA5).copy(alpha = 0.22f),
            frostedTint = Color(0xCCF8FAFC),
            frostedBorder = Color(0x805E81AC)
        )

        AppThemeStyle.DRACULA_PRO -> PulsePalette(
            isDark = false,
            background = Color(0xFFF4F2FA),
            surfaceElevated = Color(0xFFFDFCFF),
            glassSurface = Color(0xE6FDFCFF),
            glassSurfaceVariant = Color(0xFFEBE7F7),
            stackCardFront = Color(0xFFFFFFFF),
            stackCardBack = Color(0xFFEAE6F7),
            glassBorder = Color(0x36644AC9),
            glassBorderLuminous = Color(0x70644AC9),
            glassTopSpecular = Color(0xE6FFFFFF),
            neuBase = Color(0xFFF4F2FA),
            neuSurfaceLight = Color(0xFFFDFCFF),
            neuHighlightEdge = Color(0xFFFFFFFF),
            neuShadowEdge = Color(0xFFD3CCE9),
            primaryAccent = Color(0xFF644AC9),
            primaryAccentGlow = Color(0x3D644AC9),
            emeraldAccent = Color(0xFF148F44),
            emeraldAccentGlow = Color(0x3D148F44),
            crimsonAccent = Color(0xFFCB2B3E),
            crimsonAccentGlow = Color(0x3DCB2B3E),
            amberAccent = Color(0xFF9B7200),
            tertiaryAccent = Color(0xFF008B8B),
            secondaryGlowAccent = Color(0xFFC42B7B),
            textPrimary = Color(0xFF1F1E2B),
            textSecondary = Color(0xFF504B6B),
            textTertiary = Color(0xFF746E94),
            onAccent = Color.White,
            subtleFill = Color(0xFF644AC9).copy(alpha = 0.09f),
            subtleBorder = Color(0xFF644AC9).copy(alpha = 0.24f),
            frostedTint = Color(0xCCFDFCFF),
            frostedBorder = Color(0x80644AC9)
        )
    }
}

val LocalPulsePalette = staticCompositionLocalOf {
    paletteForTheme(AppThemeStyle.CHROMATIC_FROSTED_LIQUID, isDark = true)
}

// Dynamic Semantic Tokens backed by the active theme + Light/Dark appearance
val DeepVoidBlack: Color
    @Composable get() = LocalPulsePalette.current.background

val DarkSurfaceElevated: Color
    @Composable get() = LocalPulsePalette.current.surfaceElevated

val GlassSurface: Color
    @Composable get() = LocalPulsePalette.current.glassSurface

val GlassSurfaceVariant: Color
    @Composable get() = LocalPulsePalette.current.glassSurfaceVariant

val StackCardFrontSurface: Color
    @Composable get() = LocalPulsePalette.current.stackCardFront

val StackCardBackSurface: Color
    @Composable get() = LocalPulsePalette.current.stackCardBack

val GlassBorder: Color
    @Composable get() = LocalPulsePalette.current.glassBorder

val GlassBorderLuminous: Color
    @Composable get() = LocalPulsePalette.current.glassBorderLuminous

// Neumorphism Sculpted Slate Tokens
val NeuBaseDark: Color
    @Composable get() = LocalPulsePalette.current.neuBase

// Holographic & Chromatic Dispersion Tokens
val HoloViolet: Color
    @Composable get() = LocalPulsePalette.current.tertiaryAccent

val HoloMagenta: Color
    @Composable get() = LocalPulsePalette.current.secondaryGlowAccent

// Vibrant Cyber Accents
val ElectricCyan: Color
    @Composable get() = LocalPulsePalette.current.primaryAccent

val EmeraldMint: Color
    @Composable get() = LocalPulsePalette.current.emeraldAccent

val CrimsonPulse: Color
    @Composable get() = LocalPulsePalette.current.crimsonAccent

val AmberWarning: Color
    @Composable get() = LocalPulsePalette.current.amberAccent

val TextPrimary: Color
    @Composable get() = LocalPulsePalette.current.textPrimary

val TextSecondary: Color
    @Composable get() = LocalPulsePalette.current.textSecondary

val OnAccentInk: Color
    @Composable get() = LocalPulsePalette.current.onAccent

val SubtleSurfaceFill: Color
    @Composable get() = LocalPulsePalette.current.subtleFill

val SubtleSurfaceBorder: Color
    @Composable get() = LocalPulsePalette.current.subtleBorder

// Fixed Full-Screen Update Prompt Palette (always high-contrast dark crimson space card)
val UpdateCrimson = Color(0xFFE01818)
val UpdateCrimsonText = Color(0xFFD61F26)
val UpdateHaloOuter = Color(0xFF280D11)
val UpdateHaloMiddle = Color(0xFF471216)
val UpdateSpaceInner = Color(0xFF14090B)
val UpdateRocketBody = Color(0xFFF5F7FA)
val UpdateRocketWindow = Color(0xFF00D4FF)
val UpdateRocketWindowRim = Color(0xFFCDD5DF)
val UpdateRocketFlameOuter = Color(0xFFFF7A00)
val UpdateRocketFlameInner = Color(0xFFFFD600)
val UpdateStarYellow = Color(0xFFD4BA2E)
val UpdateCloseGray = Color(0xFF8E929B)
val UpdateCardSurface = Color(0xFF14151D)
val UpdateCardInner = Color(0xFF1C1E28)
val UpdateCrimsonBright = Color(0xFFF22626)
val UpdateCrimsonDeep = Color(0xFFB91117)
val UpdateTextPrimary = Color(0xFFF0F6FC)
val UpdateTextSecondary = Color(0xFF9EA7B3)
val UpdateBorder = Color(0x24FFFFFF)
