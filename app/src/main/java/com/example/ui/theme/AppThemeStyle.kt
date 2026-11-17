package com.example.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf

enum class AppAppearanceMode(
    val testTagSuffix: String
) {
    DARK("dark"),
    LIGHT("light"),
    SYSTEM("system")
}

enum class AppThemeStyle(
    val displayName: String,
    val shortBadge: String,
    val description: String
) {
    CHROMATIC_FROSTED_LIQUID(
        displayName = "Liquid / Frosted Glass",
        shortBadge = "CHROMATIC",
        description = "Frosted blurred backdrop, RGB chromatic dispersion & inner shadow"
    ),
    HOLOGRAPHIC_LIQUID_GLASS(
        displayName = "Holographic Liquid Glass",
        shortBadge = "PRISM",
        description = "Animated iridescent prism borders & liquid spectral reflection"
    ),
    FROSTED_GLASSMORPHISM(
        displayName = "Frosted Glassmorphism",
        shortBadge = "FROST",
        description = "Arctic diffused ice glass, soft micro-grain & blurred depth"
    ),
    GLASS_PSD_MOCKUP(
        displayName = "Glass morphism PSD Mockup",
        shortBadge = "STUDIO",
        description = "Multi-plane crystal mockup sheets with 45° specular glare"
    ),
    NEUMORPHISM(
        displayName = "Neumorphism",
        shortBadge = "TACTILE",
        description = "Sculpted soft-matte slate with dual light & shadow extrusion"
    ),
    TOKYO_NIGHT(
        displayName = "Tokyo Night",
        shortBadge = "CYBER",
        description = "Neon Tokyo midnight indigo, laser cyan & amethyst prism"
    ),
    CATPPUCCIN_MOCHA(
        displayName = "Catppuccin Mocha",
        shortBadge = "COZY",
        description = "Cozy velvet mocha, warm lavender & pastel sapphire glow"
    ),
    SYNTHWAVE_84(
        displayName = "SynthWave '84",
        shortBadge = "RETRO-GLOW",
        description = "Outrun twilight plum, hot arcade magenta & sunset gold"
    ),
    NORD(
        displayName = "Nord",
        shortBadge = "ARCTIC MINIMAL",
        description = "Nordic polar slate, glacial frost blue & aurora pine"
    ),
    DRACULA_PRO(
        displayName = "Dracula Pro",
        shortBadge = "ELITE",
        description = "Obsidian velvet pro, royal violet, blade cyan & venom green"
    )
}

val LocalAppThemeStyle = staticCompositionLocalOf { AppThemeStyle.CHROMATIC_FROSTED_LIQUID }
val LocalSimpleMode = staticCompositionLocalOf { false }
val LocalIsDarkTheme = staticCompositionLocalOf { true }
val LocalPageActive = compositionLocalOf { true }
