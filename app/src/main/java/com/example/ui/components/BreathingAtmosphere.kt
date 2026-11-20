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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
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
 * and Dark / Light appearance mode with zero per-frame Composable recompositions.
 */
@Composable
fun BreathingAtmosphere(
    isProtected: Boolean,
    isAdGuardShield: Boolean,
    ambientGlowEnabled: Boolean,
    pageOffsetFractionProvider: () -> Float,
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

    val baseBgColorState = animateColorAsState(
        targetValue = baseBgTarget,
        animationSpec = tween(durationMillis = 700),
        label = "bg_base"
    )

    val primaryColorState = animateColorAsState(
        targetValue = primaryTarget,
        animationSpec = tween(durationMillis = 850, easing = FastOutSlowInEasing),
        label = "aurora_primary"
    )

    val secondaryColorState = animateColorAsState(
        targetValue = secondaryTarget,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label = "aurora_secondary"
    )

    val useBlurredBackdrop = themeStyle == AppThemeStyle.CHROMATIC_FROSTED_LIQUID ||
        themeStyle == AppThemeStyle.FROSTED_GLASSMORPHISM ||
        themeStyle == AppThemeStyle.CATPPUCCIN_MOCHA
    val showGrid = themeStyle != AppThemeStyle.NEUMORPHISM && themeStyle != AppThemeStyle.NORD

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(color = baseBgColorState.value)
            }
    ) {
        // Layer 1 & 2b: Optical Aurora + Luminous Signal Particles (suspended cleanly when glow disabled)
        if (ambientGlowEnabled) {
            BreathingAuroraAndParticlesLayer(
                themeStyle = themeStyle,
                isDark = isDark,
                isProtected = isProtected,
                useBlurredBackdrop = useBlurredBackdrop,
                primaryColorProvider = { primaryColorState.value },
                secondaryColorProvider = { secondaryColorState.value },
                pageOffsetFractionProvider = pageOffsetFractionProvider
            )
        }

        // Layer 2a: Cached Static Cyber Micro-Grid (single GPU Path draw)
        if (showGrid) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithCache {
                        val gridStep = 44.dp.toPx()
                        val gridStroke = Stroke(width = 0.7.dp.toPx())
                        val gridPath = Path().apply {
                            var gx = 0f
                            while (gx < size.width) {
                                moveTo(gx, 0f)
                                lineTo(gx, size.height)
                                gx += gridStep
                            }
                            var gy = 0f
                            while (gy < size.height) {
                                moveTo(0f, gy)
                                lineTo(size.width, gy)
                                gy += gridStep
                            }
                        }
                        val baseGridAlpha = when (themeStyle) {
                            AppThemeStyle.SYNTHWAVE_84 -> if (isProtected) 0.065f else 0.040f
                            AppThemeStyle.TOKYO_NIGHT -> if (isProtected) 0.048f else 0.028f
                            else -> if (isProtected) 0.036f else 0.020f
                        }
                        val alphaMult = if (isDark) baseGridAlpha else baseGridAlpha * 1.15f
                        onDrawBehind {
                            drawPath(
                                path = gridPath,
                                color = primaryColorState.value.copy(alpha = alphaMult),
                                style = gridStroke
                            )
                        }
                    }
            )
        }
    }
}

@Composable
private fun BreathingAuroraAndParticlesLayer(
    themeStyle: AppThemeStyle,
    isDark: Boolean,
    isProtected: Boolean,
    useBlurredBackdrop: Boolean,
    primaryColorProvider: () -> Color,
    secondaryColorProvider: () -> Color,
    pageOffsetFractionProvider: () -> Float
) {
    val infiniteTransition = rememberInfiniteTransition(label = "living_atmosphere")

    val breathPhaseState = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "breath_phase"
    )

    val driftPhaseState = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 11500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "drift_phase"
    )

    val particleFlowState = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isProtected) 9000 else 15000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "particle_flow"
    )

    val particles = remember {
        arrayOf(
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
    val showParticles = themeStyle != AppThemeStyle.NEUMORPHISM || isProtected

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val breathPhase = breathPhaseState.value
        val driftPhase = driftPhaseState.value
        val primaryColor = primaryColorProvider()
        val secondaryColor = secondaryColorProvider()
        val pageOffsetFraction = pageOffsetFractionProvider()

        val breathScale = 1f + 0.14f * sin(breathPhase)
        val parallaxShiftX = (pageOffsetFraction - 1f) * w * 0.12f

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

        if (showParticles) {
            val particleFlow = particleFlowState.value
            val swayAmplitude = 14.dp.toPx()
            val protAlphaMult = if (isProtected) 0.9f else 0.45f
            val densityScale = density
            for (i in particles.indices) {
                val p = particles[i]
                val yNorm = ((p.yOffset - particleFlow * p.speedFactor) % 1f + 1f) % 1f
                val swayX = sin(driftPhase + p.xFraction * 8f) * swayAmplitude
                val px = (p.xFraction * w + swayX - parallaxShiftX * 0.35f).coerceIn(0f, w)
                val py = yNorm * h
                val pulseFactor = 0.65f + 0.35f * sin(breathPhase + p.yOffset * 6f)
                drawCircle(
                    color = primaryColor.copy(alpha = p.alpha * pulseFactor * protAlphaMult),
                    radius = p.radiusDp * densityScale,
                    center = Offset(px, py)
                )
            }
        }
    }
}
