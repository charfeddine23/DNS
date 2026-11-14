package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Security
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DnsServerProfile
import com.example.data.model.LatencyStatus
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonPulse
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldMint
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassSurface
import com.example.ui.theme.GlassSurfaceVariant
import com.example.ui.theme.LocalSimpleMode
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun DnsServerCard(
    profile: DnsServerProfile,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val simpleMode = LocalSimpleMode.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val pressScale = if (simpleMode) {
        1f
    } else {
        val animatedScale by animateFloatAsState(
            targetValue = if (isPressed) 0.975f else 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            ),
            label = "server_card_scale"
        )
        animatedScale
    }

    val targetLatencyColor = when (profile.latencyStatus) {
        LatencyStatus.EXCELLENT -> EmeraldMint
        LatencyStatus.MODERATE -> AmberWarning
        LatencyStatus.POOR -> CrimsonPulse
        LatencyStatus.UNKNOWN -> TextSecondary
    }
    val latencyColor = if (simpleMode) {
        targetLatencyColor
    } else {
        val animatedColor by animateColorAsState(
            targetValue = targetLatencyColor,
            animationSpec = tween(400),
            label = "latency_color"
        )
        animatedColor
    }

    val targetFillRatio = when (val ms = profile.latencyMs) {
        null -> 0f
        else -> (1f - (ms.coerceIn(5L, 180L) / 200f)).coerceIn(0.15f, 1f)
    }

    val animatedBarFraction = if (simpleMode) {
        targetFillRatio
    } else {
        val animBar by animateFloatAsState(
            targetValue = targetFillRatio,
            animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
            label = "latency_bar_fill"
        )
        animBar
    }

    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .scale(pressScale),
        cornerRadius = 18.dp,
        borderColor = if (isSelected) ElectricCyan else GlassBorder,
        borderWidth = if (isSelected) 1.5.dp else 1.dp,
        backgroundColor = if (isSelected) GlassSurfaceVariant else GlassSurface,
        breathingGlow = !simpleMode && isSelected
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(color = ElectricCyan),
                    onClick = onSelect
                )
                .padding(14.dp)
                .testTag("dns_server_card_${profile.id}")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            brush = Brush.radialGradient(
                                colors = if (isSelected) {
                                    listOf(ElectricCyan.copy(alpha = 0.28f), ElectricCyan.copy(alpha = 0.08f))
                                } else {
                                    listOf(Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.04f))
                                }
                            ),
                            shape = CircleShape
                        )
                        .border(
                            width = 1.dp,
                            color = if (isSelected) ElectricCyan.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.08f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when (profile.iconKey) {
                            "shield" -> Icons.Default.Shield
                            "bolt" -> Icons.Default.Bolt
                            "security" -> Icons.Default.Security
                            "public" -> Icons.Default.Public
                            else -> Icons.Default.Dns
                        },
                        contentDescription = profile.name,
                        tint = if (isSelected) ElectricCyan else TextPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = profile.name,
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (isSelected) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(ElectricCyan.copy(alpha = 0.18f))
                                    .padding(3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Active",
                                    tint = ElectricCyan,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${profile.primaryIpv4}  ${if (profile.secondaryIpv4.isNotBlank()) "• ${profile.secondaryIpv4}" else ""}",
                        color = TextSecondary,
                        fontSize = 11.5.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Latency Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(latencyColor.copy(alpha = 0.14f))
                        .border(1.dp, latencyColor.copy(alpha = 0.42f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 9.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (profile.latencyMs != null && profile.latencyMs > 0) "${profile.latencyMs} ms" else "-- ms",
                        color = latencyColor,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (animatedBarFraction > 0f) {
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.06f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedBarFraction)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(50))
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(latencyColor.copy(alpha = 0.65f), latencyColor)
                                )
                            )
                    )
                }
            }
        }
    }
}
