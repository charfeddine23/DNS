package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.example.R
import com.example.data.model.AppAuthenticatorConfig
import com.example.data.model.InstalledAppItem
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldMint
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassSurface
import com.example.ui.theme.GlassSurfaceVariant
import com.example.ui.theme.HoloViolet
import com.example.ui.theme.LocalSimpleMode
import com.example.ui.theme.OnAccentInk
import com.example.ui.theme.SubtleSurfaceBorder
import com.example.ui.theme.SubtleSurfaceFill
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

private object AppIconBitmapCache {
    private val cache = ConcurrentHashMap<String, Bitmap>()

    fun get(packageName: String): Bitmap? = cache[packageName]

    fun put(packageName: String, bitmap: Bitmap) {
        if (cache.size > 160) {
            cache.clear()
        }
        cache[packageName] = bitmap
    }
}

@Composable
fun formatRelockDelayLabel(seconds: Int): String = when (seconds) {
    0 -> stringResource(R.string.auth_relock_instant)
    15 -> stringResource(R.string.auth_relock_15s)
    30 -> stringResource(R.string.auth_relock_30s)
    60 -> stringResource(R.string.auth_relock_1m)
    300 -> stringResource(R.string.auth_relock_5m)
    900 -> stringResource(R.string.auth_relock_15m)
    else -> stringResource(R.string.auth_relock_custom_fmt, seconds)
}

