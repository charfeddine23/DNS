package com.example.ui.screens

import android.net.VpnService
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DnsEngineMode
import com.example.ui.components.DnsServerCard
import com.example.ui.components.GlassCard
import com.example.ui.components.HeroPulseButton
import com.example.ui.components.InteractiveStackedDnsServers
import com.example.ui.theme.CrimsonPulse
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldMint
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassSurface
import com.example.ui.theme.LocalPageActive
import com.example.ui.theme.LocalSimpleMode
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.utils.PrivateDnsMode
import com.example.viewmodel.DnsPulseViewModel
import com.example.viewmodel.HomeUiState

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    viewModel: DnsPulseViewModel,
    onFallbackRequired: (String) -> Unit,
    onLaunchWirelessSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val simpleMode = LocalSimpleMode.current

    val vpnLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.startVpn(context)
        }
    }

    val isDoTActive = uiState.privateDnsState.mode is PrivateDnsMode.Strict
    val isAdGuardDoT = isDoTActive && uiState.privateDnsState.hostname.equals("dns.adguard.com", ignoreCase = true)
    val isAnyActive = if (uiState.engineMode == DnsEngineMode.PRIVATE_DNS) {
        isDoTActive
    } else {
        uiState.isVpnActive
    }

    val activeDisplayTarget = if (uiState.engineMode == DnsEngineMode.PRIVATE_DNS) {
        uiState.privateDnsState.hostname ?: "System Auto"
    } else {
        uiState.activeVpnServerName
    }

    val fastestMs = remember(uiState.vpnServers) {
        uiState.vpnServers.mapNotNull { it.latencyMs }.filter { it > 0L }.minOrNull()
    }

    val pageActive = LocalPageActive.current
    val liveDotPulseState = if (simpleMode || !pageActive || !isAnyActive) {
        remember { androidx.compose.runtime.mutableFloatStateOf(if (isAnyActive) 1f else 0.7f) }
    } else {
        rememberInfiniteTransition(label = "live_dot").animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "live_dot_alpha"
        )
    }

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
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Top Header
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            ElectricCyan.copy(alpha = 0.28f),
                                            ElectricCyan.copy(alpha = 0.08f)
                                        )
                                    )
                                )
                                .border(1.dp, ElectricCyan.copy(alpha = 0.4f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = "DNS Pulse",
                                tint = ElectricCyan,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(11.dp))
                        Column {
                            Text(
                                text = "DNS PULSE",
                                color = TextPrimary,
                                fontWeight = FontWeight.Black,
                                fontSize = 19.sp,
                                letterSpacing = 1.4.sp
                            )
                            Text(
                                text = "Autonomous Cyber Shield",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }

                    UiModeHeaderSwitcher(
                        isSimpleMode = uiState.simpleMode,
                        onSelectSimpleMode = { enabled ->
                            viewModel.setSimpleMode(enabled)
                        }
                    )
                }
            }

            // Engine Mode Switcher (Private DNS DoT vs Local VPN Tunnel)
            item {
                EngineSegmentedSlider(
                    selectedMode = uiState.engineMode,
                    onSelectMode = { viewModel.setEngineMode(it) }
                )
            }

            // Center Living Pulse Reactor
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    HeroPulseButton(
                        isActive = isAnyActive,
                        activeHostOrServer = activeDisplayTarget,
                        latencyMs = uiState.livePingMs ?: fastestMs,
                        isAdGuardShield = isAdGuardDoT,
                        onClick = {
                            if (uiState.engineMode == DnsEngineMode.PRIVATE_DNS) {
                                if (isDoTActive) {
                                    viewModel.disablePrivateDns {
                                        onLaunchWirelessSettings()
                                    }
                                } else {
                                    val adguard = uiState.privatePresets.firstOrNull { it.isAdGuardShield }
                                        ?: uiState.privatePresets.first()
                                    viewModel.activatePrivateDnsPreset(adguard) { host ->
                                        onFallbackRequired(host)
                                    }
                                }
                            } else {
                                if (uiState.isVpnActive) {
                                    viewModel.stopVpn(context)
                                } else {
                                    var prepareFailed = false
                                    val prepareIntent = try {
                                        VpnService.prepare(context)
                                    } catch (e: Exception) {
                                        prepareFailed = true
                                        Toast.makeText(
                                            context,
                                            "VPN permission unavailable: ${e.localizedMessage}",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        null
                                    }
                                    if (!prepareFailed) {
                                        if (prepareIntent != null) {
                                            try {
                                                vpnLauncher.launch(prepareIntent)
                                            } catch (e: Exception) {
                                                Toast.makeText(
                                                    context,
                                                    "VPN permission dialog unavailable: ${e.localizedMessage}",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        } else {
                                            viewModel.startVpn(context)
                                        }
                                    }
                                }
                            }
                        }
                    )
                }
            }

            // Live Telemetry Ribbon
            item {
                val statusTint = when {
                    !isAnyActive -> CrimsonPulse
                    isAdGuardDoT -> EmeraldMint
                    else -> ElectricCyan
                }
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 16.dp,
                    borderColor = statusTint.copy(alpha = 0.45f),
                    breathingGlow = !simpleMode && isAnyActive
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .graphicsLayer {
                                        alpha = if (isAnyActive) liveDotPulseState.value else 0.7f
                                    }
                                    .clip(CircleShape)
                                    .background(statusTint)
                            )
                            Spacer(modifier = Modifier.width(9.dp))
                            Column {
                                Text(
                                    text = if (uiState.engineMode == DnsEngineMode.PRIVATE_DNS)
                                        "ENCRYPTED DoT ENDPOINT"
                                    else
                                        "LOCAL VPN TUNNEL NODE",
                                    color = TextSecondary,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp
                                )
                                Text(
                                    text = activeDisplayTarget,
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        val displayPing = uiState.livePingMs ?: fastestMs
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(ElectricCyan.copy(alpha = 0.14f))
                                .border(1.dp, ElectricCyan.copy(alpha = 0.30f), RoundedCornerShape(10.dp))
                                .clickable { viewModel.pingAllServers() }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .testTag("telemetry_ping_pill")
                        ) {
                            Text(
                                text = if (displayPing != null && displayPing > 0) "$displayPing ms" else "Ping",
                                color = ElectricCyan,
                                fontSize = 11.5.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // 3D Stacked Server Deck (or flat resolver cards in Simple Mode)
            if (!simpleMode) {
                item {
                    InteractiveStackedDnsServers(
                        servers = uiState.vpnServers,
                        selectedServerId = uiState.selectedVpnServerId,
                        isEngineActive = isAnyActive,
                        isPinging = uiState.isPinging,
                        onPingAll = { viewModel.pingAllServers() },
                        onAutoSelectFastest = { viewModel.autoSelectFastestServer() },
                        onServerSettledOnTop = { settledServer ->
                            viewModel.activateServerFromDeck(
                                server = settledServer,
                                context = context,
                                onVpnPermissionRequired = { prepareIntent ->
                                    try {
                                        vpnLauncher.launch(prepareIntent)
                                    } catch (e: Exception) {
                                        Toast.makeText(
                                            context,
                                            "VPN permission dialog unavailable: ${e.localizedMessage}",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                },
                                onPrivateDnsFallback = onFallbackRequired
                            )
                        }
                    )
                }
            } else {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        uiState.vpnServers.forEach { server ->
                            DnsServerCard(
                                profile = server,
                                isSelected = uiState.selectedVpnServerId == server.id,
                                onSelect = { viewModel.selectVpnServer(server.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EngineSegmentedSlider(
    selectedMode: DnsEngineMode,
    onSelectMode: (DnsEngineMode) -> Unit
) {
    val simpleMode = LocalSimpleMode.current
    val selectedIndex = if (selectedMode == DnsEngineMode.PRIVATE_DNS) 0 else 1
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(GlassSurface)
            .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
            .padding(4.dp)
    ) {
        val segmentWidth = maxWidth / 2
        val indicatorOffset = if (simpleMode) {
            segmentWidth * selectedIndex
        } else {
            val animatedOffset by animateDpAsState(
                targetValue = segmentWidth * selectedIndex,
                animationSpec = spring(
                    dampingRatio = 0.80f,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "segment_slide_offset"
            )
            animatedOffset
        }

        Box(
            modifier = Modifier
                .offset(x = indicatorOffset)
                .width(segmentWidth)
                .fillMaxHeight()
                .clip(RoundedCornerShape(12.dp))
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            ElectricCyan.copy(alpha = 0.22f),
                            EmeraldMint.copy(alpha = 0.16f)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    color = ElectricCyan.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(12.dp)
                )
        )

        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(color = ElectricCyan),
                        onClick = { onSelectMode(DnsEngineMode.PRIVATE_DNS) }
                    )
                    .testTag("tab_private_dns"),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = if (selectedIndex == 0) ElectricCyan else TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Private DNS (DoT)",
                        color = if (selectedIndex == 0) TextPrimary else TextSecondary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(color = ElectricCyan),
                        onClick = { onSelectMode(DnsEngineMode.LOCAL_VPN) }
                    )
                    .testTag("tab_local_vpn"),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null,
                        tint = if (selectedIndex == 1) ElectricCyan else TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Local VPN Tunnel",
                        color = if (selectedIndex == 1) TextPrimary else TextSecondary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun UiModeHeaderSwitcher(
    isSimpleMode: Boolean,
    onSelectSimpleMode: (Boolean) -> Unit
) {
    val selectedIndex = if (isSimpleMode) 0 else 1
    Box(
        modifier = Modifier
            .height(48.dp)
            .testTag("ui_mode_header_switcher"),
        contentAlignment = Alignment.Center
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .width(126.dp)
                .height(34.dp)
                .clip(RoundedCornerShape(50))
                .background(GlassSurface)
                .border(
                    width = 1.dp,
                    color = if (isSimpleMode) EmeraldMint.copy(alpha = 0.42f) else ElectricCyan.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(50)
                )
                .padding(3.dp)
        ) {
            val pillWidth = maxWidth / 2
            val indicatorOffset = if (isSimpleMode) {
                0.dp
            } else {
                val animatedOffset by animateDpAsState(
                    targetValue = pillWidth * selectedIndex,
                    animationSpec = spring(
                        dampingRatio = 0.82f,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "header_mode_offset"
                )
                animatedOffset
            }

            Box(
                modifier = Modifier
                    .offset(x = indicatorOffset)
                    .width(pillWidth)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(50))
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = if (isSimpleMode) {
                                listOf(
                                    EmeraldMint.copy(alpha = 0.24f),
                                    EmeraldMint.copy(alpha = 0.14f)
                                )
                            } else {
                                listOf(
                                    ElectricCyan.copy(alpha = 0.28f),
                                    EmeraldMint.copy(alpha = 0.18f)
                                )
                            }
                        )
                    )
                    .border(
                        width = 1.dp,
                        color = if (isSimpleMode) EmeraldMint.copy(alpha = 0.55f) else ElectricCyan.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(50)
                    )
            )

            Row(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(50))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = true, color = EmeraldMint),
                            onClick = { onSelectSimpleMode(true) }
                        )
                        .testTag("mode_simple_btn"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Simple",
                        color = if (isSimpleMode) TextPrimary else TextSecondary,
                        fontSize = 10.5.sp,
                        fontWeight = if (isSimpleMode) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(50))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = true, color = ElectricCyan),
                            onClick = { onSelectSimpleMode(false) }
                        )
                        .testTag("mode_pro_btn"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Pro",
                        color = if (!isSimpleMode) TextPrimary else TextSecondary,
                        fontSize = 10.5.sp,
                        fontWeight = if (!isSimpleMode) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
