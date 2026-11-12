package com.example.ui.components

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DnsServerProfile
import com.example.data.model.LatencyStatus
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonPulse
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldMint
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassSurface
import com.example.ui.theme.GlassSurfaceVariant
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun DnsServerCard(
    profile: DnsServerProfile,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val latencyColor = when (profile.latencyStatus) {
        LatencyStatus.EXCELLENT -> EmeraldMint
        LatencyStatus.MODERATE -> AmberWarning
        LatencyStatus.POOR -> CrimsonPulse
        LatencyStatus.UNKNOWN -> TextSecondary
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) GlassSurfaceVariant else GlassSurface)
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) ElectricCyan else GlassBorder,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { onSelect() }
            .padding(14.dp)
            .testTag("dns_server_card_${profile.id}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(
                        color = if (isSelected) ElectricCyan.copy(alpha = 0.2f) else Color(0x1AFFFFFF),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (profile.iconKey) {
                        "shield" -> Icons.Default.Shield
                        "bolt" -> Icons.Default.Bolt
                        "security" -> Icons.Default.Security
                        "public" -> Icons.Default.Public
                        else -> Icons.Default.Dns
                    },
                    contentDescription = profile.name,
                    tint = if (isSelected) ElectricCyan else TextPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Details
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = profile.name,
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (isSelected) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Active",
                            tint = ElectricCyan,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${profile.primaryIpv4}  ${if (profile.secondaryIpv4.isNotBlank()) "• ${profile.secondaryIpv4}" else ""}",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Latency Badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(latencyColor.copy(alpha = 0.15f))
                    .border(1.dp, latencyColor.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (profile.latencyMs != null && profile.latencyMs > 0) "${profile.latencyMs} ms" else "-- ms",
                    color = latencyColor,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