@Composable
fun AuthenticatorHeroGuardCard(
    config: AppAuthenticatorConfig,
    onToggleMasterGuard: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val simpleMode = LocalSimpleMode.current
    val guardAccent = if (config.isMasterGuardEnabled) EmeraldMint else ElectricCyan

    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("auth_hero_guard_card"),
        cornerRadius = 24.dp,
        borderColor = guardAccent.copy(alpha = if (config.isMasterGuardEnabled) 0.52f else 0.34f),
        breathingGlow = !simpleMode && config.isMasterGuardEnabled
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(
                                        guardAccent.copy(alpha = 0.26f),
                                        guardAccent.copy(alpha = 0.06f)
                                    )
                                )
                            )
                            .border(1.dp, guardAccent.copy(alpha = 0.45f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Fingerprint,
                            contentDescription = null,
                            tint = guardAccent,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.auth_master_guard_title),
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (config.isMasterGuardEnabled)
                                stringResource(R.string.auth_master_guard_active)
                            else
                                stringResource(R.string.auth_master_guard_paused),
                            color = TextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Switch(
                    checked = config.isMasterGuardEnabled,
                    onCheckedChange = onToggleMasterGuard,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = OnAccentInk,
                        checkedTrackColor = EmeraldMint,
                        uncheckedThumbColor = TextSecondary,
                        uncheckedTrackColor = SubtleSurfaceFill
                    ),
                    modifier = Modifier.testTag("auth_master_guard_switch")
                )
            }

            // Stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AuthStatChip(
                    label = stringResource(R.string.auth_stat_protected),
                    value = config.protectedPackages.size.toString(),
                    accent = EmeraldMint,
                    modifier = Modifier.weight(1f)
                )
                AuthStatChip(
                    label = stringResource(R.string.auth_stat_unlocks),
                    value = config.totalUnlockCount.toString(),
                    accent = ElectricCyan,
                    modifier = Modifier.weight(1f)
                )
                AuthStatChip(
                    label = stringResource(R.string.auth_stat_blocked),
                    value = config.totalInterceptionCount.toString(),
                    accent = HoloViolet,
                    modifier = Modifier.weight(1f)
                )
            }

            // Long-press hint for per-app Re-lock Delay customization
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(ElectricCyan.copy(alpha = 0.09f))
                    .border(1.dp, ElectricCyan.copy(alpha = 0.24f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.TouchApp,
                    contentDescription = null,
                    tint = ElectricCyan,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.auth_long_press_hint),
                    color = ElectricCyan,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun AuthStatChip(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(GlassSurfaceVariant)
            .border(1.dp, accent.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            color = accent,
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = label,
            color = TextSecondary,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun AccessibilityGuardBannerCard(
    isServiceActive: Boolean,
    onOpenAccessibilitySettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = if (isServiceActive) EmeraldMint else AmberWarning
    GlassCard(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 18.dp,
        borderColor = accent.copy(alpha = 0.42f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Security,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isServiceActive)
                        stringResource(R.string.auth_service_active)
                    else
                        stringResource(R.string.auth_service_needed),
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (isServiceActive)
                        stringResource(R.string.auth_service_active_desc)
                    else
                        stringResource(R.string.auth_service_needed_desc),
                    color = TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
            if (!isServiceActive) {
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onOpenAccessibilitySettings,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AmberWarning,
                        contentColor = OnAccentInk
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier
                        .height(40.dp)
                        .testTag("auth_enable_accessibility_btn")
                ) {
                    Text(
                        text = stringResource(R.string.auth_service_enable_btn),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppLockRowCard(
    item: InstalledAppItem,
    effectiveTimeoutSeconds: Int,
    onToggle: (Boolean) -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val simpleMode = LocalSimpleMode.current
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val pressScale = if (simpleMode) {
        1f
    } else {
        val animatedScale by animateFloatAsState(
            targetValue = if (isPressed) 0.975f else 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            ),
            label = "app_lock_scale_${item.packageName}"
        )
        animatedScale
    }

    val targetBorder = if (item.isLocked) EmeraldMint else GlassBorder
    val borderColor = if (simpleMode) {
        targetBorder
    } else {
        val animColor by animateColorAsState(
            targetValue = targetBorder,
            animationSpec = tween(320),
            label = "app_lock_border_${item.packageName}"
        )
        animColor
    }

    val appBitmap by produceState<Bitmap?>(
        initialValue = AppIconBitmapCache.get(item.packageName),
        key1 = item.packageName
    ) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                runCatching {
                    val bmp = context.packageManager
                        .getApplicationIcon(item.packageName)
                        .toBitmap(width = 96, height = 96)
                    AppIconBitmapCache.put(item.packageName, bmp)
                    bmp
                }.getOrNull()
            }
        }
    }

    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .scale(pressScale),
        cornerRadius = 18.dp,
        borderColor = borderColor,
        borderWidth = if (item.isLocked) 1.4.dp else 1.dp,
        backgroundColor = if (item.isLocked) GlassSurfaceVariant else GlassSurface,
        breathingGlow = !simpleMode && item.isLocked
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = ripple(color = EmeraldMint),
                    onClick = { onToggle(!item.isLocked) },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongPress()
                    }
                )
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .testTag("auth_app_row_${item.packageName}"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(SubtleSurfaceFill)
                    .border(
                        width = 1.dp,
                        color = if (item.isLocked) EmeraldMint.copy(alpha = 0.45f) else SubtleSurfaceBorder,
                        shape = RoundedCornerShape(13.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                val bmp = appBitmap
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = item.appName,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(9.dp))
                    )
                } else {
                    Icon(
                        imageVector = if (item.isLocked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                        contentDescription = item.appName,
                        tint = if (item.isLocked) EmeraldMint else TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = item.appName,
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (item.isSystemApp) {
                        Text(
                            text = stringResource(R.string.auth_badge_system),
                            color = TextSecondary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(5.dp))
                                .background(SubtleSurfaceFill)
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(3.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (item.isLocked)
                            stringResource(R.string.auth_badge_locked)
                        else
                            item.packageName,
                        color = if (item.isLocked) EmeraldMint else TextSecondary,
                        fontSize = 10.5.sp,
                        fontFamily = if (item.isLocked) FontFamily.Default else FontFamily.Monospace,
                        fontWeight = if (item.isLocked) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (item.isLocked || item.customRelockTimeoutSeconds != null) {
                        val isCustom = item.customRelockTimeoutSeconds != null
                        val delayColor = if (isCustom) ElectricCyan else TextSecondary
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (isCustom) ElectricCyan.copy(alpha = 0.14f) else SubtleSurfaceFill
                                )
                                .border(
                                    width = 0.8.dp,
                                    color = if (isCustom) ElectricCyan.copy(alpha = 0.45f) else SubtleSurfaceBorder,
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                .testTag("auth_app_delay_badge_${item.packageName}")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Timer,
                                contentDescription = null,
                                tint = delayColor,
                                modifier = Modifier.size(10.dp)
                            )
                            Text(
                                text = formatRelockDelayLabel(effectiveTimeoutSeconds),
                                color = delayColor,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Switch(
                checked = item.isLocked,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = OnAccentInk,
                    checkedTrackColor = EmeraldMint,
                    uncheckedThumbColor = TextSecondary,
                    uncheckedTrackColor = SubtleSurfaceFill
                ),
                modifier = Modifier.testTag("auth_app_toggle_${item.packageName}")
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRelockDelayBottomSheet(
    item: InstalledAppItem,
    effectiveTimeoutSeconds: Int,
    onToggleLock: (Boolean) -> Unit,
    onSelectDelay: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val presetValues = remember { listOf(0, 15, 30, 60, 300, 900) }
    var customInput by remember(item.packageName, effectiveTimeoutSeconds) {
        mutableStateOf(
            if (effectiveTimeoutSeconds !in presetValues) effectiveTimeoutSeconds.toString() else ""
        )
    }

    val appBitmap by produceState<Bitmap?>(
        initialValue = AppIconBitmapCache.get(item.packageName),
        key1 = item.packageName
    ) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                runCatching {
                    val bmp = context.packageManager
                        .getApplicationIcon(item.packageName)
                        .toBitmap(width = 96, height = 96)
                    AppIconBitmapCache.put(item.packageName, bmp)
                    bmp
                }.getOrNull()
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = GlassSurface,
        contentColor = TextPrimary,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .testTag("auth_app_delay_sheet"),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(SubtleSurfaceFill)
                        .border(1.dp, ElectricCyan.copy(alpha = 0.4f), RoundedCornerShape(13.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    val bmp = appBitmap
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = item.appName,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(9.dp))
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Timer,
                            contentDescription = null,
                            tint = ElectricCyan,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.auth_app_sheet_title, item.appName),
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = item.packageName,
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("auth_sheet_close_btn")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.update_close_desc),
                        tint = TextSecondary
                    )
                }
            }

            // Per-app lock toggle inside sheet
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(SubtleSurfaceFill)
                    .border(
                        width = 1.dp,
                        color = if (item.isLocked) EmeraldMint.copy(alpha = 0.45f) else SubtleSurfaceBorder,
                        shape = RoundedCornerShape(14.dp)
                    )
                    .clickable { onToggleLock(!item.isLocked) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Fingerprint,
                        contentDescription = null,
                        tint = if (item.isLocked) EmeraldMint else TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = stringResource(R.string.auth_app_sheet_lock_toggle),
                        color = TextPrimary,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Switch(
                    checked = item.isLocked,
                    onCheckedChange = onToggleLock,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = OnAccentInk,
                        checkedTrackColor = EmeraldMint,
                        uncheckedThumbColor = TextSecondary,
                        uncheckedTrackColor = SubtleSurfaceFill
                    ),
                    modifier = Modifier.testTag("auth_sheet_lock_toggle")
                )
            }

            // Preset Re-lock Delay chips (2 rows of 3)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.auth_relock_label),
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                presetValues.chunked(3).forEach { rowValues ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowValues.forEach { seconds ->
                            val selected = effectiveTimeoutSeconds == seconds
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (selected) ElectricCyan.copy(alpha = 0.22f) else SubtleSurfaceFill
                                    )
                                    .border(
                                        width = if (selected) 1.4.dp else 1.dp,
                                        color = if (selected) ElectricCyan else SubtleSurfaceBorder,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = ripple(color = ElectricCyan),
                                        onClick = {
                                            customInput = ""
                                            onSelectDelay(seconds)
                                        }
                                    )
                                    .testTag("auth_sheet_timeout_${seconds}s"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = formatRelockDelayLabel(seconds),
                                    color = if (selected) ElectricCyan else TextPrimary,
                                    fontSize = 12.5.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
                if (effectiveTimeoutSeconds == 0) {
                    Text(
                        text = stringResource(R.string.auth_relock_instant_hint),
                        color = ElectricCyan.copy(alpha = 0.88f),
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp
                    )
                }
            }

            // Arbitrary custom seconds input
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = customInput,
                    onValueChange = { raw ->
                        customInput = raw.filter { it.isDigit() }.take(5)
                    },
                    singleLine = true,
                    label = {
                        Text(
                            text = stringResource(R.string.auth_app_sheet_custom_label),
                            fontSize = 12.sp
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricCyan,
                        unfocusedBorderColor = SubtleSurfaceBorder,
                        focusedLabelColor = ElectricCyan,
                        unfocusedLabelColor = TextSecondary,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = ElectricCyan
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("auth_sheet_custom_input")
                )

                val parsedSeconds = customInput.toIntOrNull()
                Button(
                    onClick = {
                        if (parsedSeconds != null) {
                            onSelectDelay(parsedSeconds)
                            onDismiss()
                        }
                    },
                    enabled = parsedSeconds != null,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricCyan,
                        contentColor = OnAccentInk,
                        disabledContainerColor = SubtleSurfaceFill,
                        disabledContentColor = TextSecondary
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .height(52.dp)
                        .testTag("auth_sheet_apply_custom_btn")
                ) {
                    Text(
                        text = stringResource(R.string.auth_app_sheet_apply_custom),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

