package com.example.ui.screens

import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DnsEngineMode
import com.example.data.model.PrivateDnsPreset
import com.example.ui.components.DnsServerCard
import com.example.ui.components.ExpertModeBottomSheet
import com.example.ui.components.HeroPulseButton
import com.example.ui.components.PrivateDnsShieldCard
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.DeepVoidBlack
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldMint
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassSurface
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

    // Launcher for VPN permission dialog
    val vpnLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.toggleVpn(context)
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
        uiState.privateDnsState.hostname ?: "Auto"
    } else {
        uiState.activeVpnServerName
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(DeepVoidBlack)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            // App Header Bar with Branding & Shizuku Status Indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(ElectricCyan.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = "DNS Pulse",
                            tint = ElectricCyan,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "DNS PULSE",
                            color = TextPrimary,
                            fontWeight = FontWeight.Black,
                            fontSize = 19.sp,
                            letterSpacing = 1.2.sp
                        )
                        Text(
                            text = "Next-Gen DNS Shield",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }

                val isElevated = uiState.privateDnsState.hasWriteSecureSettings ||
                        uiState.shizukuStatus == com.example.service.ShizukuStatus.AUTHORIZED
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isElevated) EmeraldMint.copy(alpha = 0.15f)
                            else GlassSurface
                        )
                        .clickable { showExpertSheet = true }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("shizuku_status_badge"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isElevated) EmeraldMint
                                    else TextSecondary
                                )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = when {
                                uiState.shizukuStatus == com.example.service.ShizukuStatus.AUTHORIZED -> "SHIZUKU ACTIVE"
                                uiState.privateDnsState.hasWriteSecureSettings -> "1-TAP READY"
                                else -> "EXPERT MODE"
                            },
                            color = if (isElevated) EmeraldMint else TextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
        }

        // Engine Mode Selector Tabs (Mode A: Private DNS / Mode B: Local VPN)
        item {
            TabRow(
                selectedTabIndex = if (uiState.engineMode == DnsEngineMode.PRIVATE_DNS) 0 else 1,
                containerColor = DarkSurfaceElevated,
                contentColor = ElectricCyan,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(
                            tabPositions[if (uiState.engineMode == DnsEngineMode.PRIVATE_DNS) 0 else 1]
                        ),
                        color = ElectricCyan,
                        height = 3.dp
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
            ) {
                Tab(
                    selected = uiState.engineMode == DnsEngineMode.PRIVATE_DNS,
                    onClick = { viewModel.setEngineMode(DnsEngineMode.PRIVATE_DNS) },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Private DNS (DoT)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    },
                    modifier = Modifier.testTag("tab_private_dns")
                )
                Tab(
                    selected = uiState.engineMode == DnsEngineMode.LOCAL_VPN,
                    onClick = { viewModel.setEngineMode(DnsEngineMode.LOCAL_VPN) },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Local VPN Tunnel", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    },
                    modifier = Modifier.testTag("tab_local_vpn")
                )
            }
        }

        // Center Hero Pulse Button
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
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
                            // Local VPN toggle
                            if (uiState.isVpnActive) {
                                viewModel.toggleVpn(context)
                            } else {
                                var prepareFailed = false
                                val prepareIntent = try {
                                    VpnService.prepare(context)
                                } catch (e: Exception) {
                                    prepareFailed = true
                                    android.widget.Toast.makeText(
                                        context,
                                        "VPN permission unavailable: ${e.localizedMessage}",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                    null
                                }
                                if (!prepareFailed) {
                                    if (prepareIntent != null) {
                                        vpnLauncher.launch(prepareIntent)
                                    } else {
                                        viewModel.toggleVpn(context)
                                    }
                                }
                            }
                        }
                    }
                )
            }
        }

        // Dynamic Engine Card
        item {
            if (uiState.engineMode == DnsEngineMode.PRIVATE_DNS) {
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
                // VPN Quick Servers & Benchmark Header
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "LOCAL VPN DNS SERVERS",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "Zero-Root DNS Query Encryption",
                                color = TextPrimary,
                                fontSize = 14.sp,
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
                }
            }
        }

        // In Local VPN mode, render DNS servers cards
        if (uiState.engineMode == DnsEngineMode.LOCAL_VPN) {
            items(uiState.vpnServers) { server ->
                DnsServerCard(
                    profile = server,
                    isSelected = uiState.selectedVpnServerId == server.id,
                    onSelect = {
                        viewModel.selectVpnServer(server.id)
                    }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
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
