package com.example.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf

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
    )
}

val LocalAppThemeStyle = staticCompositionLocalOf { AppThemeStyle.CHROMATIC_FROSTED_LIQUID }
val LocalSimpleMode = staticCompositionLocalOf { false }
