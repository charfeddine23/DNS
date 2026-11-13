package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.example.data.model.PrivateDnsPreset
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldMint
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassBorderLuminous
import com.example.ui.theme.GlassSurfaceVariant
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.utils.PrivateDnsMode
import com.example.utils.PrivateDnsState

@Composable
fun PrivateDnsShieldCard(
    privateDnsState: PrivateDnsState,
    presets: List<PrivateDnsPreset>,
    selectedHostname: String,
    onActivatePreset: (PrivateDnsPreset) -> Unit,
    onDisablePrivateDns: () -> Unit,
    onOpenExpertGuide: () -> Unit,
    onLaunchWirelessSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isAdGuardActive = privateDnsState.mode is PrivateDnsMode.Strict &&
            privateDnsState.hostname.equals("dns.adguard.com", ignoreCase = true)
    val isCustomStrictActive = privateDnsState.mode is PrivateDnsMode.Strict

    GlassCard(
        modifier = modifier.fillMaxWidth(),
        borderColor = if (isAdGuardActive) EmeraldMint else GlassBorderLuminous,
        borderWidth = if (isAdGuardActive) 1.5.dp else 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Header Row: Shield Title & Status Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                color = if (isAdGuardActive) EmeraldMint.copy(alpha = 0.2f) else ElectricCyan.copy(alpha = 0.15f),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "Private DNS Shield",
                            tint = if (isAdGuardActive) EmeraldMint else ElectricCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Private DNS (DoT)",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = if (privateDnsState.hasWriteSecureSettings) "Direct 1-Tap Unlocked (Shizuku/ADB)" else "Standard Mode",
                            color = if (privateDnsState.hasWriteSecureSettings) EmeraldMint else TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }

                // Shizuku/Expert helper button
                IconButton(
                    onClick = onOpenExpertGuide,
                    modifier = Modifier.testTag("expert_mode_info_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Expert Mode Info",
                        tint = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Current State display badge
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(GlassSurfaceVariant)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "SYSTEM STATUS",
                        fontSize = 10.sp,
                        color = TextSecondary,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = when (privateDnsState.mode) {
                            is PrivateDnsMode.Strict -> "Strict DoT: ${privateDnsState.hostname ?: "Unknown"}"
                            is PrivateDnsMode.Opportunistic -> "Auto (Opportunistic ISP DNS)"
                            is PrivateDnsMode.Off -> "Disabled / Standard Unencrypted"
                            is PrivateDnsMode.Unknown -> "Mode: ${(privateDnsState.mode as PrivateDnsMode.Unknown).value}"
                        },
                        fontSize = 13.sp,
                        color = if (isCustomStrictActive) EmeraldMint else TextPrimary,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (isCustomStrictActive) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Active",
                        tint = EmeraldMint,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Primary Highlight: ONE-TAP ADGUARD ACTIVATION BUTTON
            val adguardPreset = presets.firstOrNull { it.isAdGuardShield } ?: presets.first()
            Button(
                onClick = {
                    if (isAdGuardActive) {
                        onDisablePrivateDns()
                    } else {
                        onActivatePreset(adguardPreset)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("one_tap_adguard_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isAdGuardActive) EmeraldMint else EmeraldMint.copy(alpha = 0.9f),
                    contentColor = Color(0xFF090C10)
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isAdGuardActive) "AdGuard Shield Active (Tap to Disable)" else "One-Tap AdGuard (dns.adguard.com)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Horizontal Chip List for other DoT Presets
            Text(
                text = "QUICK DoT PRESETS",
                color = TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(presets) { preset ->
                    val isSelected = privateDnsState.hostname.equals(preset.hostname, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isSelected) ElectricCyan.copy(alpha = 0.15f) else GlassSurfaceVariant
                            )
                            .border(
                                width = 1.dp,
                                color = if (isSelected) ElectricCyan else GlassBorder,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { onActivatePreset(preset) }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .testTag("preset_chip_${preset.id}")
                    ) {
                        Column {
                            Text(
                                text = preset.name,
                                color = if (isSelected) ElectricCyan else TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = preset.hostname,
                                color = TextSecondary,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Fallback guide notice if WRITE_SECURE_SETTINGS not granted
            if (!privateDnsState.hasWriteSecureSettings) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0x1A00E5FF))
                        .clickable { onLaunchWirelessSettings() }
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = "Open Settings",
                        tint = ElectricCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Tapping copies hostname & opens Android Private DNS Settings automatically.",
                        color = ElectricCyan,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }
        }
    }
}
