package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AppThemeStyle
import com.example.ui.theme.LocalAppThemeStyle
import com.example.ui.theme.LocalPulsePalette
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private data class SignalParticle(
    val xFraction: Float,
    val yOffset: Float,
    val speedFactor: Float,
    val radiusDp: Float,
    val alpha: Float
)

/**
 * Full-bleed living atmospheric backdrop that adapts optically to the active [AppThemeStyle]
 * and Dark / Light appearance mode at locked 90+ fps.
 */
@Composable
fun BreathingAtmosphere(
    isProtected: Boolean,
    isAdGuardShield: Boolean,
    ambientGlowEnabled: Boolean,
    pageOffsetFraction: Float,
    modifier: Modifier = Modifier
) {
    val themeStyle = LocalAppThemeStyle.current
    val palette = LocalPulsePalette.current
    val isDark = palette.isDark

    val primaryTarget = when (themeStyle) {
        AppThemeStyle.HOLOGRAPHIC_LIQUID_GLASS -> when {
            !isProtected -> palette.secondaryGlowAccent
            isAdGuardShield -> palette.emeraldAccent
            else -> palette.tertiaryAccent
        }
        AppThemeStyle.CHROMATIC_FROSTED_LIQUID -> when {
            !isProtected -> palette.secondaryGlowAccent
            isAdGuardShield -> palette.emeraldAccent
            else -> palette.primaryAccent
        }
        AppThemeStyle.FROSTED_GLASSMORPHISM -> when {
            !isProtected -> palette.crimsonAccent
            isAdGuardShield -> palette.emeraldAccent
            else -> if (isDark) Color(0xFF6CD4FF) else palette.primaryAccent
        }
        AppThemeStyle.GLASS_PSD_MOCKUP -> when {
            !isProtected -> palette.crimsonAccent
            isAdGuardShield -> palette.emeraldAccent
            else -> if (isDark) Color(0xFF3A86FF) else palette.primaryAccent
        }
        AppThemeStyle.NEUMORPHISM -> when {
            !isProtected -> palette.crimsonAccent
            isAdGuardShield -> palette.emeraldAccent
            else -> palette.primaryAccent
        }
        AppThemeStyle.TOKYO_NIGHT -> when {
            !isProtected -> palette.crimsonAccent
            isAdGuardShield -> palette.emeraldAccent
            else -> palette.primaryAccent
        }
        AppThemeStyle.CATPPUCCIN_MOCHA -> when {
            !isProtected -> palette.crimsonAccent
            isAdGuardShield -> palette.emeraldAccent
            else -> palette.tertiaryAccent
        }
        AppThemeStyle.SYNTHWAVE_84 -> when {
            !isProtected -> palette.crimsonAccent
            isAdGuardShield -> palette.emeraldAccent
            else -> palette.secondaryGlowAccent
        }
        AppThemeStyle.NORD -> when {
            !isProtected -> palette.crimsonAccent
            isAdGuardShield -> palette.emeraldAccent
            else -> palette.primaryAccent
        }
        AppThemeStyle.DRACULA_PRO -> when {
            !isProtected -> palette.crimsonAccent
            isAdGuardShield -> palette.emeraldAccent
            else -> palette.primaryAccent
        }
    }

    val secondaryTarget = when (themeStyle) {
        AppThemeStyle.HOLOGRAPHIC_LIQUID_GLASS -> if (isProtected) palette.primaryAccent else palette.tertiaryAccent
        AppThemeStyle.CHROMATIC_FROSTED_LIQUID -> if (isProtected) palette.secondaryGlowAccent else palette.primaryAccent
        AppThemeStyle.FROSTED_GLASSMORPHISM -> if (isDark) Color(0xFF9BE7FF) else palette.tertiaryAccent
        AppThemeStyle.GLASS_PSD_MOCKUP -> palette.primaryAccent
        AppThemeStyle.NEUMORPHISM -> if (isDark) Color(0xFF2B3242) else palette.neuShadowEdge
        AppThemeStyle.TOKYO_NIGHT -> palette.tertiaryAccent
        AppThemeStyle.CATPPUCCIN_MOCHA -> palette.amberAccent
        AppThemeStyle.SYNTHWAVE_84 -> palette.primaryAccent
        AppThemeStyle.NORD -> palette.tertiaryAccent
        AppThemeStyle.DRACULA_PRO -> palette.secondaryGlowAccent
    }

    val baseBgTarget = when (themeStyle) {
        AppThemeStyle.FROSTED_GLASSMORPHISM -> if (isDark) Color(0xFF080D15) else palette.background
        AppThemeStyle.GLASS_PSD_MOCKUP -> if (isDark) Color(0xFF070A14) else palette.background
        else -> palette.background
    }

    val baseBgColor by animateColorAsState(
        targetValue = baseBgTarget,
        animationSpec = tween(durationMillis = 700),
        label = "bg_base"
    )

    val primaryColor by animateColorAsState(
        targetValue = primaryTarget,
        animationSpec = tween(durationMillis = 850, easing = FastOutSlowInEasing),
        label = "aurora_primary"
    )

    val secondaryColor by animateColorAsState(
        targetValue = secondaryTarget,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label = "aurora_secondary"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "living_atmosphere")

    val breathPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "breath_phase"
    )

    val driftPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 11500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "drift_phase"
    )

    val particleFlow by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isProtected) 9000 else 15000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "particle_flow"
    )

    val particles = remember {
        listOf(
            SignalParticle(0.12f, 0.15f, 1.0f, 1.8f, 0.35f),
            SignalParticle(0.28f, 0.72f, 1.3f, 1.2f, 0.25f),
            SignalParticle(0.41f, 0.42f, 0.8f, 2.1f, 0.30f),
            SignalParticle(0.58f, 0.85f, 1.1f, 1.5f, 0.28f),
            SignalParticle(0.73f, 0.24f, 1.4f, 1.9f, 0.38f),
            SignalParticle(0.86f, 0.61f, 0.9f, 1.3f, 0.24f),
            SignalParticle(0.21f, 0.54f, 1.2f, 1.4f, 0.22f),
            SignalParticle(0.67f, 0.11f, 0.95f, 1.7f, 0.32f),
            SignalParticle(0.91f, 0.39f, 1.15f, 1.5f, 0.26f),
            SignalParticle(0.49f, 0.67f, 1.35f, 1.1f, 0.20f)
        )
    }

    val useBlurredBackdrop = themeStyle == AppThemeStyle.CHROMATIC_FROSTED_LIQUID ||
        themeStyle == AppThemeStyle.FROSTED_GLASSMORPHISM ||
        themeStyle == AppThemeStyle.CATPPUCCIN_MOCHA

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(baseBgColor)
    ) {
        // Layer 1: Optical Aurora & Soft Multi-Stop Bokeh Backdrop
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val breathScale = 1f + 0.14f * sin(breathPhase)
            val parallaxShiftX = (pageOffsetFraction - 1f) * w * 0.12f

            if (ambientGlowEnabled) {
                val baseBoost = when (themeStyle) {
                    AppThemeStyle.NEUMORPHISM -> 0.28f
                    AppThemeStyle.NORD -> 0.65f
                    AppThemeStyle.CATPPUCCIN_MOCHA -> 0.85f
                    AppThemeStyle.GLASS_PSD_MOCKUP -> 1.15f
                    AppThemeStyle.HOLOGRAPHIC_LIQUID_GLASS -> 1.22f
                    AppThemeStyle.SYNTHWAVE_84 -> 1.25f
                    AppThemeStyle.DRACULA_PRO -> 1.12f
                    AppThemeStyle.TOKYO_NIGHT -> 1.08f
                    else -> 1.0f
                }
                val modeAttenuation = if (isDark) 1.0f else 0.72f
                val intensityBoost = baseBoost * modeAttenuation
                val frostedSpread = if (useBlurredBackdrop) 1.18f else 1.0f

                val orb1Center = Offset(
                    x = w * 0.5f + cos(driftPhase) * w * 0.10f - parallaxShiftX,
                    y = h * 0.26f + sin(breathPhase) * h * 0.03f
                )
                val orb1Radius = w * 0.90f * breathScale * frostedSpread
                drawCircle(
                    brush = Brush.radialGradient(
                        0.0f to primaryColor.copy(alpha = ((if (isProtected) 0.26f else 0.16f) * intensityBoost).coerceAtMost(0.45f)),
                        0.45f to primaryColor.copy(alpha = ((if (isProtected) 0.11f else 0.06f) * intensityBoost).coerceAtMost(0.26f)),
                        0.78f to primaryColor.copy(alpha = ((if (isProtected) 0.035f else 0.015f) * intensityBoost).coerceAtMost(0.10f)),
                        1.0f to Color.Transparent,
                        center = orb1Center,
                        radius = orb1Radius
                    ),
                    radius = orb1Radius,
                    center = orb1Center
                )

                val orb2Scale = 1f + 0.12f * cos(breathPhase + 1.1f)
                val orb2Center = Offset(
                    x = w * 0.22f + sin(driftPhase * 0.8f) * w * 0.14f + parallaxShiftX * 0.7f,
                    y = h * 0.76f + cos(driftPhase) * h * 0.05f
                )
                val orb2Radius = w * 0.78f * orb2Scale * frostedSpread
                drawCircle(
                    brush = Brush.radialGradient(
                        0.0f to secondaryColor.copy(alpha = ((if (isProtected) 0.18f else 0.10f) * intensityBoost).coerceAtMost(0.35f)),
                        0.55f to secondaryColor.copy(alpha = ((if (isProtected) 0.06f else 0.03f) * intensityBoost).coerceAtMost(0.14f)),
                        1.0f to Color.Transparent,
                        center = orb2Center,
                        radius = orb2Radius
                    ),
                    radius = orb2Radius,
                    center = orb2Center
                )

                val orb3Center = Offset(
                    x = w * 0.88f - cos(driftPhase) * w * 0.08f - parallaxShiftX * 0.5f,
                    y = h * 0.54f + sin(breathPhase * 0.7f) * h * 0.06f
                )
                val orb3Radius = w * 0.55f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            primaryColor.copy(alpha = ((if (isProtected) 0.13f else 0.06f) * intensityBoost).coerceAtMost(0.3f)),
                            Color.Transparent
                        ),
                        center = orb3Center,
                        radius = orb3Radius
                    ),
                    radius = orb3Radius,
                    center = orb3Center
                )
            }
        }

        // Layer 2: Crisp Cyber Micro-Grid & Luminous Signal Particles
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val parallaxShiftX = (pageOffsetFraction - 1f) * w * 0.12f

            val showGrid = themeStyle != AppThemeStyle.NEUMORPHISM && themeStyle != AppThemeStyle.NORD
            if (showGrid) {
                val gridStep = 44.dp.toPx()
                val baseGridAlpha = when (themeStyle) {
                    AppThemeStyle.SYNTHWAVE_84 -> if (isProtected) 0.065f else 0.040f
                    AppThemeStyle.TOKYO_NIGHT -> if (isProtected) 0.048f else 0.028f
                    else -> if (isProtected) 0.036f else 0.020f
                }
                val gridColor = primaryColor.copy(alpha = if (isDark) baseGridAlpha else baseGridAlpha * 1.15f)
                var gx = 0f
                while (gx < w) {
                    drawLine(
                        color = gridColor,
                        start = Offset(gx, 0f),
                        end = Offset(gx, h),
                        strokeWidth = 0.7.dp.toPx()
                    )
                    gx += gridStep
                }
                var gy = 0f
                while (gy < h) {
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, gy),
                        end = Offset(w, gy),
                        strokeWidth = 0.7.dp.toPx()
                    )
                    gy += gridStep
                }
            }

            if (themeStyle != AppThemeStyle.NEUMORPHISM || isProtected) {
                particles.forEach { p ->
                    val yNorm = ((p.yOffset - particleFlow * p.speedFactor) % 1f + 1f) % 1f
                    val swayX = sin(driftPhase + p.xFraction * 8f) * 14.dp.toPx()
                    val px = (p.xFraction * w + swayX - parallaxShiftX * 0.35f).coerceIn(0f, w)
                    val py = yNorm * h
                    val pulseFactor = 0.65f + 0.35f * sin(breathPhase + p.yOffset * 6f)
                    drawCircle(
                        color = primaryColor.copy(alpha = p.alpha * pulseFactor * (if (isProtected) 0.9f else 0.45f)),
                        radius = p.radiusDp.dp.toPx(),
                        center = Offset(px, py)
                    )
                }
            }
        }
    }
}
