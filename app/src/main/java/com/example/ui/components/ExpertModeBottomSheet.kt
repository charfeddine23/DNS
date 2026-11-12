package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.service.ShizukuStatus
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldMint
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassSurfaceVariant
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.utils.PrivateDnsController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpertModeBottomSheet(
    shizukuStatus: ShizukuStatus,
    onRequestShizukuPermission: () -> Unit,
    onGrantViaShizuku: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val adbGrantCommand = PrivateDnsController.getAdbCommand(context)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DarkSurfaceElevated,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .padding(bottom = 36.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(EmeraldMint.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.FlashOn,
                        contentDescription = "Expert Mode",
                        tint = EmeraldMint,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Non-Root Private DNS Bridge",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Bridge system settings via Shizuku API or ADB",
                        color = EmeraldMint,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Shizuku Live Status Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(GlassSurfaceVariant)
                    .border(
                        1.dp,
                        if (shizukuStatus == ShizukuStatus.AUTHORIZED) EmeraldMint else ElectricCyan.copy(alpha = 0.5f),
                        RoundedCornerShape(12.dp)
                    )
                    .padding(14.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (shizukuStatus == ShizukuStatus.AUTHORIZED) Icons.Default.CheckCircle else Icons.Default.Security,
                                contentDescription = null,
                                tint = if (shizukuStatus == ShizukuStatus.AUTHORIZED) EmeraldMint else ElectricCyan,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "SHIZUKU API STATUS",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 1.sp
                            )
                        }

                        Text(
                            text = when (shizukuStatus) {
                                ShizukuStatus.AUTHORIZED -> "ACTIVE & BRIDGED"
                                ShizukuStatus.WAITING_PERMISSION -> "PERMISSION NEEDED"
                                ShizukuStatus.NOT_AVAILABLE -> "NOT RUNNING"
                                ShizukuStatus.UNSUPPORTED -> "INCOMPATIBLE"
                            },
                            color = when (shizukuStatus) {
                                ShizukuStatus.AUTHORIZED -> EmeraldMint
                                ShizukuStatus.WAITING_PERMISSION -> ElectricCyan
                                else -> TextSecondary
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = when (shizukuStatus) {
                            ShizukuStatus.AUTHORIZED ->
                                "Shizuku Service is actively bridging system DNS settings. Private DNS switches silently without root or manual settings prompts."
                            ShizukuStatus.WAITING_PERMISSION ->
                                "Shizuku is running! Tap the button below to authorize DNS Pulse."
                            ShizukuStatus.NOT_AVAILABLE ->
                                "Shizuku is not running on this device. You can start it via Wireless Debugging or use the ADB command below."
                            ShizukuStatus.UNSUPPORTED ->
                                "Shizuku version is incompatible with this API."
                        },
                        color = TextPrimary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )

                    if (shizukuStatus == ShizukuStatus.WAITING_PERMISSION) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = onRequestShizukuPermission,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElectricCyan,
                                contentColor = Color(0xFF090C10)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().testTag("auth_shizuku_btn")
                        ) {
                            Text("Request Shizuku Authorization", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    } else if (shizukuStatus == ShizukuStatus.AUTHORIZED) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = onGrantViaShizuku,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = EmeraldMint.copy(alpha = 0.2f),
                                contentColor = EmeraldMint
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().testTag("permanent_grant_btn")
                        ) {
                            Text("Auto-Grant System Permission via Shizuku", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Step 1: ADB Command
            Text(
                text = "STANDALONE ADB (NO SHIZUKU REQUIRED)",
                color = ElectricCyan,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(6.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(GlassSurfaceVariant)
                    .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Column {
                    Text(
                        text = adbGrantCommand,
                        color = EmeraldMint,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("ADB Grant Command", adbGrantCommand))
                                Toast.makeText(context, "ADB command copied to clipboard!", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElectricCyan.copy(alpha = 0.2f),
                                contentColor = ElectricCyan
                            ),
                            modifier = Modifier.testTag("copy_adb_grant_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Copy Command", fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Got It", color = TextPrimary)
            }
        }
    }
}
