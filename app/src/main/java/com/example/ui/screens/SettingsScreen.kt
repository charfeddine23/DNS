package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.R
import com.example.ui.components.GlassCard
import com.example.ui.components.MultiThemeEngineSheet
import com.example.ui.theme.DeepVoidBlack
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldMint
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.HoloMagenta
import com.example.ui.theme.HoloViolet
import com.example.ui.theme.LocalSimpleMode
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.DnsPulseViewModel
import com.example.viewmodel.HomeUiState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun SettingsScreen(
    uiState: HomeUiState,
    viewModel: DnsPulseViewModel,
    onLaunchWirelessSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val simpleMode = LocalSimpleMode.current
    var showThemeSheet by remember { mutableStateOf(false) }
    val powerManager = remember { context.getSystemService(Context.POWER_SERVICE) as? PowerManager }
    val isBatteryUnrestricted = remember(context) {
        powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Settings & Diagnostics",
            color = TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            text = "Configure themes, living aura, and background protection",
            color = TextSecondary,
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(18.dp))

        SectionLabel("NETWORK & SYSTEM")
        Spacer(modifier = Modifier.height(8.dp))

        // System Private DNS shortcut card
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onLaunchWirelessSettings() }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(ElectricCyan.copy(alpha = 0.15f), CircleShape)
                            .border(1.dp, ElectricCyan.copy(alpha = 0.35f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = "Open Network Settings",
                            tint = ElectricCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Android Network & Internet Settings",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Inspect native Private DNS system status",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))
        SectionLabel("APPEARANCE & MULTI-THEME ENGINE")
        Spacer(modifier = Modifier.height(8.dp))

        // Multi-Theme Engine Card
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            borderColor = ElectricCyan.copy(alpha = if (simpleMode) 0.28f else 0.55f),
            breathingGlow = !simpleMode
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !simpleMode) { showThemeSheet = true }
                    .padding(16.dp)
                    .testTag("multi_theme_engine_card"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(ElectricCyan.copy(alpha = 0.16f), CircleShape)
                            .border(1.dp, ElectricCyan.copy(alpha = 0.45f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = "Multi-Theme Engine",
                            tint = if (simpleMode) TextSecondary else ElectricCyan,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Multi-Theme Engine",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.5.sp
                        )
                        Text(
                            text = if (simpleMode) {
                                "Paused while Simple Mode is enabled"
                            } else {
                                "Active: ${uiState.themeStyle.displayName}"
                            },
                            color = if (simpleMode) TextSecondary else ElectricCyan,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(ElectricCyan.copy(alpha = if (simpleMode) 0.08f else 0.15f))
                        .border(
                            1.dp,
                            ElectricCyan.copy(alpha = if (simpleMode) 0.2f else 0.4f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 9.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = if (simpleMode) "SIMPLE" else uiState.themeStyle.shortBadge,
                        color = if (simpleMode) TextSecondary else ElectricCyan,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.8.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Simple Mode Card
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            borderColor = if (uiState.simpleMode) EmeraldMint.copy(alpha = 0.45f) else GlassBorder
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.setSimpleMode(!uiState.simpleMode) }
                    .padding(16.dp)
                    .testTag("simple_mode_card"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = stringResource(R.string.simple_mode_title),
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.5.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.simple_mode_desc),
                        color = TextSecondary,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
                Switch(
                    checked = uiState.simpleMode,
                    onCheckedChange = { viewModel.setSimpleMode(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = DeepVoidBlack,
                        checkedTrackColor = EmeraldMint
                    ),
                    modifier = Modifier.testTag("simple_mode_switch")
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))
        SectionLabel("ENGINE & AUTOMATION")
        Spacer(modifier = Modifier.height(8.dp))

        // Preferences Toggles
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto-Start VPN On Device Boot",
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Resume local DNS shield automatically on restart",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = uiState.autoStartBoot,
                        onCheckedChange = { viewModel.setAutoStartBoot(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = DeepVoidBlack,
                            checkedTrackColor = ElectricCyan
                        ),
                        modifier = Modifier.testTag("boot_switch")
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Breathing Aurora & Glow",
                            color = if (simpleMode) TextSecondary else TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = if (simpleMode) {
                                "Disabled automatically in Simple Mode"
                            } else {
                                "Living atmospheric nebula & energy pulse field"
                            },
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = if (simpleMode) false else uiState.ambientGlow,
                        onCheckedChange = { viewModel.setAmbientGlow(it) },
                        enabled = !simpleMode,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = DeepVoidBlack,
                            checkedTrackColor = ElectricCyan
                        ),
                        modifier = Modifier.testTag("glow_switch")
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Background Keep-Alive / Battery Optimization Card
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            breathingGlow = !simpleMode && isBatteryUnrestricted
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        try {
                            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                        } catch (_: Exception) {
                            context.startActivity(Intent(Settings.ACTION_SETTINGS))
                        }
                    }
                    .padding(16.dp)
                    .testTag("battery_optimization_card"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    val accent = if (isBatteryUnrestricted) EmeraldMint else ElectricCyan
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(accent.copy(alpha = 0.15f), CircleShape)
                            .border(1.dp, accent.copy(alpha = 0.35f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Background Protection",
                            tint = accent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Background Keep-Alive",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = if (isBatteryUnrestricted)
                                "Unrestricted • Protected from Recent Apps kill"
                            else
                                "Tap to set Battery to Unrestricted for OEM ROMs",
                            color = if (isBatteryUnrestricted) EmeraldMint else TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))
        SectionLabel("UPDATES & DEVELOPER")
        Spacer(modifier = Modifier.height(8.dp))

        // Check for Updates Card
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.checkForUpdates(isManual = true) }
                    .padding(16.dp)
                    .testTag("check_updates_card"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(ElectricCyan.copy(alpha = 0.15f), CircleShape)
                            .border(1.dp, ElectricCyan.copy(alpha = 0.35f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SystemUpdate,
                            contentDescription = "Check for Updates",
                            tint = ElectricCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Check for Updates",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Current version: v${BuildConfig.VERSION_NAME} (GitHub Releases)",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Luxurious, Luminous, Dynamic Developer Showcase Container
        DeveloperShowcaseCard()

        Spacer(modifier = Modifier.height(110.dp))
    }

    if (showThemeSheet) {
        MultiThemeEngineSheet(
            selectedTheme = uiState.themeStyle,
            onSelectTheme = { style -> viewModel.setThemeStyle(style) },
            onDismiss = { showThemeSheet = false }
        )
    }
}

@Composable
private fun DeveloperShowcaseCard() {
    val simpleMode = LocalSimpleMode.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale = if (simpleMode) {
        1f
    } else {
        val animatedScale by animateFloatAsState(
            targetValue = if (isPressed) 0.98f else 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            ),
            label = "dev_card_press_scale"
        )
        animatedScale
    }

    var sweepPhase = 0f
    var orbitRotation = 0f
    var pulseGlow = 0.65f
    if (!simpleMode) {
        val infiniteTransition = rememberInfiniteTransition(label = "dev_showcase_glow")
        val animSweep by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = (2f * PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 5200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "dev_sweep_phase"
        )
        val animOrbit by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 6500, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "dev_orbit_rotation"
        )
        val animPulse by infiniteTransition.animateFloat(
            initialValue = 0.45f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "dev_pulse_glow"
        )
        sweepPhase = animSweep
        orbitRotation = animOrbit
        pulseGlow = animPulse
    }

    val shape = RoundedCornerShape(24.dp)
    val shiftX = (cos(sweepPhase) + 1f) * 0.5f
    val shiftY = (sin(sweepPhase) + 1f) * 0.5f

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .scale(pressScale)
            .then(
                if (simpleMode) {
                    Modifier
                } else {
                    Modifier.shadow(
                        elevation = 20.dp,
                        shape = shape,
                        ambientColor = ElectricCyan.copy(alpha = 0.25f * pulseGlow),
                        spotColor = EmeraldMint.copy(alpha = 0.32f * pulseGlow)
                    )
                }
            )
            .testTag("developer_showcase_card"),
        cornerRadius = 24.dp,
        borderColor = ElectricCyan.copy(alpha = if (simpleMode) 0.28f else 0.65f),
        borderWidth = if (simpleMode) 1.dp else 1.5.dp,
        breathingGlow = !simpleMode
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (simpleMode) {
                        Modifier
                    } else {
                        Modifier
                            .drawWithContent {
                                drawContent()
                                // Flowing liquid-prism specular sheen across the card
                                drawRect(
                                    brush = Brush.linearGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            ElectricCyan.copy(alpha = 0.09f * pulseGlow),
                                            HoloViolet.copy(alpha = 0.08f * pulseGlow),
                                            EmeraldMint.copy(alpha = 0.06f * pulseGlow),
                                            Color.Transparent
                                        ),
                                        start = Offset(
                                            size.width * (shiftX - 0.5f),
                                            size.height * (shiftY - 0.5f)
                                        ),
                                        end = Offset(
                                            size.width * (shiftX + 0.7f),
                                            size.height * (shiftY + 0.7f)
                                        )
                                    )
                                )
                            }
                            .border(
                                width = 1.4.dp,
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        ElectricCyan.copy(alpha = 0.85f * pulseGlow),
                                        EmeraldMint.copy(alpha = 0.75f),
                                        HoloViolet.copy(alpha = 0.75f),
                                        HoloMagenta.copy(alpha = 0.55f * pulseGlow),
                                        ElectricCyan.copy(alpha = 0.85f)
                                    ),
                                    start = Offset(shiftX * 650f, 0f),
                                    end = Offset(750f - shiftX * 500f, 520f)
                                ),
                                shape = shape
                            )
                    }
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(color = ElectricCyan),
                    onClick = {}
                )
                .padding(18.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header: Orbital Pulse Emblem + Brand / Developer Identity
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(54.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!simpleMode) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val r = size.minDimension * 0.46f
                                val c = Offset(size.width / 2f, size.height / 2f)
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            ElectricCyan.copy(alpha = 0.34f * pulseGlow),
                                            EmeraldMint.copy(alpha = 0.14f),
                                            Color.Transparent
                                        )
                                    ),
                                    radius = size.minDimension * 0.52f,
                                    center = c
                                )
                                drawArc(
                                    color = ElectricCyan.copy(alpha = 0.9f),
                                    startAngle = orbitRotation,
                                    sweepAngle = 110f,
                                    useCenter = false,
                                    topLeft = Offset(c.x - r, c.y - r),
                                    size = Size(r * 2f, r * 2f),
                                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                                )
                                drawArc(
                                    color = EmeraldMint.copy(alpha = 0.85f),
                                    startAngle = orbitRotation + 180f,
                                    sweepAngle = 95f,
                                    useCenter = false,
                                    topLeft = Offset(c.x - r, c.y - r),
                                    size = Size(r * 2f, r * 2f),
                                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(
                                    brush = Brush.linearGradient(
                                        colors = listOf(
                                            ElectricCyan.copy(alpha = 0.28f),
                                            HoloViolet.copy(alpha = 0.25f)
                                        )
                                    )
                                )
                                .border(1.dp, Color.White.copy(alpha = 0.28f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "Pulse Tech",
                                tint = ElectricCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(
                                            ElectricCyan.copy(alpha = 0.18f),
                                            EmeraldMint.copy(alpha = 0.18f)
                                        )
                                    )
                                )
                                .border(0.8.dp, ElectricCyan.copy(alpha = 0.45f), RoundedCornerShape(50))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Bolt,
                                    contentDescription = null,
                                    tint = EmeraldMint,
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.dev_info_studio_badge),
                                    color = ElectricCyan,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 1.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = stringResource(R.string.dev_info_title),
                            color = TextPrimary,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.5.sp,
                            lineHeight = 20.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Glowing Telemetry Metadata Tiles (Version n°, Build °, Last Push)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DevTelemetryTile(
                        label = stringResource(R.string.dev_info_version_label),
                        value = "v${BuildConfig.VERSION_NAME}",
                        accent = ElectricCyan,
                        modifier = Modifier.weight(1f)
                    )
                    DevTelemetryTile(
                        label = stringResource(R.string.dev_info_build_label),
                        value = "${BuildConfig.VERSION_CODE}",
                        accent = EmeraldMint,
                        modifier = Modifier.weight(0.85f)
                    )
                    DevTelemetryTile(
                        label = stringResource(R.string.dev_info_last_push_label),
                        value = BuildConfig.LAST_PUSH_DATE,
                        accent = HoloViolet,
                        modifier = Modifier.weight(1.25f)
                    )
                }
            }
        }
    }
}

@Composable
private fun DevTelemetryTile(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        accent.copy(alpha = 0.14f),
                        Color.White.copy(alpha = 0.04f)
                    )
                )
            )
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 9.dp)
    ) {
        Text(
            text = label,
            color = TextSecondary,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = value,
            color = accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = ElectricCyan.copy(alpha = 0.85f),
        fontSize = 10.5.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(start = 4.dp)
    )
}
