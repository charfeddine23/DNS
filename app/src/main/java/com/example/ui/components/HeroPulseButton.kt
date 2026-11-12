package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CrimsonPulse
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldMint
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassSurface
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
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_radar")
    val pulseScale1 by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scale1"
    )
    val pulseAlpha1 by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "alpha1"
    )

    val pulseScale2 by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, delayMillis = 600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scale2"
    )
    val pulseAlpha2 by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, delayMillis = 600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "alpha2"
    )

    val buttonScale = remember { Animatable(1f) }

    val activeGlowColor = when {
        !isActive -> CrimsonPulse
        isAdGuardShield -> EmeraldMint
        else -> ElectricCyan
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(240.dp)
            .testTag("hero_pulse_button_container")
    ) {
        // Concentric animated radar waves when active
        if (isActive) {
            Canvas(modifier = Modifier.size(240.dp)) {
                val centerOffset = Offset(size.width / 2, size.height / 2)
                val baseRadius = size.width / 2.7f

                // Wave 1
                drawCircle(
                    color = activeGlowColor.copy(alpha = pulseAlpha1),
                    radius = baseRadius * pulseScale1,
                    center = centerOffset,
                    style = Stroke(width = 2.dp.toPx())
                )

                // Wave 2
                drawCircle(
                    color = activeGlowColor.copy(alpha = pulseAlpha2),
                    radius = baseRadius * pulseScale2,
                    center = centerOffset,
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }
        }

        // Ambient radial background glow
        Box(
            modifier = Modifier
                .size(175.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            activeGlowColor.copy(alpha = if (isActive) 0.35f else 0.12f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )

        // Core interactive button
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(160.dp)
                .scale(buttonScale.value)
                .clip(CircleShape)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF1F2630),
                            Color(0xFF12161D)
                        )
                    )
                )
                .border(
                    width = 2.dp,
                    brush = Brush.sweepGradient(
                        listOf(
                            activeGlowColor.copy(alpha = 0.9f),
                            Color.White.copy(alpha = 0.15f),
                            activeGlowColor.copy(alpha = 0.6f),
                            activeGlowColor.copy(alpha = 0.9f)
                        )
                    ),
                    shape = CircleShape
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                )
                .testTag("hero_pulse_button")
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = when {
                        !isActive -> Icons.Default.PowerSettingsNew
                        isAdGuardShield -> Icons.Default.Shield
                        else -> Icons.Default.Bolt
                    },
                    contentDescription = "Pulse Status",
                    tint = activeGlowColor,
                    modifier = Modifier.size(38.dp)
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = if (isActive) "PROTECTED" else "OFFLINE",
                    color = if (isActive) activeGlowColor else TextSecondary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    letterSpacing = 1.5.sp
                )

                if (isActive) {
                    Text(
                        text = if (latencyMs != null && latencyMs > 0) "${latencyMs}ms" else "ACTIVE",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold
                    )
                } else {
                    Text(
                        text = "Tap to Pulse",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}
