package com.example.ui.screens

import android.net.VpnService
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DnsEngineMode
import com.example.service.ShizukuStatus
import com.example.ui.components.DnsServerCard
import com.example.ui.components.ExpertModeBottomSheet
import com.example.ui.components.GlassCard
import com.example.ui.components.HeroPulseButton
import com.example.ui.components.PrivateDnsShieldCard
import com.example.ui.theme.CrimsonPulse
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldMint
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassSurface
import com.example.ui.theme.GlassSurfaceVariant
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
    onNavigateToServers: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showExpertSheet by remember { mutableStateOf(false) }

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

    val liveDotPulse by rememberInfiniteTransition(label = "live_dot").animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "live_dot_alpha"
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
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

                val isElevated = uiState.privateDnsState.hasWriteSecureSettings ||
                    uiState.shizukuStatus == ShizukuStatus.AUTHORIZED
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (isElevated) EmeraldMint.copy(alpha = 0.14f)
                            else GlassSurface
                        )
                        .border(
                            width = 1.dp,
                            color = if (isElevated) EmeraldMint.copy(alpha = 0.4f) else GlassBorder,
                            shape = RoundedCornerShape(50)
                        )
                        .clickable { showExpertSheet = true }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                        .testTag("shizuku_status_badge"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .alpha(if (isElevated) liveDotPulse else 0.7f)
                                .clip(CircleShape)
                                .background(if (isElevated) EmeraldMint else TextSecondary)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = when {
                                uiState.shizukuStatus == ShizukuStatus.AUTHORIZED -> "SHIZUKU ACTIVE"
                                uiState.privateDnsState.hasWriteSecureSettings -> "1-TAP READY"
                                else -> "EXPERT MODE"
                            },
                            color = if (isElevated) EmeraldMint else TextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.6.sp
                        )
                    }
                }
            }
        }

        // Sleek Sliding Pill Engine Mode Switcher
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
                    latencyMs = uiState.livePingMs,
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
                                        vpnLauncher.launch(prepareIntent)
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
                breathingGlow = isAnyActive
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
                                .alpha(if (isAnyActive) liveDotPulse else 0.7f)
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

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(ElectricCyan.copy(alpha = 0.12f))
                            .clickable { onNavigateToServers() }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (uiState.livePingMs != null && uiState.livePingMs > 0)
                                "${uiState.livePingMs} ms"
                            else
                                "Ping",
                            color = ElectricCyan,
                            fontSize = 11.5.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Smooth Horizontal Slide Between Private DNS Controls & Local VPN Servers
        item {
            AnimatedContent(
                targetState = uiState.engineMode,
                transitionSpec = {
                    val forward = targetState == DnsEngineMode.LOCAL_VPN
                    (slideInHorizontally(
                        animationSpec = spring(
                            dampingRatio = 0.86f,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ) { width -> if (forward) width else -width } + fadeIn(tween(260)))
                        .togetherWith(
                            slideOutHorizontally(
                                animationSpec = spring(
                                    dampingRatio = 0.86f,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            ) { width -> if (forward) -width else width } + fadeOut(tween(200))
                        )
                },
                label = "engine_panel_slide"
            ) { mode ->
                if (mode == DnsEngineMode.PRIVATE_DNS) {
                    PrivateDnsShieldCard(
                        privateDnsState = uiState.privateDnsState,
                        presets = uiState.privatePresets,
                        selectedHostname = uiState.selectedDotPreset,
                        onActivatePreset = { preset ->
                            viewModel.activatePrivateDnsPreset(preset) { host ->
                                onFallbackRequired(host)
                            }
                        },
                        onDisablePrivateDns = {
                            viewModel.disablePrivateDns {
                                onLaunchWirelessSettings()
                            }
                        },
                        onOpenExpertGuide = { showExpertSheet = true },
                        onLaunchWirelessSettings = onLaunchWirelessSettings
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "LOCAL VPN DNS SERVERS",
                                    color = TextSecondary,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = "Zero-Root DNS Tunneling",
                                    color = TextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { viewModel.pingAllServers() },
                                    modifier = Modifier.testTag("ping_all_servers_btn")
                                ) {
                                    if (uiState.isPinging) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                            color = ElectricCyan
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "Ping All",
                                            tint = ElectricCyan
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = { viewModel.autoSelectFastestServer() },
                                    modifier = Modifier.testTag("auto_select_fastest_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Speed,
                                        contentDescription = "Auto Select Fastest",
                                        tint = EmeraldMint
                                    )
                                }
                            }
                        }

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

    if (showExpertSheet) {
        ExpertModeBottomSheet(
            shizukuStatus = uiState.shizukuStatus,
            onRequestShizukuPermission = { viewModel.requestShizukuAuthorization() },
            onGrantViaShizuku = { viewModel.grantPermissionViaShizuku() },
            onDismiss = { showExpertSheet = false }
        )
    }
}

@Composable
private fun EngineSegmentedSlider(
    selectedMode: DnsEngineMode,
    onSelectMode: (DnsEngineMode) -> Unit
) {
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
        val indicatorOffset by animateDpAsState(
            targetValue = segmentWidth * selectedIndex,
            animationSpec = spring(
                dampingRatio = 0.80f,
                stiffness = Spring.StiffnessMediumLow
            ),
            label = "segment_slide_offset"
        )

        // Sliding luminous pill
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
