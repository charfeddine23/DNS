package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.BuildConfig
import com.example.R
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.LocalSimpleMode
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.UpdateCardInner
import com.example.ui.theme.UpdateCardSurface
import com.example.ui.theme.UpdateCloseGray
import com.example.ui.theme.UpdateCrimson
import com.example.ui.theme.UpdateCrimsonBright
import com.example.ui.theme.UpdateCrimsonDeep
import com.example.ui.theme.UpdateCrimsonText
import com.example.ui.theme.UpdateHaloMiddle
import com.example.ui.theme.UpdateHaloOuter
import com.example.ui.theme.UpdateRocketBody
import com.example.ui.theme.UpdateRocketFlameInner
import com.example.ui.theme.UpdateRocketFlameOuter
import com.example.ui.theme.UpdateRocketWindow
import com.example.ui.theme.UpdateRocketWindowRim
import com.example.ui.theme.UpdateSpaceInner
import com.example.ui.theme.UpdateStarYellow
import com.example.utils.GitHubReleaseInfo
import com.example.utils.UpdateDownloadState
import java.io.File
import java.util.Locale

@Composable
fun UpdateDialog(
    updateState: UpdateDownloadState,
    onStartDownload: (GitHubReleaseInfo) -> Unit,
    onInstallApk: (File) -> Unit,
    onRetryCheck: () -> Unit,
    onDismiss: () -> Unit
) {
    val isDownloading = updateState is UpdateDownloadState.Downloading

    Dialog(
        onDismissRequest = {
            if (!isDownloading) onDismiss()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = !isDownloading
        )
    ) {
        val cardShape = RoundedCornerShape(28.dp)
        val releaseTag = when (updateState) {
            is UpdateDownloadState.Available -> updateState.release.tagName
            is UpdateDownloadState.Downloading -> updateState.release.tagName
            is UpdateDownloadState.ReadyToInstall -> updateState.release.tagName
            is UpdateDownloadState.Error -> updateState.release?.tagName
            else -> null
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 400.dp)
                    .shadow(
                        elevation = 28.dp,
                        shape = cardShape,
                        ambientColor = UpdateCrimson.copy(alpha = 0.28f),
                        spotColor = UpdateCrimson.copy(alpha = 0.35f)
                    )
                    .clip(cardShape)
                    .border(
                        width = 1.2.dp,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                UpdateCrimson.copy(alpha = 0.55f),
                                GlassBorder,
                                Color.White.copy(alpha = 0.06f)
                            )
                        ),
                        shape = cardShape
                    )
                    .testTag("update_dialog"),
                color = UpdateCardSurface,
                shape = cardShape
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    UpdateCrimson.copy(alpha = 0.16f),
                                    UpdateCardSurface
                                ),
                                center = Offset(500f, 120f),
                                radius = 720f
                            )
                        )
                        .padding(horizontal = 22.dp, vertical = 20.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Top utility bar: X button on physical left, subtle version pill on right
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = onDismiss,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("update_later_btn")
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(Color.White.copy(alpha = 0.06f))
                                            .border(
                                                width = 1.dp,
                                                color = Color.White.copy(alpha = 0.09f),
                                                shape = CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = stringResource(R.string.update_close_desc),
                                            tint = UpdateCloseGray,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                if (!releaseTag.isNullOrBlank()) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(50))
                                            .background(UpdateHaloOuter.copy(alpha = 0.9f))
                                            .border(
                                                width = 1.dp,
                                                color = UpdateCrimson.copy(alpha = 0.45f),
                                                shape = RoundedCornerShape(50)
                                            )
                                            .padding(horizontal = 12.dp, vertical = 5.dp)
                                    ) {
                                        Text(
                                            text = "v${BuildConfig.VERSION_NAME} → $releaseTag",
                                            color = Color.White.copy(alpha = 0.9f),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        val simpleMode = LocalSimpleMode.current
                        UpdateRocketEmblem(
                            isAnimating = !simpleMode && (
                                updateState is UpdateDownloadState.Available ||
                                    updateState is UpdateDownloadState.Downloading ||
                                    updateState is UpdateDownloadState.ReadyToInstall
                                )
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                when (updateState) {
                                    is UpdateDownloadState.Checking -> {
                                        Text(
                                            text = stringResource(R.string.update_title_checking),
                                            color = TextPrimary,
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = stringResource(R.string.update_subtitle_checking),
                                            color = UpdateCrimsonText,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(22.dp))
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(30.dp),
                                            color = UpdateCrimson,
                                            strokeWidth = 3.dp
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }

                                    is UpdateDownloadState.Available -> {
                                        val release = updateState.release
                                        Text(
                                            text = stringResource(R.string.update_title_available),
                                            color = TextPrimary,
                                            fontSize = 22.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            textAlign = TextAlign.Center
                                        )

                                        Spacer(modifier = Modifier.height(6.dp))

                                        Text(
                                            text = stringResource(R.string.update_subtitle_new),
                                            color = UpdateCrimsonText,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            textAlign = TextAlign.Center
                                        )

                                        Spacer(modifier = Modifier.height(14.dp))

                                        val customNotes = release.releaseNotes
                                            .takeIf {
                                                it.isNotBlank() &&
                                                    !it.startsWith("Performance improvements, stability fixes") &&
                                                    !it.startsWith("New update ")
                                            }

                                        if (customNotes != null) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .heightIn(min = 44.dp, max = 115.dp)
                                                    .clip(RoundedCornerShape(16.dp))
                                                    .background(UpdateCardInner)
                                                    .border(
                                                        width = 1.dp,
                                                        color = Color.White.copy(alpha = 0.07f),
                                                        shape = RoundedCornerShape(16.dp)
                                                    )
                                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                                                    .verticalScroll(rememberScrollState()),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = customNotes,
                                                    color = TextSecondary,
                                                    fontSize = 13.sp,
                                                    lineHeight = 19.sp,
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        } else {
                                            Text(
                                                text = buildString {
                                                    append(stringResource(R.string.update_description_available))
                                                    if (release.apkSizeBytes > 0L) {
                                                        append(" • ")
                                                        append(formatBytes(release.apkSizeBytes))
                                                    }
                                                },
                                                color = TextSecondary,
                                                fontSize = 14.sp,
                                                textAlign = TextAlign.Center
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(22.dp))

                                        LuxuryCrimsonButton(
                                            text = stringResource(R.string.update_button_text),
                                            onClick = { onStartDownload(release) },
                                            modifier = Modifier.testTag("update_now_btn")
                                        )
                                    }

                                    is UpdateDownloadState.Downloading -> {
                                        val release = updateState.release
                                        val isConnecting = updateState.downloadedBytes <= 0L
                                        val hasTotalSize = updateState.totalBytes > 0L && updateState.progressPercent >= 0
                                        val targetProgress = if (hasTotalSize) {
                                            (updateState.progressPercent.coerceIn(0, 100)) / 100f
                                        } else {
                                            0f
                                        }
                                        val animatedProgress by animateFloatAsState(
                                            targetValue = targetProgress,
                                            animationSpec = tween(
                                                durationMillis = if (simpleMode) 0 else 180,
                                                easing = FastOutSlowInEasing
                                            ),
                                            label = "update_download_progress"
                                        )

                                        Text(
                                            text = stringResource(R.string.update_title_downloading),
                                            color = TextPrimary,
                                            fontSize = 21.sp,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center
                                        )

                                        Spacer(modifier = Modifier.height(6.dp))

                                        Text(
                                            text = release.apkAssetName,
                                            color = UpdateCrimsonText,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            fontFamily = FontFamily.Monospace,
                                            textAlign = TextAlign.Center
                                        )

                                        Spacer(modifier = Modifier.height(20.dp))

                                        if (hasTotalSize && !isConnecting) {
                                            LinearProgressIndicator(
                                                progress = { animatedProgress },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(8.dp)
                                                    .clip(RoundedCornerShape(50)),
                                                color = UpdateCrimsonBright,
                                                trackColor = UpdateHaloOuter
                                            )
                                            Spacer(modifier = Modifier.height(10.dp))
                                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    val speedText = formatSpeed(updateState.speedBytesPerSec)
                                                    Text(
                                                        text = if (speedText.isNotEmpty()) {
                                                            "${updateState.progressPercent}% • $speedText"
                                                        } else {
                                                            "${updateState.progressPercent}%"
                                                        },
                                                        color = UpdateCrimsonText,
                                                        fontSize = 12.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        fontFamily = FontFamily.Monospace
                                                    )
                                                    Text(
                                                        text = "${formatBytes(updateState.downloadedBytes)} / ${formatBytes(updateState.totalBytes)}",
                                                        color = TextSecondary,
                                                        fontSize = 12.sp,
                                                        fontFamily = FontFamily.Monospace
                                                    )
                                                }
                                            }
                                        } else {
                                            LinearProgressIndicator(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(8.dp)
                                                    .clip(RoundedCornerShape(50)),
                                                color = UpdateCrimsonBright,
                                                trackColor = UpdateHaloOuter
                                            )
                                            Spacer(modifier = Modifier.height(10.dp))
                                            if (isConnecting) {
                                                Text(
                                                    text = stringResource(R.string.update_connecting),
                                                    color = TextSecondary,
                                                    fontSize = 12.5.sp,
                                                    textAlign = TextAlign.Center
                                                )
                                            } else {
                                                val speedText = formatSpeed(updateState.speedBytesPerSec)
                                                Text(
                                                    text = if (speedText.isNotEmpty()) {
                                                        "${formatBytes(updateState.downloadedBytes)} • $speedText"
                                                    } else {
                                                        formatBytes(updateState.downloadedBytes)
                                                    },
                                                    color = TextSecondary,
                                                    fontSize = 12.sp,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))
                                        TextButton(
                                            onClick = onDismiss,
                                            modifier = Modifier.testTag("update_cancel_btn")
                                        ) {
                                            Text(
                                                text = stringResource(R.string.update_cancel_download),
                                                color = TextSecondary,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }

                                    is UpdateDownloadState.ReadyToInstall -> {
                                        Text(
                                            text = stringResource(R.string.update_title_ready),
                                            color = TextPrimary,
                                            fontSize = 21.sp,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center
                                        )

                                        Spacer(modifier = Modifier.height(8.dp))

                                        Text(
                                            text = stringResource(R.string.update_description_available),
                                            color = TextSecondary,
                                            fontSize = 14.sp,
                                            textAlign = TextAlign.Center
                                        )

                                        Spacer(modifier = Modifier.height(22.dp))

                                        LuxuryCrimsonButton(
                                            text = stringResource(R.string.update_install_button),
                                            onClick = { onInstallApk(updateState.apkFile) },
                                            modifier = Modifier.testTag("install_apk_btn")
                                        )
                                    }

                                    is UpdateDownloadState.Error -> {
                                        Text(
                                            text = stringResource(
                                                if (updateState.release != null) {
                                                    R.string.update_title_download_error
                                                } else {
                                                    R.string.update_title_error
                                                }
                                            ),
                                            color = TextPrimary,
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center
                                        )

                                        Spacer(modifier = Modifier.height(8.dp))

                                        Text(
                                            text = updateState.message,
                                            color = TextSecondary,
                                            fontSize = 13.sp,
                                            lineHeight = 19.sp,
                                            textAlign = TextAlign.Center
                                        )

                                        Spacer(modifier = Modifier.height(22.dp))

                                        LuxuryCrimsonButton(
                                            text = stringResource(R.string.update_retry_button),
                                            onClick = {
                                                val release = updateState.release
                                                if (release != null) onStartDownload(release) else onRetryCheck()
                                            },
                                            modifier = Modifier.testTag("retry_update_btn")
                                        )
                                    }

                                    UpdateDownloadState.Idle -> Unit
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun LuxuryCrimsonButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pillShape = RoundedCornerShape(50)
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .shadow(
                elevation = 14.dp,
                shape = pillShape,
                ambientColor = UpdateCrimson.copy(alpha = 0.45f),
                spotColor = UpdateCrimson.copy(alpha = 0.55f)
            ),
        shape = pillShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = Color.White
        ),
        contentPadding = PaddingValues(0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(UpdateCrimsonBright, UpdateCrimson, UpdateCrimsonDeep)
                    ),
                    shape = pillShape
                )
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.16f),
                    shape = pillShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@Composable
private fun UpdateRocketEmblem(isAnimating: Boolean) {
    var offsetY = 0f
    var currentFlameScale = 1f
    if (isAnimating) {
        val infiniteTransition = rememberInfiniteTransition(label = "rocket_float")
        val floatY by infiniteTransition.animateFloat(
            initialValue = -2.2f,
            targetValue = 2.2f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "rocket_y"
        )
        val flameScale by infiniteTransition.animateFloat(
            initialValue = 0.92f,
            targetValue = 1.08f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 420, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "flame_scale"
        )
        offsetY = floatY
        currentFlameScale = flameScale
    }

    Canvas(modifier = Modifier.size(116.dp)) {
        val w = size.width
        val h = size.height
        val cx = w * 0.5f
        val cy = h * 0.5f

        drawCircle(
            color = UpdateHaloOuter,
            radius = w * 0.50f,
            center = Offset(cx, cy)
        )
        drawCircle(
            color = UpdateHaloMiddle,
            radius = w * 0.415f,
            center = Offset(cx, cy)
        )
        drawCircle(
            color = UpdateSpaceInner,
            radius = w * 0.34f,
            center = Offset(cx, cy)
        )
        drawCircle(
            color = UpdateCrimson,
            radius = w * 0.34f,
            center = Offset(cx, cy),
            style = Stroke(width = w * 0.024f)
        )

        val starSize = w * 0.026f
        drawRect(
            color = UpdateCrimsonText,
            topLeft = Offset(w * 0.28f, h * 0.37f),
            size = Size(starSize, starSize)
        )
        drawRect(
            color = UpdateStarYellow,
            topLeft = Offset(w * 0.325f, h * 0.61f),
            size = Size(starSize, starSize)
        )
        drawRect(
            color = Color.White,
            topLeft = Offset(w * 0.70f, h * 0.335f),
            size = Size(starSize * 1.15f, starSize * 1.15f)
        )
        drawRect(
            color = UpdateCrimsonText,
            topLeft = Offset(w * 0.675f, h * 0.55f),
            size = Size(starSize, starSize)
        )

        val rCy = cy + offsetY.dp.toPx()
        val rW = w * 0.20f
        val rTop = rCy - h * 0.23f
        val rBot = rCy + h * 0.115f

        val flameTop = rBot - h * 0.01f
        val flameTipY = rBot + h * (0.145f * currentFlameScale)
        val outerFlame = Path().apply {
            moveTo(cx - rW * 0.26f, flameTop)
            lineTo(cx + rW * 0.26f, flameTop)
            lineTo(cx + rW * 0.14f, flameTop + (flameTipY - flameTop) * 0.45f)
            lineTo(cx, flameTipY)
            lineTo(cx - rW * 0.14f, flameTop + (flameTipY - flameTop) * 0.45f)
            close()
        }
        drawPath(path = outerFlame, color = UpdateRocketFlameOuter)

        val innerFlameTipY = rBot + h * (0.10f * currentFlameScale)
        val innerFlame = Path().apply {
            moveTo(cx - rW * 0.14f, flameTop)
            lineTo(cx + rW * 0.14f, flameTop)
            lineTo(cx, innerFlameTipY)
            close()
        }
        drawPath(path = innerFlame, color = UpdateRocketFlameInner)

        val leftFin = Path().apply {
            moveTo(cx - rW * 0.36f, rCy - h * 0.01f)
            lineTo(cx - rW * 0.92f, rBot + h * 0.025f)
            lineTo(cx - rW * 0.85f, rBot + h * 0.045f)
            lineTo(cx - rW * 0.32f, rBot - h * 0.005f)
            close()
        }
        drawPath(path = leftFin, color = UpdateCrimsonDeep)

        val rightFin = Path().apply {
            moveTo(cx + rW * 0.36f, rCy - h * 0.01f)
            lineTo(cx + rW * 0.92f, rBot + h * 0.025f)
            lineTo(cx + rW * 0.85f, rBot + h * 0.045f)
            lineTo(cx + rW * 0.32f, rBot - h * 0.005f)
            close()
        }
        drawPath(path = rightFin, color = UpdateCrimsonDeep)

        val fuselage = Path().apply {
            moveTo(cx, rTop)
            cubicTo(
                cx + rW * 0.48f, rTop + h * 0.09f,
                cx + rW * 0.56f, rCy + h * 0.01f,
                cx + rW * 0.34f, rBot
            )
            lineTo(cx - rW * 0.34f, rBot)
            cubicTo(
                cx - rW * 0.56f, rCy + h * 0.01f,
                cx - rW * 0.48f, rTop + h * 0.09f,
                cx, rTop
            )
            close()
        }

        drawPath(path = fuselage, color = UpdateRocketBody)

        clipPath(fuselage) {
            drawRect(
                color = UpdateCrimson,
                topLeft = Offset(cx - rW, rTop),
                size = Size(rW * 2f, h * 0.115f)
            )
        }

        val centerFin = Path().apply {
            moveTo(cx, rBot - h * 0.065f)
            lineTo(cx + rW * 0.11f, rBot + h * 0.03f)
            lineTo(cx - rW * 0.11f, rBot + h * 0.03f)
            close()
        }
        drawPath(path = centerFin, color = UpdateCrimsonDeep)

        val winCenter = Offset(cx, rCy - h * 0.035f)
        drawCircle(
            color = TextSecondary,
            radius = rW * 0.30f,
            center = winCenter
        )
        drawCircle(
            color = UpdateRocketWindowRim,
            radius = rW * 0.27f,
            center = winCenter
        )
        drawCircle(
            color = UpdateRocketWindow,
            radius = rW * 0.21f,
            center = winCenter
        )

        drawLine(
            color = Color.White.copy(alpha = 0.75f),
            start = Offset(winCenter.x - rW * 0.10f, winCenter.y - rW * 0.06f),
            end = Offset(winCenter.x - rW * 0.02f, winCenter.y - rW * 0.12f),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 MB"
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 0.1) {
        String.format(Locale.US, "%.1f MB", mb)
    } else {
        val kb = bytes / 1024.0
        String.format(Locale.US, "%.0f KB", kb.coerceAtLeast(1.0))
    }
}

private fun formatSpeed(bytesPerSec: Long): String {
    if (bytesPerSec <= 0L) return ""
    val mbPerSec = bytesPerSec / (1024.0 * 1024.0)
    return if (mbPerSec >= 1.0) {
        String.format(Locale.US, "%.1f MB/s", mbPerSec)
    } else {
        val kbPerSec = bytesPerSec / 1024.0
        String.format(Locale.US, "%.0f KB/s", kbPerSec.coerceAtLeast(1.0))
    }
}
