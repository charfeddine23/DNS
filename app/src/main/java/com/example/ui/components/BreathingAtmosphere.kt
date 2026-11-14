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
import com.example.ui.theme.CrimsonPulse
import com.example.ui.theme.DeepVoidBlack
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldMint
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
 * Full-bleed living atmospheric backdrop that breathes organically with sinusoidal aurora orbs,
 * a subtle cyber telemetry grid, and drifting signal micro-particles driven by protection state.
 */
@Composable
fun BreathingAtmosphere(
    isProtected: Boolean,
    isAdGuardShield: Boolean,
    ambientGlowEnabled: Boolean,
    pageOffsetFraction: Float,
    modifier: Modifier = Modifier
) {
    val primaryColor by animateColorAsState(
        targetValue = when {
            !isProtected -> CrimsonPulse
            isAdGuardShield -> EmeraldMint
            else -> ElectricCyan
        },
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "aurora_primary"
    )

    val secondaryColor by animateColorAsState(
        targetValue = when {
            !isProtected -> ElectricCyan.copy(alpha = 0.55f)
            isAdGuardShield -> ElectricCyan
            else -> EmeraldMint
        },
        animationSpec = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DeepVoidBlack)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val breathScale = 1f + 0.14f * sin(breathPhase)
            val parallaxShiftX = (pageOffsetFraction - 1f) * w * 0.12f

            // 1. Subtle Cyber Micro-Grid
            val gridStep = 44.dp.toPx()
            val gridAlpha = if (isProtected) 0.035f else 0.022f
            val gridColor = primaryColor.copy(alpha = gridAlpha)
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

            if (ambientGlowEnabled) {
                // 2. Primary Breathing Aurora Orb (top-center reactive)
                val orb1Center = Offset(
                    x = w * 0.5f + cos(driftPhase) * w * 0.10f - parallaxShiftX,
                    y = h * 0.26f + sin(breathPhase) * h * 0.03f
                )
                val orb1Radius = w * 0.85f * breathScale
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            primaryColor.copy(alpha = if (isProtected) 0.24f else 0.14f),
                            primaryColor.copy(alpha = if (isProtected) 0.07f else 0.03f),
                            Color.Transparent
                        ),
                        center = orb1Center,
                        radius = orb1Radius
                    ),
                    radius = orb1Radius,
                    center = orb1Center
                )

                // 3. Counter-breathing Deep Nebula Orb (bottom-right/left drift)
                val orb2Scale = 1f + 0.12f * cos(breathPhase + 1.1f)
                val orb2Center = Offset(
                    x = w * 0.22f + sin(driftPhase * 0.8f) * w * 0.14f + parallaxShiftX * 0.7f,
                    y = h * 0.76f + cos(driftPhase) * h * 0.05f
                )
                val orb2Radius = w * 0.72f * orb2Scale
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            secondaryColor.copy(alpha = if (isProtected) 0.16f else 0.09f),
                            Color.Transparent
                        ),
                        center = orb2Center,
                        radius = orb2Radius
                    ),
                    radius = orb2Radius,
                    center = orb2Center
                )

                // 4. Right Edge Accent Shimmer
                val orb3Center = Offset(
                    x = w * 0.88f - cos(driftPhase) * w * 0.08f - parallaxShiftX * 0.5f,
                    y = h * 0.54f + sin(breathPhase * 0.7f) * h * 0.06f
                )
                val orb3Radius = w * 0.55f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            primaryColor.copy(alpha = if (isProtected) 0.11f else 0.05f),
                            Color.Transparent
                        ),
                        center = orb3Center,
                        radius = orb3Radius
                    ),
                    radius = orb3Radius,
                    center = orb3Center
                )
            }

            // 5. Floating Luminous Signal Particles
            particles.forEach { p ->
                val yNorm = ((p.yOffset - particleFlow * p.speedFactor) % 1f + 1f) % 1f
                val swayX = sin(driftPhase + p.xFraction * 8f) * 14.dp.toPx()
                val px = (p.xFraction * w + swayX - parallaxShiftX * 0.35f).coerceIn(0f, w)
                val py = yNorm * h
                val pulseFactor = 0.65f + 0.35f * sin(breathPhase + p.yOffset * 6f)
                drawCircle(
                    color = primaryColor.copy(alpha = p.alpha * pulseFactor * (if (isProtected) 1f else 0.55f)),
                    radius = p.radiusDp.dp.toPx(),
                    center = Offset(px, py)
                )
            }
        }
    }
}
