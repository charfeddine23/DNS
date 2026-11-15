package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.HourlyTrafficBucket
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonPulse
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldMint
import com.example.ui.theme.HoloViolet
import com.example.ui.theme.LocalSimpleMode
import com.example.ui.theme.SubtleSurfaceFill
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun AdBlockDonutGauge(
    adsReached: Long,
    adsBlocked: Long,
    blockEfficiencyPercent: Int,
    blockRateLabel: String,
    modifier: Modifier = Modifier
) {
    val simpleMode = LocalSimpleMode.current
    val targetFraction = if (adsReached > 0L) {
        (adsBlocked.toFloat() / adsReached.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    val animatedFraction = if (simpleMode) {
        targetFraction
    } else {
        val value by animateFloatAsState(
            targetValue = targetFraction,
            animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
            label = "donut_block_fraction"
        )
        value
    }

    // Snapshot theme tokens before Canvas draw block
    val trackColor = SubtleSurfaceFill
    val blockedColor = EmeraldMint
    val passedColor = CrimsonPulse
    val accentCyan = ElectricCyan

    Box(
        modifier = modifier.size(134.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val strokeWidth = 13.dp.toPx()
            val inset = strokeWidth / 2f + 2.dp.toPx()
            val arcSize = Size(
                width = (size.width - inset * 2f).coerceAtLeast(1f),
                height = (size.height - inset * 2f).coerceAtLeast(1f)
            )
            val topLeft = Offset(inset, inset)

            // Base track
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            if (adsReached > 0L) {
                val blockedSweep = 360f * animatedFraction
                val passedSweep = (360f - blockedSweep).coerceAtLeast(0f)

                if (passedSweep > 1f) {
                    drawArc(
                        color = passedColor.copy(alpha = 0.78f),
                        startAngle = -90f + blockedSweep,
                        sweepAngle = passedSweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }

                if (blockedSweep > 1f) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = listOf(blockedColor, accentCyan, blockedColor)
                        ),
                        startAngle = -90f,
                        sweepAngle = blockedSweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }
            } else {
                drawArc(
                    color = accentCyan.copy(alpha = 0.30f),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth * 0.45f, cap = StrokeCap.Round)
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (adsReached > 0L) "$blockEfficiencyPercent%" else "--",
                color = TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = blockRateLabel,
                color = TextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun HourlyTrafficBarChart(
    buckets: List<HourlyTrafficBucket>,
    modifier: Modifier = Modifier
) {
    val trackColor = SubtleSurfaceFill
    val totalBarColor = ElectricCyan.copy(alpha = 0.32f)
    val adsReachedColor = AmberWarning.copy(alpha = 0.72f)
    val adsBlockedColor = EmeraldMint

    val maxQueries = buckets.maxOfOrNull { it.totalQueries }?.coerceAtLeast(1L) ?: 1L

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(112.dp)
        ) {
            if (buckets.isEmpty()) return@Canvas
            val count = buckets.size
            val gap = 6.dp.toPx()
            val barWidth = ((size.width - gap * (count - 1)) / count).coerceAtLeast(4.dp.toPx())
            val maxBarHeight = size.height - 6.dp.toPx()
            val corner = CornerRadius(4.dp.toPx(), 4.dp.toPx())

            buckets.forEachIndexed { index, bucket ->
                val x = index * (barWidth + gap)
                // Full track backdrop
                drawRoundRect(
                    color = trackColor,
                    topLeft = Offset(x, 4.dp.toPx()),
                    size = Size(barWidth, maxBarHeight),
                    cornerRadius = corner
                )

                if (bucket.totalQueries > 0L) {
                    val totalH = ((bucket.totalQueries.toFloat() / maxQueries.toFloat()) * maxBarHeight)
                        .coerceIn(6.dp.toPx(), maxBarHeight)
                    drawRoundRect(
                        color = totalBarColor,
                        topLeft = Offset(x, size.height - totalH),
                        size = Size(barWidth, totalH),
                        cornerRadius = corner
                    )
                }

                if (bucket.adsDetected > 0L) {
                    val detectedH = ((bucket.adsDetected.toFloat() / maxQueries.toFloat()) * maxBarHeight)
                        .coerceIn(5.dp.toPx(), maxBarHeight)
                    drawRoundRect(
                        color = adsReachedColor,
                        topLeft = Offset(x, size.height - detectedH),
                        size = Size(barWidth, detectedH),
                        cornerRadius = corner
                    )
                }

                if (bucket.adsBlocked > 0L) {
                    val blockedH = ((bucket.adsBlocked.toFloat() / maxQueries.toFloat()) * maxBarHeight)
                        .coerceIn(4.dp.toPx(), maxBarHeight)
                    drawRoundRect(
                        color = adsBlockedColor,
                        topLeft = Offset(x, size.height - blockedH),
                        size = Size(barWidth, blockedH),
                        cornerRadius = corner
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            buckets.forEachIndexed { idx, bucket ->
                val showLabel = idx == 0 || idx == buckets.lastIndex || idx % 3 == 0
                Text(
                    text = if (showLabel) "${bucket.label}h" else "",
                    color = TextSecondary,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ChartLegendDot(
                color = ElectricCyan.copy(alpha = 0.65f),
                label = stringResource(R.string.analytics_legend_dns)
            )
            ChartLegendDot(
                color = AmberWarning,
                label = stringResource(R.string.analytics_legend_ads)
            )
            ChartLegendDot(
                color = EmeraldMint,
                label = stringResource(R.string.analytics_legend_blocked)
            )
        }
    }
}

@Composable
private fun ChartLegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = label,
            color = TextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun CategoryProgressRow(
    label: String,
    count: Long,
    totalAds: Long,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val simpleMode = LocalSimpleMode.current
    val targetProgress = if (totalAds > 0L) (count.toFloat() / totalAds.toFloat()).coerceIn(0f, 1f) else 0f
    val progress = if (simpleMode) {
        targetProgress
    } else {
        val anim by animateFloatAsState(
            targetValue = targetProgress,
            animationSpec = tween(durationMillis = 550, easing = FastOutSlowInEasing),
            label = "category_progress_$label"
        )
        anim
    }
    val pct = if (totalAds > 0L) ((count * 100L) / totalAds).toInt() else 0

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(accentColor)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = label,
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                text = "$count ($pct%)",
                color = TextSecondary,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(7.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(SubtleSurfaceFill)
        ) {
            if (progress > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.coerceAtLeast(0.03f))
                        .height(7.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(accentColor, HoloViolet.copy(alpha = 0.85f))
                            )
                        )
                )
            }
        }
    }
}
