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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.example.data.model.DnsServerProfile
import com.example.data.model.LatencyStatus
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonPulse
import com.example.ui.theme.DeepVoidBlack
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldMint
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.StackCardBackSurface
import com.example.ui.theme.StackCardFrontSurface
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sign
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
    val cardStridePx = with(density) { 78.dp.toPx() }
    val cardStepOffsetPx = with(density) { 14.dp.toPx() }
    val peelDropPx = with(density) { 104.dp.toPx() }

    val orderedIds = remember { mutableStateListOf<String>() }
    val deckPosition = remember { Animatable(0f) }
    var directDragPosition by remember { mutableFloatStateOf(0f) }
    var isDraggingDirect by remember { mutableStateOf(false) }
    var isUserInteracting by remember { mutableStateOf(false) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    var lastDetentSlot by remember { mutableIntStateOf(0) }

    val currentOnSettled by rememberUpdatedState(onServerSettledOnTop)

    fun readDeckPhase(): Float = if (isDraggingDirect) directDragPosition else deckPosition.value

    // Sync orderedIds only when the set of server IDs changes
    LaunchedEffect(servers.map { it.id }) {
        val validIds = servers.map { it.id }
        if (orderedIds.isEmpty() || orderedIds.toSet() != validIds.toSet()) {
            orderedIds.clear()
            val selectedFirst = validIds.filter { it == selectedServerId }
            val rest = validIds.filterNot { it == selectedServerId }
            orderedIds.addAll(selectedFirst + rest)
            isDraggingDirect = false
            directDragPosition = 0f
            deckPosition.snapTo(0f)
            lastDetentSlot = 0
        }
    }

    val serverMap = remember(servers) { servers.associateBy { it.id } }
    val deck = orderedIds.mapNotNull { serverMap[it] }.ifEmpty { servers }
    val deckCount = deck.size

    // Smoothly align deck if selectedServerId changed externally (e.g., Auto-Select Fastest or Benchmark screen)
    LaunchedEffect(selectedServerId, deckCount) {
        if (deckCount <= 1 || isUserInteracting || isDraggingDirect) return@LaunchedEffect
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
                    dampingRatio = 0.78f,
                    stiffness = 340f
                )
            )
        }
    }

    fun settleAndActivateFrom(
        startPosition: Float,
        targetSlot: Float,
        initialVelocityCardsPerSec: Float = 0f,
        syncDirectPositionFirst: Boolean = false
    ) {
        settleJob?.cancel()
        settleJob = scope.launch {
            isUserInteracting = true
            try {
                if (syncDirectPositionFirst) {
                    deckPosition.snapTo(startPosition)
                    isDraggingDirect = false
                }

                var handoffVelocity = initialVelocityCardsPerSec
                val travelDistance = abs(targetSlot - deckPosition.value)

                // Stage 1: Natural inertia coast for fast flicks across multiple cards
                if (abs(initialVelocityCardsPerSec) > 2.1f && travelDistance >= 0.85f) {
                    val flickDirection = sign(initialVelocityCardsPerSec)
                    val coastDecay = exponentialDecay<Float>(frictionMultiplier = 2.0f)
                    val decayJob = launch {
                        deckPosition.animateDecay(
                            initialVelocity = initialVelocityCardsPerSec,
                            animationSpec = coastDecay
                        ) {
                            handoffVelocity = velocity
                            val detent = value.roundToInt()
                            if (detent != lastDetentSlot) {
                                lastDetentSlot = detent
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            val remainingTowardTarget = (targetSlot - value) * flickDirection
                            if (remainingTowardTarget <= 0.58f || abs(velocity) <= 1.6f) {
                                cancel()
                            }
                        }
                    }
                    decayJob.join()
                }

                // Stage 2: Weighted magnetic spring settle onto exact Index 0 slot
                deckPosition.animateTo(
                    targetValue = targetSlot,
                    initialVelocity = handoffVelocity,
                    animationSpec = spring(
                        dampingRatio = 0.78f,
                        stiffness = 340f
                    )
                ) {
                    val detent = value.roundToInt()
                    if (detent != lastDetentSlot) {
                        lastDetentSlot = detent
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                }
            } finally {
                isDraggingDirect = false
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
        val basePos = readDeckPhase()
        val baseSlot = basePos.roundToInt()
        settleAndActivateFrom(
            startPosition = basePos,
            targetSlot = (baseSlot + delta).toFloat(),
            syncDirectPositionFirst = isDraggingDirect
        )
    }

    // Derived discrete states avoid full card recomposition during 90/120fps drag & physics frames
    val activeModIndex by remember(deckCount) {
        derivedStateOf {
            val slot = readDeckPhase().roundToInt()
            if (deckCount > 0) ((slot % deckCount) + deckCount) % deckCount else 0
        }
    }
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

        // Continuous Infinite 3D Card Deck (0 recompositions per drag/physics frame)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(192.dp)
                .pointerInput(deckCount, cardStridePx) {
                    if (deckCount <= 1) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val velocityTracker = VelocityTracker()
                        var liveDeckPos = readDeckPhase()

                        val dragStart = awaitVerticalTouchSlopOrCancellation(down.id) { change, overSlop ->
                            change.consume()
                            settleJob?.cancel()
                            isUserInteracting = true
                            directDragPosition = liveDeckPos
                            isDraggingDirect = true
                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                            liveDeckPos += (overSlop / cardStridePx)
                            directDragPosition = liveDeckPos
                            val detent = liveDeckPos.roundToInt()
                            if (detent != lastDetentSlot) {
                                lastDetentSlot = detent
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }

                        if (dragStart != null) {
                            val completed = verticalDrag(dragStart.id) { change ->
                                val dy = change.positionChange().y
                                change.consume()
                                velocityTracker.addPosition(change.uptimeMillis, change.position)
                                if (dy != 0f) {
                                    liveDeckPos += (dy / cardStridePx)
                                    directDragPosition = liveDeckPos
                                    val detent = liveDeckPos.roundToInt()
                                    if (detent != lastDetentSlot) {
                                        lastDetentSlot = detent
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                }
                            }

                            val velocityPxPerSec = velocityTracker.calculateVelocity().y
                            val velocityCardsPerSec = (velocityPxPerSec / cardStridePx).coerceIn(-18f, 18f)

                            if (completed) {
                                val decay = exponentialDecay<Float>(frictionMultiplier = 2.0f)
                                val projectedEnd = decay.calculateTargetValue(
                                    typeConverter = Float.VectorConverter,
                                    initialValue = liveDeckPos,
                                    initialVelocity = velocityCardsPerSec
                                )
                                settleAndActivateFrom(
                                    startPosition = liveDeckPos,
                                    targetSlot = projectedEnd.roundToInt().toFloat(),
                                    initialVelocityCardsPerSec = velocityCardsPerSec,
                                    syncDirectPositionFirst = true
                                )
                            } else {
                                settleAndActivateFrom(
                                    startPosition = liveDeckPos,
                                    targetSlot = liveDeckPos.roundToInt().toFloat(),
                                    initialVelocityCardsPerSec = 0f,
                                    syncDirectPositionFirst = true
                                )
                            }
                        }
                    }
                },
            contentAlignment = Alignment.TopCenter
        ) {
            deck.forEachIndexed { index, server ->
                key(server.id) {
                    val isTopFocused = index == activeModIndex

                    StackedDnsCardItem(
                        server = server,
                        isTopCard = isTopFocused,
                        isSettled = !isUserInteracting && isTopFocused,
                        isEngineActive = isEngineActive,
                        onClick = {
                            if (!isTopFocused) {
                                val basePos = readDeckPhase()
                                val currentSlot = basePos.roundToInt()
                                val currentMod = ((currentSlot % deckCount) + deckCount) % deckCount
                                var diff = index - currentMod
                                if (diff > deckCount / 2) diff -= deckCount
                                if (diff < -deckCount / 2) diff += deckCount
                                settleAndActivateFrom(
                                    startPosition = basePos,
                                    targetSlot = (currentSlot + diff).toFloat(),
                                    syncDirectPositionFirst = isDraggingDirect
                                )
                            } else {
                                currentOnSettled(server)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .layout { measurable, constraints ->
                                val placeable = measurable.measure(constraints)
                                layout(placeable.width, placeable.height) {
                                    val pos = readDeckPhase()
                                    val phase = (((index - pos) % deckCount) + deckCount) % deckCount
                                    val pose = computeRealisticDeckPose(
                                        phase = phase,
                                        deckCount = deckCount,
                                        maxVisibleDepth = maxVisibleDepth,
                                        stepOffsetPx = cardStepOffsetPx,
                                        peelDropPx = peelDropPx,
                                        densityScale = density.density
                                    )
                                    placeable.placeWithLayer(0, 0, zIndex = pose.zOrder) {
                                        translationY = pose.translationY
                                        scaleX = pose.scale
                                        scaleY = pose.scale
                                        alpha = pose.alpha
                                        rotationX = pose.rotationX
                                        transformOrigin = TransformOrigin(0.5f, 0.28f)
                                        cameraDistance = 18f * density.density
                                        shadowElevation = pose.shadowElevationPx
                                    }
                                }
                            }
                    )
                }
            }
        }
    }
}

private data class DeckCardPose(
    val translationY: Float,
    val scale: Float,
    val alpha: Float,
    val rotationX: Float,
    val zOrder: Float,
    val shadowElevationPx: Float
)

/**
 * Computes smooth, continuous 3D pose for each card in an infinite circular deck.
 * - phase in [0, deckCount - 1]: resting / promoting up through the stacked layers.
 * - phase in [deckCount - 1, deckCount): peeling smoothly down off the front of the deck,
 *   tilting forward with top-spine perspective, then tucking cleanly behind the back layer.
 */
private fun computeRealisticDeckPose(
    phase: Float,
    deckCount: Int,
    maxVisibleDepth: Int,
    stepOffsetPx: Float,
    peelDropPx: Float,
    densityScale: Float
): DeckCardPose {
    val peelStart = (deckCount - 1).toFloat()

    // Case 1: Card is peeling off the front toward the back (or emerging smoothly when dragged upward)
    if (deckCount > 1 && phase >= peelStart) {
        // u goes 0.0 (at front top) -> 1.0 (tucked behind slot deckCount - 1)
        val u = (deckCount.toFloat() - phase).coerceIn(0f, 1f)
        val backDepth = minOf(deckCount - 1, 3)
        val backY = backDepth * stepOffsetPx
        val backScale = 1f - (backDepth * 0.052f)
        val backAlpha = when {
            deckCount <= 4 -> (1f - backDepth * 0.21f).coerceAtLeast(0.32f)
            else -> 0f
        }
        val backTilt = -backDepth * 2.1f

        return if (u <= 0.48f) {
            // Front-half peel: follows finger downward in a natural curved arc with realistic forward tilt
            val t = u / 0.48f
            val arc = sin((t * PI / 2.0)).toFloat()
            DeckCardPose(
                translationY = lerp(0f, peelDropPx, arc),
                scale = lerp(1f, 0.90f, arc),
                alpha = lerp(1f, 0.92f, arc),
                rotationX = lerp(0f, -17.5f, arc),
                zOrder = 200f - u * 10f,
                shadowElevationPx = lerp(10f, 16f, arc) * densityScale
            )
        } else {
            // Back-half tuck: slips behind the stack cleanly and glides up into the backmost layer
            val t = (u - 0.48f) / 0.52f
            val smoothT = (1f - cos(t * PI).toFloat()) * 0.5f
            DeckCardPose(
                translationY = lerp(peelDropPx, backY, smoothT),
                scale = lerp(0.90f, backScale, smoothT),
                alpha = if (t < 0.35f) {
                    lerp(0.92f, maxOf(backAlpha, 0.30f), t / 0.35f)
                } else {
                    lerp(maxOf(backAlpha, 0.30f), backAlpha, (t - 0.35f) / 0.65f)
                },
                rotationX = lerp(-17.5f, backTilt, smoothT),
                zOrder = 10f - u,
                shadowElevationPx = lerp(8f, 1.5f, smoothT) * densityScale
            )
        }
    }

    // Hidden distant cards in decks larger than 4
    if (deckCount > 4 && phase > 3.6f) {
        return DeckCardPose(
            translationY = 3.6f * stepOffsetPx,
            scale = 0.78f,
            alpha = 0f,
            rotationX = -7.5f,
            zOrder = -10f,
            shadowElevationPx = 0f
        )
    }

    // Case 2: Card is promoting up through the front stack layers [0 .. 3.6]
    val clampedDepth = phase.coerceIn(0f, 3.6f)
    val y = clampedDepth * stepOffsetPx
    val scale = (1f - clampedDepth * 0.052f).coerceAtLeast(0.78f)
    val baseAlpha = (1f - clampedDepth * 0.21f).coerceAtLeast(0f)
    val alpha = if (clampedDepth > maxVisibleDepth.toFloat() && deckCount > 4) {
        val fadeT = (clampedDepth - 3f) / 0.6f
        lerp(0.37f, 0f, fadeT.coerceIn(0f, 1f))
    } else {
        baseAlpha.coerceAtLeast(0.32f)
    }
    val tilt = -clampedDepth * 2.1f
    val elevation = (10f - clampedDepth * 2.2f).coerceAtLeast(1.5f) * densityScale

    return DeckCardPose(
        translationY = y,
        scale = scale,
        alpha = alpha,
        rotationX = tilt,
        zOrder = 100f - clampedDepth * 10f,
        shadowElevationPx = elevation
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
    val cardBackground = if (isTopCard) StackCardFrontSurface else StackCardBackSurface

    GlassCard(
        modifier = modifier
            .height(124.dp)
            .testTag(if (isTopCard) "stacked_top_card_${server.id}" else "stacked_card_${server.id}"),
        cornerRadius = 20.dp,
        borderColor = cardBorderColor,
        borderWidth = if (isTopCard) 1.7.dp else 1.dp,
        backgroundColor = cardBackground,
        breathingGlow = isSettled,
        surfaceAlphaOverride = 0.90f
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
