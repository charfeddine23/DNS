package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.LatencyStatus
import com.example.ui.components.DnsServerCard
import com.example.ui.components.GlassCard
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonPulse
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.DeepVoidBlack
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldMint
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassSurface
import com.example.ui.theme.GlassSurfaceVariant
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.DnsPulseViewModel
import com.example.viewmodel.HomeUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServersScreen(
    uiState: HomeUiState,
    viewModel: DnsPulseViewModel,
    modifier: Modifier = Modifier
) {
    var showAddCustomDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(DeepVoidBlack)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "DNS Benchmark & Profiles",
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Real-time latency ping analysis across worldwide resolvers",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }

                IconButton(
                    onClick = { showAddCustomDialog = true },
                    modifier = Modifier.testTag("add_custom_server_btn")
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(ElectricCyan.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Custom DNS",
                            tint = ElectricCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // Live Benchmark Summary Card
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = null,
                                tint = ElectricCyan,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "LATENCY BENCHMARK",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                letterSpacing = 1.sp
                            )
                        }

                        Button(
                            onClick = { viewModel.pingAllServers() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElectricCyan.copy(alpha = 0.15f),
                                contentColor = ElectricCyan
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("benchmark_refresh_btn")
                        ) {
                            if (uiState.isPinging) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = ElectricCyan
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Pinging...", fontSize = 11.sp)
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Benchmark All", fontSize = 11.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Speed indicators legend
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        LegendItem(color = EmeraldMint, label = "< 40ms", status = "Fast")
                        LegendItem(color = AmberWarning, label = "40-100ms", status = "Normal")
                        LegendItem(color = CrimsonPulse, label = "> 100ms", status = "Slow")
                    }
                }
            }
        }

        // Auto Select Fastest Action Button
        item {
            Button(
                onClick = { viewModel.autoSelectFastestServer() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("servers_auto_select_btn"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmeraldMint.copy(alpha = 0.2f),
                    contentColor = EmeraldMint
                )
            ) {
                Icon(imageVector = Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Auto-Select & Switch to Fastest Server",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }

        // Servers List
        items(uiState.vpnServers) { server ->
            DnsServerCard(
                profile = server,
                isSelected = uiState.selectedVpnServerId == server.id,
                onSelect = {
                    viewModel.selectVpnServer(server.id)
                }
            )
        }

        item {
            Spacer(modifier = Modifier.height(28.dp))
        }
    }

    if (showAddCustomDialog) {
        AddCustomServerSheet(
            onDismiss = { showAddCustomDialog = false },
            onAdd = { name, p4, s4, p6, dot ->
                viewModel.addCustomServer(name, p4, s4, p6, dot)
                showAddCustomDialog = false
            }
        )
    }
}

@Composable
private fun LegendItem(color: Color, label: String, status: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Column {
            Text(text = label, color = TextPrimary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            Text(text = status, color = TextSecondary, fontSize = 9.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCustomServerSheet(
    onDismiss: () -> Unit,
    onAdd: (name: String, p4: String, s4: String, p6: String, dot: String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf("") }
    var primaryIpv4 by remember { mutableStateOf("") }
    var secondaryIpv4 by remember { mutableStateOf("") }
    var primaryIpv6 by remember { mutableStateOf("") }
    var dotHost by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DarkSurfaceElevated
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(
                text = "Add Custom DNS Profile",
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Profile Name (e.g. NextDNS, Pi-Hole)") },
                modifier = Modifier.fillMaxWidth().testTag("custom_name_input"),
                colors = customTextFieldColors()
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = primaryIpv4,
                onValueChange = { primaryIpv4 = it },
                label = { Text("Primary IPv4 (e.g. 45.90.28.0)") },
                modifier = Modifier.fillMaxWidth().testTag("custom_ipv4_input"),
                colors = customTextFieldColors()
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = secondaryIpv4,
                onValueChange = { secondaryIpv4 = it },
                label = { Text("Secondary IPv4 (Optional)") },
                modifier = Modifier.fillMaxWidth().testTag("custom_secondary_ipv4_input"),
                colors = customTextFieldColors()
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = dotHost,
                onValueChange = { dotHost = it },
                label = { Text("DoT Hostname (Optional, e.g. dns.nextdns.io)") },
                modifier = Modifier.fillMaxWidth().testTag("custom_dot_input"),
                colors = customTextFieldColors()
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    if (primaryIpv4.isNotBlank()) {
                        onAdd(name, primaryIpv4, secondaryIpv4, primaryIpv6, dotHost)
                    }
                },
                enabled = primaryIpv4.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("save_custom_server_btn"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElectricCyan,
                    contentColor = DeepVoidBlack
                )
            ) {
                Text("Save Server Profile", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun customTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = ElectricCyan,
    unfocusedBorderColor = GlassBorder,
    focusedLabelColor = ElectricCyan,
    unfocusedLabelColor = TextSecondary,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary
)
