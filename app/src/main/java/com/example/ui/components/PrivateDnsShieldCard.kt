package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.PrivateDnsPreset
import com.example.ui.theme.DeepVoidBlack
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

    val accentColor by animateColorAsState(
        targetValue = if (isAdGuardActive) EmeraldMint else ElectricCyan,
        animationSpec = tween(500),
        label = "shield_card_accent"
    )

    GlassCard(
        modifier = modifier.fillMaxWidth(),
        borderColor = if (isAdGuardActive) EmeraldMint else GlassBorderLuminous,
        borderWidth = if (isAdGuardActive) 1.5.dp else 1.dp,
        breathingGlow = isAdGuardActive || isCustomStrictActive
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(
                                color = accentColor.copy(alpha = 0.16f),
                                shape = CircleShape
                            )
                            .border(1.dp, accentColor.copy(alpha = 0.35f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "Private DNS Shield",
                            tint = accentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Private DNS (DoT)",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = if (privateDnsState.hasWriteSecureSettings)
                                "Direct 1-Tap Unlocked (Shizuku/ADB)"
                            else
                                "Standard Mode",
                            color = if (privateDnsState.hasWriteSecureSettings) EmeraldMint else TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }

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

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(GlassSurfaceVariant)
                    .border(
                        width = 1.dp,
                        color = if (isCustomStrictActive) EmeraldMint.copy(alpha = 0.35f) else GlassBorder,
                        shape = RoundedCornerShape(14.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "SYSTEM STATUS",
                        fontSize = 10.sp,
                        color = TextSecondary,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = when (privateDnsState.mode) {
                            is PrivateDnsMode.Strict -> "Strict DoT: ${privateDnsState.hostname ?: "Unknown"}"
                            is PrivateDnsMode.Opportunistic -> "Auto (Opportunistic ISP DNS)"
                            is PrivateDnsMode.Off -> "Disabled / Standard Unencrypted"
                            is PrivateDnsMode.Unknown -> "Mode: ${privateDnsState.mode.value}"
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

            val adguardPreset = presets.firstOrNull { it.isAdGuardShield } ?: presets.first()
            val ctaInteraction = remember { MutableInteractionSource() }
            val ctaPressed by ctaInteraction.collectIsPressedAsState()
            val ctaScale by animateFloatAsState(
                targetValue = if (ctaPressed) 0.97f else 1f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "adguard_cta_scale"
            )

            Button(
                onClick = {
                    if (isAdGuardActive) onDisablePrivateDns() else onActivatePreset(adguardPreset)
                },
                interactionSource = ctaInteraction,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .scale(ctaScale)
                    .testTag("one_tap_adguard_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    contentColor = DeepVoidBlack
                ),
                contentPadding = PaddingValues(0.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = if (isAdGuardActive) {
                                    listOf(EmeraldMint, ElectricCyan)
                                } else {
                                    listOf(EmeraldMint, EmeraldMint.copy(alpha = 0.85f))
                                }
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = DeepVoidBlack
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isAdGuardActive)
                                "AdGuard Shield Active (Tap to Disable)"
                            else
                                "One-Tap AdGuard (dns.adguard.com)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = DeepVoidBlack
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

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
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected) ElectricCyan.copy(alpha = 0.16f) else GlassSurfaceVariant
                            )
                            .border(
                                width = if (isSelected) 1.3.dp else 1.dp,
                                color = if (isSelected) ElectricCyan else GlassBorder,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(color = ElectricCyan),
                                onClick = { onActivatePreset(preset) }
                            )
                            .padding(horizontal = 13.dp, vertical = 9.dp)
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

            if (!privateDnsState.hasWriteSecureSettings) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(ElectricCyan.copy(alpha = 0.10f))
                        .border(1.dp, ElectricCyan.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
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
