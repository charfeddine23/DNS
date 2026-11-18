package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DataSaverOn
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.DnsEngineMode
import com.example.ui.components.AdBlockDonutGauge
import com.example.ui.components.AnalyticsMiniMetricCard
import com.example.ui.components.CategoryProgressRow
import com.example.ui.components.GlassCard
import com.example.ui.components.HeroStatCounter
import com.example.ui.components.HourlyTrafficBarChart
import com.example.ui.components.InterceptedDomainRow
import com.example.ui.components.RecentDnsEventRow
import com.example.ui.components.formatAnalyticsDataSaved
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonPulse
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldMint
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.HoloViolet
import com.example.ui.theme.LocalBottomDockClearance
import com.example.ui.theme.OnAccentInk
import com.example.ui.theme.SubtleSurfaceBorder
import com.example.ui.theme.SubtleSurfaceFill
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.utils.PrivateDnsMode
import com.example.viewmodel.DnsPulseViewModel
import com.example.viewmodel.HomeUiState
import java.text.NumberFormat

@Composable
fun AnalyticsScreen(
    uiState: HomeUiState,
    viewModel: DnsPulseViewModel,
    onNavigateToShield: () -> Unit,
    modifier: Modifier = Modifier
) {
    val analytics = uiState.analytics
    val numberFmt = remember { NumberFormat.getIntegerInstance() }
    var showResetDialog by remember { mutableStateOf(false) }

    val isDoTActive = uiState.engineMode == DnsEngineMode.PRIVATE_DNS &&
        uiState.privateDnsState.mode is PrivateDnsMode.Strict

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp)
                .padding(horizontal = 16.dp)
                .testTag("analytics_screen"),
            contentPadding = PaddingValues(bottom = LocalBottomDockClearance.current),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.analytics_title),
                            color = TextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = stringResource(R.string.analytics_subtitle),
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }

                    IconButton(
                        onClick = { showResetDialog = true },
                        enabled = analytics.totalQueries > 0L,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(SubtleSurfaceFill)
                            .border(1.dp, SubtleSurfaceBorder, CircleShape)
                            .testTag("analytics_reset_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteSweep,
                            contentDescription = stringResource(R.string.analytics_reset_desc),
                            tint = if (analytics.totalQueries > 0L) CrimsonPulse else TextSecondary
                        )
                    }
                }
            }

            // Live Inspection Status Banner
            item {
                val statusAccent = when {
                    uiState.isVpnActive -> EmeraldMint
                    isDoTActive -> AmberWarning
                    else -> ElectricCyan
                }
                val statusTitle = when {
                    uiState.isVpnActive -> stringResource(R.string.analytics_status_vpn_live)
                    isDoTActive -> stringResource(R.string.analytics_status_dot_note)
                    else -> stringResource(R.string.analytics_status_idle)
                }
                val statusDesc = when {
                    uiState.isVpnActive -> stringResource(R.string.analytics_status_vpn_live_desc)
                    isDoTActive -> stringResource(R.string.analytics_status_dot_note_desc)
                    else -> stringResource(R.string.analytics_status_idle_desc)
                }

                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 18.dp,
                    borderColor = statusAccent.copy(alpha = 0.45f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(statusAccent.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (uiState.isVpnActive) Icons.Filled.Radar else Icons.Filled.Shield,
                                contentDescription = null,
                                tint = statusAccent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = statusTitle,
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = statusDesc,
                                color = TextSecondary,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }

            // Hero Card: Ads Reached vs Ads Blocked + Donut Gauge
            item {
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("analytics_hero_card"),
                    cornerRadius = 24.dp,
                    borderColor = EmeraldMint.copy(alpha = 0.42f),
                    breathingGlow = uiState.isVpnActive
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AdBlockDonutGauge(
                            adsReached = analytics.adsReachedCount,
                            adsBlocked = analytics.adsBlockedCount,
                            blockEfficiencyPercent = analytics.blockEfficiencyPercent,
                            blockRateLabel = stringResource(R.string.analytics_block_rate)
                        )

                        Spacer(modifier = Modifier.width(18.dp))

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            HeroStatCounter(
                                label = stringResource(R.string.analytics_ads_reached),
                                value = numberFmt.format(analytics.adsReachedCount),
                                accent = AmberWarning,
                                icon = Icons.Filled.WarningAmber
                            )
                            HeroStatCounter(
                                label = stringResource(R.string.analytics_ads_blocked),
                                value = numberFmt.format(analytics.adsBlockedCount),
                                accent = EmeraldMint,
                                icon = Icons.Filled.Block
                            )
                            HeroStatCounter(
                                label = stringResource(R.string.analytics_ads_passed),
                                value = numberFmt.format(analytics.adsPassedCount),
                                accent = CrimsonPulse,
                                icon = Icons.Filled.Public
                            )
                        }
                    }
                }
            }

            // Secondary 2x2 Metrics Grid
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AnalyticsMiniMetricCard(
                            title = stringResource(R.string.analytics_total_queries),
                            value = numberFmt.format(analytics.totalQueries),
                            icon = Icons.Filled.Dns,
                            accent = ElectricCyan,
                            modifier = Modifier.weight(1f)
                        )
                        AnalyticsMiniMetricCard(
                            title = stringResource(R.string.analytics_clean_queries),
                            value = numberFmt.format(analytics.cleanQueriesCount),
                            icon = Icons.Filled.CheckCircle,
                            accent = EmeraldMint,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AnalyticsMiniMetricCard(
                            title = stringResource(R.string.analytics_avg_latency),
                            value = if (analytics.avgLatencyMs > 0L) "${analytics.avgLatencyMs} ms" else "--",
                            icon = Icons.Filled.Speed,
                            accent = HoloViolet,
                            modifier = Modifier.weight(1f)
                        )
                        AnalyticsMiniMetricCard(
                            title = stringResource(R.string.analytics_est_saved),
                            value = formatAnalyticsDataSaved(analytics.estimatedDataSavedKb),
                            icon = Icons.Filled.DataSaverOn,
                            accent = AmberWarning,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Empty State CTA if no traffic has been inspected yet
            if (analytics.totalQueries == 0L) {
                item {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 22.dp,
                        borderColor = GlassBorder
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(22.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(ElectricCyan.copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Analytics,
                                    contentDescription = null,
                                    tint = ElectricCyan,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = stringResource(R.string.analytics_empty_title),
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = stringResource(R.string.analytics_empty_desc),
                                color = TextSecondary,
                                fontSize = 12.sp,
                                lineHeight = 18.sp
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = onNavigateToShield,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ElectricCyan,
                                    contentColor = OnAccentInk
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("analytics_go_shield_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Shield,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Shield",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            } else {
                // 12-Hour Traffic & Blocking Chart
                if (analytics.hourlyBuckets.isNotEmpty()) {
                    item {
                        GlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            cornerRadius = 22.dp,
                            borderColor = ElectricCyan.copy(alpha = 0.32f)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = stringResource(R.string.analytics_hourly_title),
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                HourlyTrafficBarChart(buckets = analytics.hourlyBuckets)
                            }
                        }
                    }
                }

                // Threat Category Breakdown
                item {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 22.dp,
                        borderColor = GlassBorder
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.analytics_category_breakdown),
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            CategoryProgressRow(
                                label = stringResource(R.string.analytics_cat_ads),
                                count = analytics.adNetworkHits,
                                totalAds = analytics.adsReachedCount,
                                accentColor = CrimsonPulse
                            )
                            CategoryProgressRow(
                                label = stringResource(R.string.analytics_cat_trackers),
                                count = analytics.trackerHits,
                                totalAds = analytics.adsReachedCount,
                                accentColor = AmberWarning
                            )
                            CategoryProgressRow(
                                label = stringResource(R.string.analytics_cat_telemetry),
                                count = analytics.telemetryHits,
                                totalAds = analytics.adsReachedCount,
                                accentColor = HoloViolet
                            )
                        }
                    }
                }

                // Top Intercepted Domains
                if (analytics.topInterceptedDomains.isNotEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.analytics_top_domains_title),
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    items(analytics.topInterceptedDomains, key = { "top_${it.domain}" }) { item ->
                        InterceptedDomainRow(item = item)
                    }
                }

                // Recent DNS Queries Feed
                if (analytics.recentEvents.isNotEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.analytics_recent_title),
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    itemsIndexed(
                        items = analytics.recentEvents,
                        key = { index, ev -> "ev_${index}_${ev.timestampMs}_${ev.domain}" }
                    ) { _, event ->
                        RecentDnsEventRow(event = event)
                    }
                }
            }
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            containerColor = DarkSurfaceElevated,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = {
                Text(
                    text = stringResource(R.string.analytics_reset_confirm_title),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(text = stringResource(R.string.analytics_reset_confirm_body))
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.resetAnalytics()
                        showResetDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CrimsonPulse,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.testTag("analytics_reset_confirm_button")
                ) {
                    Text(stringResource(R.string.analytics_reset_confirm_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text(
                        text = stringResource(R.string.analytics_cancel),
                        color = TextSecondary
                    )
                }
            }
        )
    }
}
