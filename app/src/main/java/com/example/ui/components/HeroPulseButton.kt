package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AppThemeStyle
import com.example.ui.theme.ChromaticAmber
import com.example.ui.theme.ChromaticCyan
import com.example.ui.theme.ChromaticMagenta
import com.example.ui.theme.CrimsonPulse
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldMint
import com.example.ui.theme.FrostedBorder
import com.example.ui.theme.HoloMagenta
import com.example.ui.theme.HoloViolet
import com.example.ui.theme.LocalAppThemeStyle
import com.example.ui.theme.LocalSimpleMode
import com.example.ui.theme.NeuBaseDark
import com.example.ui.theme.NeuHighlightEdge
import com.example.ui.theme.NeuShadowEdge
import com.example.ui.theme.NeuSurfaceLight
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun HeroPulseButton(
    isActive: Boolean,
    activeHostOrServer: String,
    latencyMs: Long?,
    isAdGuardShield: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val simpleMode = LocalSimpleMode.current
    val themeStyle = LocalAppThemeStyle.current
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }

    if (simpleMode) {
        val statusColor = when {
            !isActive -> CrimsonPulse
            isAdGuardShield -> EmeraldMint
            else -> ElectricCyan
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .size(200.dp)
                .testTag("hero_pulse_button_container")
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(160.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF131820))
                    .border(2.dp, statusColor.copy(alpha = 0.75f), CircleShape)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = ripple(bounded = true, color = statusColor),
                        onClick = onClick
                    )
                    .testTag("hero_pulse_button")
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = when {
                            !isActive -> Icons.Default.PowerSettingsNew
                            isAdGuardShield -> Icons.Default.Shield
                            else -> Icons.Default.Bolt
                        },
                        contentDescription = "Pulse Status",
                        tint = statusColor,
                        modifier = Modifier.size(38.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (isActive) "PROTECTED" else "OFFLINE",
                        color = if (isActive) statusColor else TextSecondary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        letterSpacing = 1.4.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isActive) {
                            if (latencyMs != null && latencyMs > 0) "${latencyMs} ms" else "ACTIVE"
                        } else {
                            "Tap to Connect"
                        },
                        color = if (isActive) TextPrimary else TextSecondary,
                        fontSize = 11.sp,
                        fontFamily = if (isActive) FontFamily.Monospace else FontFamily.Default
                    )
                }
            }
        }
        return
    }

    val isPressed by interactionSource.collectIsPressedAsState()

    val activeGlowColor by animateColorAsState(
        targetValue = when {
            !isActive -> CrimsonPulse
            isAdGuardShield -> EmeraldMint
            themeStyle == AppThemeStyle.HOLOGRAPHIC_LIQUID_GLASS -> HoloViolet
            else -> ElectricCyan
        },
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "hero_glow_color"
    )

    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.93f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "hero_press_scale"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "pulse_reactor")

    val coreBreath by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (isActive) 1.038f else 1.018f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isActive) 1900 else 2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "core_breath"
    )

    val orbitAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isActive) 5500 else 14000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbit_angle"
    )

    val wave1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave1"
    )
    val wave2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, delayMillis = 850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave2"
    )
    val wave3 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, delayMillis = 1700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave3"
    )

    val coreFillBrush = when (themeStyle) {
        AppThemeStyle.NEUMORPHISM -> Brush.linearGradient(
            colors = listOf(NeuSurfaceLight, NeuBaseDark),
            start = Offset.Zero,
            end = Offset(450f, 450f)
        )
        AppThemeStyle.FROSTED_GLASSMORPHISM -> Brush.radialGradient(
            colors = listOf(Color(0xFF283A4E), Color(0xFF15202E), Color(0xFF0D141F))
        )
        AppThemeStyle.GLASS_PSD_MOCKUP -> Brush.linearGradient(
            colors = listOf(Color(0x36FFFFFF), Color(0xE0131C2B), Color(0xFF0B101B)),
            start = Offset.Zero,
            end = Offset(420f, 420f)
        )
        else -> Brush.radialGradient(
            colors = listOf(Color(0xFF232C3B), Color(0xFF131822), Color(0xFF0A0D14)),
            center = Offset(120f, 100f),
            radius = 340f
        )
    }

    val coreBorderBrush = when (themeStyle) {
        AppThemeStyle.NEUMORPHISM -> Brush.linearGradient(
            colors = listOf(
                if (isActive) activeGlowColor.copy(alpha = 0.7f) else NeuHighlightEdge,
                NeuShadowEdge
            )
        )
        AppThemeStyle.HOLOGRAPHIC_LIQUID_GLASS -> Brush.sweepGradient(
            listOf(ElectricCyan, HoloViolet, HoloMagenta, EmeraldMint, ElectricCyan)
        )
        AppThemeStyle.CHROMATIC_FROSTED_LIQUID -> Brush.sweepGradient(
            listOf(ChromaticCyan, ChromaticMagenta, ChromaticAmber, activeGlowColor, ChromaticCyan)
        )
        AppThemeStyle.FROSTED_GLASSMORPHISM -> Brush.verticalGradient(
            listOf(FrostedBorder, activeGlowColor.copy(alpha = 0.65f), Color.White.copy(alpha = 0.18f))
        )
        AppThemeStyle.GLASS_PSD_MOCKUP -> Brush.linearGradient(
            listOf(Color.White.copy(alpha = 0.65f), activeGlowColor.copy(alpha = 0.7f), Color.White.copy(alpha = 0.12f))
        )
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(256.dp)
            .testTag("hero_pulse_button_container")
    ) {
        Canvas(modifier = Modifier.size(256.dp)) {
            val centerOffset = Offset(size.width / 2, size.height / 2)
            val baseRadius = size.width * 0.34f

            if (isActive) {
                listOf(wave1, wave2, wave3).forEach { progress ->
                    val radius = baseRadius + progress * (size.width * 0.16f)
                    val alpha = (1f - progress) * 0.45f
                    drawCircle(
                        color = activeGlowColor.copy(alpha = alpha),
                        radius = radius,
                        center = centerOffset,
                        style = Stroke(width = (2.2f * (1f - progress * 0.5f)).dp.toPx())
                    )
                }
            }

            val ring1Radius = size.width * 0.385f
            drawArc(
                color = activeGlowColor.copy(alpha = if (isActive) 0.65f else 0.22f),
                startAngle = orbitAngle,
                sweepAngle = 78f,
                useCenter = false,
                topLeft = Offset(centerOffset.x - ring1Radius, centerOffset.y - ring1Radius),
                size = Size(ring1Radius * 2f, ring1Radius * 2f),
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
            )
            drawArc(
                color = activeGlowColor.copy(alpha = if (isActive) 0.40f else 0.14f),
                startAngle = orbitAngle + 180f,
                sweepAngle = 55f,
                useCenter = false,
                topLeft = Offset(centerOffset.x - ring1Radius, centerOffset.y - ring1Radius),
                size = Size(ring1Radius * 2f, ring1Radius * 2f),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )

            val ring2Radius = size.width * 0.352f
            drawArc(
                color = Color.White.copy(alpha = if (isActive) 0.35f else 0.10f),
                startAngle = -orbitAngle * 1.3f,
                sweepAngle = 42f,
                useCenter = false,
                topLeft = Offset(centerOffset.x - ring2Radius, centerOffset.y - ring2Radius),
                size = Size(ring2Radius * 2f, ring2Radius * 2f),
                style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        Box(
            modifier = Modifier
                .size(198.dp)
                .scale(coreBreath)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            activeGlowColor.copy(alpha = if (isActive) 0.42f else 0.14f),
                            activeGlowColor.copy(alpha = if (isActive) 0.12f else 0.03f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(164.dp)
                .scale(pressScale * coreBreath)
                .shadow(
                    elevation = if (isActive) 24.dp else 12.dp,
                    shape = CircleShape,
                    ambientColor = activeGlowColor.copy(alpha = 0.5f),
                    spotColor = activeGlowColor.copy(alpha = 0.6f)
                )
                .clip(CircleShape)
                .background(brush = coreFillBrush)
                .border(
                    width = 2.dp,
                    brush = coreBorderBrush,
                    shape = CircleShape
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(bounded = true, color = activeGlowColor),
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onClick()
                    }
                )
                .testTag("hero_pulse_button")
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AnimatedContent(
                    targetState = Triple(isActive, isAdGuardShield, latencyMs),
                    transitionSpec = {
                        (fadeIn(tween(240)) + scaleIn(initialScale = 0.85f, animationSpec = tween(240)))
                            .togetherWith(fadeOut(tween(180)) + scaleOut(targetScale = 0.85f, animationSpec = tween(180)))
                    },
                    label = "hero_core_icon"
                ) { (active, adGuard, _) ->
                    Icon(
                        imageVector = when {
                            !active -> Icons.Default.PowerSettingsNew
                            adGuard -> Icons.Default.Shield
                            else -> Icons.Default.Bolt
                        },
                        contentDescription = "Pulse Status",
                        tint = activeGlowColor,
                        modifier = Modifier.size(40.dp)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = if (isActive) "PROTECTED" else "OFFLINE",
                    color = if (isActive) activeGlowColor else TextSecondary,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp,
                    letterSpacing = 1.6.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(activeGlowColor.copy(alpha = if (isActive) 0.16f else 0.08f))
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = if (isActive) {
                            if (latencyMs != null && latencyMs > 0) "${latencyMs} ms" else "ACTIVE"
                        } else {
                            "Tap to Ignite"
                        },
                        color = if (isActive) TextPrimary else TextSecondary,
                        fontSize = 11.sp,
                        fontFamily = if (isActive) FontFamily.Monospace else FontFamily.Default,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
