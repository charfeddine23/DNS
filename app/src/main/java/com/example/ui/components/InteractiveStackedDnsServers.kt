package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import com.example.data.model.DnsServerProfile
import com.example.data.model.LatencyStatus
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonPulse
import com.example.ui.theme.DeepVoidBlack
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldMint
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassSurface
import com.example.ui.theme.GlassSurfaceVariant
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Interactive 3D Stacked DNS Server Deck with depth, scale, and alpha opacity gradients.
 * Supports vertical drag-and-release with spring physics: the card settling at Index 0
 * is automatically selected, highlighted in Emerald Mint (#00E676), and activated.
 */
@Composable
fun InteractiveStackedDnsServers(
    servers: List<DnsServerProfile>,
    selectedServerId: String,
    isEngineActive: Boolean,
    onServerSettledOnTop: (DnsServerProfile) -> Unit,
    modifier: Modifier = Modifier
) {
    if (servers.isEmpty()) return

    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val dragThresholdPx = with(density) { 46.dp.toPx() }
    val dragOffsetY = remember { Animatable(0f) }

    // Maintain a deck order where Index 0 corresponds to the top active card
    val orderedIds = remember { mutableStateListOf<String>() }

    // Sync deck order when servers list or external selection changes
    LaunchedEffect(servers.map { it.id }, selectedServerId) {
        val validIds = servers.map { it.id }
        if (orderedIds.isEmpty() || orderedIds.toSet() != validIds.toSet()) {
            orderedIds.clear()
            val selectedFirst = validIds.filter { it == selectedServerId }
            val rest = validIds.filterNot { it == selectedServerId }
            orderedIds.addAll(selectedFirst + rest)
        } else if (orderedIds.firstOrNull() != selectedServerId && validIds.contains(selectedServerId)) {
            val idx = orderedIds.indexOf(selectedServerId)
            if (idx > 0) {
                val rotated = orderedIds.subList(idx, orderedIds.size).toList() +
                    orderedIds.subList(0, idx).toList()
                orderedIds.clear()
                orderedIds.addAll(rotated)
            }
        }
    }

    val serverMap = remember(servers) { servers.associateBy { it.id } }
    val deck = orderedIds.mapNotNull { serverMap[it] }.ifEmpty { servers }
    val visibleCount = minOf(4, deck.size)

    fun rotateNext() {
        if (orderedIds.size <= 1) return
        val first = orderedIds.removeAt(0)
        orderedIds.add(first)
        val topServer = orderedIds.firstOrNull()?.let { serverMap[it] } ?: return
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        onServerSettledOnTop(topServer)
    }

    fun rotatePrevious() {
        if (orderedIds.size <= 1) return
        val last = orderedIds.removeAt(orderedIds.lastIndex)
        orderedIds.add(0, last)
        val topServer = orderedIds.firstOrNull()?.let { serverMap[it] } ?: return
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        onServerSettledOnTop(topServer)
    }

    val dragProgress = (abs(dragOffsetY.value) / dragThresholdPx).coerceIn(0f, 1f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("interactive_stacked_dns_servers")
    ) {
        // Section header with quick step controls for one-handed or accessibility use
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.SwapVert,
                    contentDescription = null,
                    tint = EmeraldMint,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Text(
                        text = "DNS RESOLVER STACK",
                        color = TextSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Drag stack vertically & release to activate",
                        color = TextPrimary.copy(alpha = 0.85f),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { rotatePrevious() },
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("stack_prev_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "Previous DNS",
                        tint = TextSecondary
                    )
                }
                IconButton(
                    onClick = { rotateNext() },
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("stack_next_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Next DNS",
                        tint = EmeraldMint
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Stacked 3D Card Deck Container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(178.dp)
                .pointerInput(deck.size) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            scope.launch {
                                val next = (dragOffsetY.value + dragAmount).coerceIn(
                                    -dragThresholdPx * 2.1f,
                                    dragThresholdPx * 2.1f
                                )
                                dragOffsetY.snapTo(next)
                            }
                        },
                        onDragEnd = {
                            val currentOffset = dragOffsetY.value
                            if (currentOffset <= -dragThresholdPx) {
                                rotateNext()
                            } else if (currentOffset >= dragThresholdPx) {
                                rotatePrevious()
                            }
                            scope.launch {
                                dragOffsetY.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(
                                        dampingRatio = 0.68f,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                )
                            }
                        },
                        onDragCancel = {
                            scope.launch {
                                dragOffsetY.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                )
                            }
                        }
                    )
                },
            contentAlignment = Alignment.TopCenter
        ) {
            // Render back-to-front so Index 0 stays strictly on top
            for (index in (visibleCount - 1) downTo 0) {
                val server = deck[index]
                val isTopCard = index == 0

                val targetDepthOffsetDp = if (isTopCard) {
                    0.dp
                } else {
                    val baseOffset = (index * 16).dp
                    val promotedOffset = ((index - 1) * 16).dp
                    lerp(baseOffset.value, promotedOffset.value, dragProgress).dp
                }

                val targetScale = if (isTopCard) {
                    1f - 0.045f * dragProgress
                } else {
                    val baseScale = 1f - (index * 0.055f)
                    val promotedScale = 1f - ((index - 1) * 0.055f)
                    lerp(baseScale, promotedScale, dragProgress)
                }

                val targetAlpha = if (isTopCard) {
                    1f - 0.16f * dragProgress
                } else {
                    val baseAlpha = (1f - (index * 0.20f)).coerceAtLeast(0.32f)
                    val promotedAlpha = (1f - ((index - 1) * 0.20f)).coerceAtLeast(0.32f)
                    lerp(baseAlpha, promotedAlpha, dragProgress)
                }

                val animatedOffsetY by animateDpAsState(
                    targetValue = targetDepthOffsetDp,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "stack_depth_offset_$index"
                )
                val animatedScale by animateFloatAsState(
                    targetValue = targetScale,
                    animationSpec = spring(
                        dampingRatio = 0.78f,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "stack_scale_$index"
                )
                val animatedAlpha by animateFloatAsState(
                    targetValue = targetAlpha,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "stack_alpha_$index"
                )

                val cardYShiftPx = if (isTopCard) dragOffsetY.value.roundToInt() else 0

                StackedDnsCardItem(
                    server = server,
                    isTopCard = isTopCard,
                    isEngineActive = isEngineActive,
                    onClick = {
                        if (!isTopCard) {
                            val idx = orderedIds.indexOf(server.id)
                            if (idx > 0) {
                                val rotated = orderedIds.subList(idx, orderedIds.size).toList() +
                                    orderedIds.subList(0, idx).toList()
                                orderedIds.clear()
                                orderedIds.addAll(rotated)
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onServerSettledOnTop(server)
                            }
                        } else {
                            onServerSettledOnTop(server)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .zIndex((visibleCount - index).toFloat())
                        .offset(y = animatedOffsetY)
                        .offset { IntOffset(0, cardYShiftPx) }
                        .scale(animatedScale)
                        .alpha(animatedAlpha)
                        .graphicsLayer {
                            if (isTopCard) {
                                rotationX = (-dragOffsetY.value / 24f).coerceIn(-8f, 8f)
                            }
                        }
                )
            }
        }
    }
}

@Composable
private fun StackedDnsCardItem(
    server: DnsServerProfile,
    isTopCard: Boolean,
    isEngineActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val latencyColor = when (server.latencyStatus) {
        LatencyStatus.EXCELLENT -> EmeraldMint
        LatencyStatus.MODERATE -> AmberWarning
        LatencyStatus.POOR -> CrimsonPulse
        LatencyStatus.UNKNOWN -> TextSecondary
    }

    val cardBorderColor = if (isTopCard) EmeraldMint else GlassBorder
    val cardBackground = if (isTopCard) GlassSurfaceVariant else GlassSurface

    GlassCard(
        modifier = modifier
            .height(122.dp)
            .testTag(if (isTopCard) "stacked_top_card_${server.id}" else "stacked_card_${server.id}"),
        cornerRadius = 20.dp,
        borderColor = cardBorderColor,
        borderWidth = if (isTopCard) 1.7.dp else 1.dp,
        backgroundColor = cardBackground,
        breathingGlow = isTopCard
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .background(
                                    brush = Brush.radialGradient(
                                        colors = if (isTopCard) {
                                            listOf(
                                                EmeraldMint.copy(alpha = 0.30f),
                                                EmeraldMint.copy(alpha = 0.08f)
                                            )
                                        } else {
                                            listOf(
                                                Color.White.copy(alpha = 0.12f),
                                                Color.White.copy(alpha = 0.04f)
                                            )
                                        }
                                    ),
                                    shape = CircleShape
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isTopCard) EmeraldMint.copy(alpha = 0.6f)
                                    else Color.White.copy(alpha = 0.12f),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = when (server.iconKey) {
                                    "shield" -> Icons.Default.Shield
                                    "bolt" -> Icons.Default.Bolt
                                    "security" -> Icons.Default.Security
                                    "public" -> Icons.Default.Public
                                    else -> Icons.Default.Dns
                                },
                                contentDescription = server.name,
                                tint = if (isTopCard) EmeraldMint else TextPrimary,
                                modifier = Modifier.size(21.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = server.name,
                                    color = if (isTopCard) EmeraldMint else TextPrimary,
                                    fontSize = 15.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (isTopCard) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Active Selected",
                                        tint = EmeraldMint,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Text(
                                text = server.description,
                                color = TextSecondary,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Latency Chip
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(latencyColor.copy(alpha = 0.15f))
                            .border(1.dp, latencyColor.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 9.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = if (server.latencyMs != null && server.latencyMs > 0) "${server.latencyMs} ms" else "-- ms",
                            color = latencyColor,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = buildString {
                            append(server.primaryIpv4)
                            if (server.dotHostname.isNotBlank()) {
                                append("  •  ")
                                append(server.dotHostname)
                            }
                        },
                        color = if (isTopCard) TextPrimary.copy(alpha = 0.9f) else TextSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    if (isTopCard) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(EmeraldMint)
                                .padding(horizontal = 9.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = if (isEngineActive) "ACTIVE SHIELD" else "SELECTED",
                                color = DeepVoidBlack,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.6.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
