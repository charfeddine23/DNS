package com.example.ui.components

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DnsServerProfile
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonPulse
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldMint
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassBorderLuminous
import com.example.ui.theme.GlassSurfaceVariant
import com.example.ui.theme.LocalSimpleMode
import com.example.ui.theme.OnAccentInk
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun BenchmarkTelemetryCard(
    servers: List<DnsServerProfile>,
    isPinging: Boolean,
    onPingAll: () -> Unit,
    onAutoSelectFastest: () -> Unit,
    onAddCustomClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val simpleMode = LocalSimpleMode.current
    val measuredServers = remember(servers) {
        servers.filter { it.latencyMs != null && it.latencyMs > 0 }
    }
    val fastestServer = remember(measuredServers) {
        measuredServers.minByOrNull { it.latencyMs ?: Long.MAX_VALUE }
    }
    val averagePingMs = remember(measuredServers) {
        if (measuredServers.isEmpty()) null
        else measuredServers.mapNotNull { it.latencyMs }.average().toLong()
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 22.dp,
            borderColor = GlassBorderLuminous,
            breathingGlow = !simpleMode && (isPinging || fastestServer != null)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(ElectricCyan.copy(alpha = 0.16f))
                                .border(1.dp, ElectricCyan.copy(alpha = 0.35f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = null,
                                tint = ElectricCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "DNS BENCHMARK",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                letterSpacing = 0.9.sp
                            )
                            Text(
                                text = "${measuredServers.size}/${servers.size} resolvers measured",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onAddCustomClick,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("add_custom_server_btn")
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(ElectricCyan.copy(alpha = 0.15f), CircleShape)
                                    .border(1.dp, ElectricCyan.copy(alpha = 0.40f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Add Custom DNS",
                                    tint = ElectricCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Button(
                            onClick = onPingAll,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElectricCyan.copy(alpha = 0.16f),
                                contentColor = ElectricCyan
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            modifier = Modifier
                                .height(40.dp)
                                .testTag("benchmark_refresh_btn")
                        ) {
                            if (isPinging) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = ElectricCyan
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Pinging", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text("Ping All", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    BenchmarkStatTile(
                        label = "FASTEST",
                        value = fastestServer?.latencyMs?.let { "$it ms" } ?: "-- ms",
                        sub = fastestServer?.name ?: "Pending",
                        accent = EmeraldMint,
                        modifier = Modifier.weight(1f)
                    )
                    BenchmarkStatTile(
                        label = "AVERAGE",
                        value = averagePingMs?.let { "$it ms" } ?: "-- ms",
                        sub = "${measuredServers.size}/${servers.size} Online",
                        accent = ElectricCyan,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    BenchmarkLegendItem(color = EmeraldMint, label = "< 40ms", status = "Optimal")
                    BenchmarkLegendItem(color = AmberWarning, label = "40-100ms", status = "Balanced")
                    BenchmarkLegendItem(color = CrimsonPulse, label = "> 100ms", status = "Slow")
                }
            }
        }

        Button(
            onClick = onAutoSelectFastest,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("servers_auto_select_btn"),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = EmeraldMint
            ),
            contentPadding = PaddingValues(0.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                EmeraldMint.copy(alpha = 0.22f),
                                ElectricCyan.copy(alpha = 0.16f)
                            )
                        ),
                        shape = RoundedCornerShape(14.dp)
                    )
                    .border(
                        width = 1.dp,
                        color = EmeraldMint.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(14.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = EmeraldMint
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Auto-Select & Switch to Fastest Server",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = EmeraldMint
                    )
                }
            }
        }
    }
}

@Composable
private fun BenchmarkStatTile(
    label: String,
    value: String,
    sub: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(GlassSurfaceVariant)
            .border(1.dp, accent.copy(alpha = 0.28f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            text = label,
            color = TextSecondary,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            color = accent,
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = sub,
            color = TextPrimary.copy(alpha = 0.85f),
            fontSize = 11.sp,
            maxLines = 1
        )
    }
}

@Composable
private fun BenchmarkLegendItem(color: Color, label: String, status: String) {
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
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("custom_name_input"),
                colors = customBenchmarkTextFieldColors()
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = primaryIpv4,
                onValueChange = { primaryIpv4 = it },
                label = { Text("Primary IPv4 (e.g. 45.90.28.0)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("custom_ipv4_input"),
                colors = customBenchmarkTextFieldColors()
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = secondaryIpv4,
                onValueChange = { secondaryIpv4 = it },
                label = { Text("Secondary IPv4 (Optional)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("custom_secondary_ipv4_input"),
                colors = customBenchmarkTextFieldColors()
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = dotHost,
                onValueChange = { dotHost = it },
                label = { Text("DoT Hostname (Optional, e.g. dns.nextdns.io)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("custom_dot_input"),
                colors = customBenchmarkTextFieldColors()
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
                    contentColor = OnAccentInk
                )
            ) {
                Text("Save Server Profile", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun customBenchmarkTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = ElectricCyan,
    unfocusedBorderColor = GlassBorder,
    focusedLabelColor = ElectricCyan,
    unfocusedLabelColor = TextSecondary,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary
)
