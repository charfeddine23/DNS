package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.calculateTargetValue
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Interactive 3D Infinite Stacked DNS Server Deck with realistic continuous drag physics,
 * momentum/inertia decay, perspective tilt, and haptic detents.
 *
 * Dragging continuously cycles cards indefinitely in real time; releasing settles on the
 * nearest projected card and activates whichever card lands at Index 0.
 */
@Composable
fun InteractiveStackedDnsServers(
    servers: List<DnsServerProfile>,
    selectedServerId: String,
    isEngineActive: Boolean,
    isPinging: Boolean = false,
    onPingAll: () -> Unit = {},
    onAutoSelectFastest: () -> Unit = {},
    onServerSettledOnTop: (DnsServerProfile) -> Unit,
    modifier: Modifier = Modifier
) {
    if (servers.isEmpty()) return

    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val cardStridePx = with(density) { 86.dp.toPx() }
    val cardStepOffsetPx = with(density) { 15.dp.toPx() }
    val peelDropPx = with(density) { 96.dp.toPx() }

    val orderedIds = remember { mutableStateListOf<String>() }
    val deckPosition = remember { Animatable(0f) }
    var isUserInteracting by remember { mutableStateOf(false) }
    var lastDetentSlot by remember { mutableIntStateOf(0) }

    val currentServers by rememberUpdatedState(servers)
    val currentOnSettled by rememberUpdatedState(onServerSettledOnTop)

    // Sync orderedIds only when the set of server IDs changes
    LaunchedEffect(servers.map { it.id }) {
        val validIds = servers.map { it.id }
        if (orderedIds.isEmpty() || orderedIds.toSet() != validIds.toSet()) {
            orderedIds.clear()
            val selectedFirst = validIds.filter { it == selectedServerId }
            val rest = validIds.filterNot { it == selectedServerId }
            orderedIds.addAll(selectedFirst + rest)
            deckPosition.snapTo(0f)
            lastDetentSlot = 0
        }
    }

    val serverMap = remember(servers) { servers.associateBy { it.id } }
    val deck = orderedIds.mapNotNull { serverMap[it] }.ifEmpty { servers }
    val deckCount = deck.size

    // Smoothly align deck if selectedServerId changed externally (e.g., Auto-Select Fastest or Benchmark screen)
    LaunchedEffect(selectedServerId, deckCount) {
        if (deckCount <= 1 || isUserInteracting) return@LaunchedEffect
        val targetIdx = deck.indexOfFirst { it.id == selectedServerId }
        if (targetIdx < 0) return@LaunchedEffect
        val currentSlot = deckPosition.value.roundToInt()
        val currentMod = ((currentSlot % deckCount) + deckCount) % deckCount
        if (currentMod != targetIdx) {
            var diff = targetIdx - currentMod
            if (diff > deckCount / 2) diff -= deckCount
            if (diff < -deckCount / 2) diff += deckCount
            val targetPos = (currentSlot + diff).toFloat()
            lastDetentSlot = targetPos.roundToInt()
            deckPosition.animateTo(
                targetValue = targetPos,
                animationSpec = spring(
                    dampingRatio = 0.80f,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
    }

    fun settleAndActivateAt(targetSlot: Float, initialVelocityCardsPerSec: Float = 0f) {
        scope.launch {
            isUserInteracting = true
            try {
                deckPosition.animateTo(
                    targetValue = targetSlot,
                    initialVelocity = initialVelocityCardsPerSec,
                    animationSpec = spring(
                        dampingRatio = 0.78f,
                        stiffness = Spring.StiffnessLow
                    )
                ) {
                    val detent = value.roundToInt()
                    if (detent != lastDetentSlot) {
                        lastDetentSlot = detent
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                }
            } finally {
                isUserInteracting = false
            }

            if (deck.isNotEmpty()) {
                val finalSlot = deckPosition.value.roundToInt()
                val modIdx = ((finalSlot % deck.size) + deck.size) % deck.size
                val settledServer = deck[modIdx]
                lastDetentSlot = finalSlot
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                currentOnSettled(settledServer)
            }
        }
    }

    fun stepBy(delta: Int) {
        if (deckCount <= 1) return
        val baseSlot = deckPosition.targetValue.roundToInt()
        settleAndActivateAt((baseSlot + delta).toFloat())
    }

    val currentPos = deckPosition.value
    val activeSlotFloat = currentPos.roundToInt()
    val activeModIndex = if (deckCount > 0) ((activeSlotFloat % deckCount) + deckCount) % deckCount else 0
    val maxVisibleDepth = minOf(3, (deckCount - 1).coerceAtLeast(0))

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("interactive_stacked_dns_servers")
    ) {
        // Header with quick Benchmark / Fastest / Step controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
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
                        text = "Hold & drag continuously • Release to activate",
                        color = TextPrimary.copy(alpha = 0.85f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onPingAll,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("ping_all_servers_btn")
                ) {
                    if (isPinging) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = ElectricCyan
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Ping All",
                            tint = ElectricCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onAutoSelectFastest,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("auto_select_fastest_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = "Auto Select Fastest",
                        tint = EmeraldMint,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = { stepBy(-1) },
                    modifier = Modifier
                        .size(34.dp)
                        .testTag("stack_prev_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "Previous DNS",
                        tint = TextSecondary
                    )
                }

                IconButton(
                    onClick = { stepBy(1) },
                    modifier = Modifier
                        .size(34.dp)
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

        // Continuous Infinite 3D Card Deck
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(186.dp)
                .pointerInput(deckCount, cardStridePx) {
                    if (deckCount <= 1) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val velocityTracker = VelocityTracker()
                        var wasRunning = deckPosition.isRunning
                        var liveDeckPos = deckPosition.value

                        val dragStart = awaitVerticalTouchSlopOrCancellation(down.id) { change, overSlop ->
                            change.consume()
                            isUserInteracting = true
                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                            liveDeckPos += (overSlop / cardStridePx)
                            val targetSnap = liveDeckPos
                            scope.launch {
                                if (wasRunning) {
                                    wasRunning = false
                                    deckPosition.stop()
                                }
                                deckPosition.snapTo(targetSnap)
                            }
                            val detent = targetSnap.roundToInt()
                            if (detent != lastDetentSlot) {
                                lastDetentSlot = detent
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }

                        if (dragStart != null) {
                            isUserInteracting = true
                            if (deckPosition.isRunning) {
                                scope.launch { deckPosition.stop() }
                            }
                            val completed = verticalDrag(dragStart.id) { change ->
                                val dy = change.positionChange().y
                                change.consume()
                                velocityTracker.addPosition(change.uptimeMillis, change.position)
                                if (dy != 0f) {
                                    liveDeckPos += (dy / cardStridePx)
                                    val targetSnap = liveDeckPos
                                    scope.launch { deckPosition.snapTo(targetSnap) }
                                    val detent = targetSnap.roundToInt()
                                    if (detent != lastDetentSlot) {
                                        lastDetentSlot = detent
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                }
                            }

                            val velocityPxPerSec = velocityTracker.calculateVelocity().y
                            val velocityCardsPerSec = (velocityPxPerSec / cardStridePx).coerceIn(-14f, 14f)

                            if (completed) {
                                val decay = exponentialDecay<Float>(frictionMultiplier = 2.6f)
                                val projectedEnd = decay.calculateTargetValue(
                                    typeConverter = Float.VectorConverter,
                                    initialValue = liveDeckPos,
                                    initialVelocity = velocityCardsPerSec
                                )
                                val snappedTarget = projectedEnd.roundToInt().toFloat()
                                settleAndActivateAt(snappedTarget, velocityCardsPerSec)
                            } else {
                                settleAndActivateAt(liveDeckPos.roundToInt().toFloat(), 0f)
                            }
                        }
                    }
                },
            contentAlignment = Alignment.TopCenter
        ) {
            // Render cards continuously based on circular phase in [0, N)
            val renderItems = remember(deck, currentPos) {
                deck.mapIndexedNotNull { index, server ->
                    val phase = (((index - currentPos) % deckCount) + deckCount) % deckCount
                    // Keep cards that are visible in front stack OR peeling/wrapping around [N-1, N)
                    val isPeelingZone = phase >= (deckCount - 1).toFloat()
                    val isVisibleStack = phase <= 3.6f
                    if (!isPeelingZone && !isVisibleStack) {
                        null
                    } else {
                        val visualProps = computeRealisticDeckPose(
                            phase = phase,
                            deckCount = deckCount,
                            maxVisibleDepth = maxVisibleDepth,
                            stepOffsetPx = cardStepOffsetPx,
                            peelDropPx = peelDropPx
                        )
                        Triple(server, index, visualProps)
                    }
                }.sortedBy { it.third.zOrder }
            }

            renderItems.forEach { (server, index, pose) ->
                val isTopFocused = index == activeModIndex &&
                    abs(pose.translationY) < cardStepOffsetPx * 0.65f

                StackedDnsCardItem(
                    server = server,
                    isTopCard = isTopFocused,
                    isSettled = !isUserInteracting && isTopFocused,
                    isEngineActive = isEngineActive,
                    onClick = {
                        if (!isTopFocused) {
                            val currentSlot = deckPosition.value.roundToInt()
                            val currentMod = ((currentSlot % deckCount) + deckCount) % deckCount
                            var diff = index - currentMod
                            if (diff > deckCount / 2) diff -= deckCount
                            if (diff < -deckCount / 2) diff += deckCount
                            settleAndActivateAt((currentSlot + diff).toFloat())
                        } else {
                            currentOnSettled(server)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .zIndex(pose.zOrder)
                        .graphicsLayer {
                            translationY = pose.translationY
                            scaleX = pose.scale
                            scaleY = pose.scale
                            alpha = pose.alpha
                            rotationX = pose.rotationX
                            cameraDistance = 12f * density.density
                        }
                )
            }
        }
    }
}

private data class DeckCardPose(
    val translationY: Float,
    val scale: Float,
    val alpha: Float,
    val rotationX: Float,
    val zOrder: Float
)

/**
 * Computes smooth, continuous 3D pose for each card in an infinite circular deck.
 * - phase in [0, deckCount - 1]: resting / promoting up through the stacked layers.
 * - phase in [deckCount - 1, deckCount): peeling smoothly down off the front of the deck,
 *   tilting forward with perspective, then tucking cleanly behind the back layer.
 */
private fun computeRealisticDeckPose(
    phase: Float,
    deckCount: Int,
    maxVisibleDepth: Int,
    stepOffsetPx: Float,
    peelDropPx: Float
): DeckCardPose {
    val peelStart = (deckCount - 1).toFloat()

    // Case 1: Card is peeling off the front toward the back (or unwrapping in reverse)
    if (deckCount > 1 && phase >= peelStart) {
        // u goes 0.0 (at front top) -> 1.0 (tucked behind slot deckCount - 1)
        val u = (deckCount.toFloat() - phase).coerceIn(0f, 1f)
        val backDepth = minOf(deckCount - 1, 3)
        val backY = backDepth * stepOffsetPx
        val backScale = 1f - (backDepth * 0.055f)
        val backAlpha = when {
            deckCount <= 4 -> (1f - backDepth * 0.21f).coerceAtLeast(0.32f)
            else -> 0f
        }
        val backTilt = -backDepth * 1.8f

        return if (u <= 0.5f) {
            // Front-half peel: slides down with finger, lifts slightly in tilt, stays in front
            val t = u / 0.5f // 0 -> 1
            val arc = sin((t * PI / 2.0)).toFloat()
            DeckCardPose(
                translationY = lerp(0f, peelDropPx, arc),
                scale = lerp(1f, 0.92f, arc),
                alpha = lerp(1f, 0.90f, arc),
                rotationX = lerp(0f, -15f, arc),
                zOrder = 200f - u * 10f
            )
        } else {
            // Back-half tuck: passes behind the deck and rises cleanly into the deepest visible layer
            val t = (u - 0.5f) / 0.5f // 0 -> 1
            val smoothT = (1f - cos(t * PI).toFloat()) * 0.5f
            DeckCardPose(
                translationY = lerp(peelDropPx, backY, smoothT),
                scale = lerp(0.92f, backScale, smoothT),
                alpha = if (t < 0.35f) {
                    lerp(0.90f, maxOf(backAlpha, 0.28f), t / 0.35f)
                } else {
                    lerp(maxOf(backAlpha, 0.28f), backAlpha, (t - 0.35f) / 0.65f)
                },
                rotationX = lerp(-15f, backTilt, smoothT),
                zOrder = 10f - u
            )
        }
    }

    // Case 2: Card is waiting in the stack [0 .. deckCount - 1), promoting upward smoothly
    val clampedDepth = phase.coerceIn(0f, 3.6f)
    val y = clampedDepth * stepOffsetPx
    val scale = (1f - clampedDepth * 0.055f).coerceAtLeast(0.78f)
    val baseAlpha = (1f - clampedDepth * 0.21f).coerceAtLeast(0f)
    val alpha = if (clampedDepth > maxVisibleDepth.toFloat() && deckCount > 4) {
        val fadeT = (clampedDepth - 3f) / 0.6f
        lerp(0.37f, 0f, fadeT.coerceIn(0f, 1f))
    } else {
        baseAlpha.coerceAtLeast(0.32f)
    }
    val tilt = -clampedDepth * 1.8f

    return DeckCardPose(
        translationY = y,
        scale = scale,
        alpha = alpha,
        rotationX = tilt,
        zOrder = 100f - clampedDepth * 10f
    )
}

@Composable
private fun StackedDnsCardItem(
    server: DnsServerProfile,
    isTopCard: Boolean,
    isSettled: Boolean,
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
            .height(124.dp)
            .testTag(if (isTopCard) "stacked_top_card_${server.id}" else "stacked_card_${server.id}"),
        cornerRadius = 20.dp,
        borderColor = cardBorderColor,
        borderWidth = if (isTopCard) 1.7.dp else 1.dp,
        backgroundColor = cardBackground,
        breathingGlow = isSettled
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
                                text = if (isEngineActive && isSettled) "ACTIVE SHIELD" else "SELECTED",
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
