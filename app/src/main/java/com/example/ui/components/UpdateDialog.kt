package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import com.example.R
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.UpdateCloseGray
import com.example.ui.theme.UpdateCrimson
import com.example.ui.theme.UpdateCrimsonText
import com.example.ui.theme.UpdateHaloMiddle
import com.example.ui.theme.UpdateHaloOuter
import com.example.ui.theme.UpdateRocketBody
import com.example.ui.theme.UpdateRocketFlameInner
import com.example.ui.theme.UpdateRocketFlameOuter
import com.example.ui.theme.UpdateRocketWindow
import com.example.ui.theme.UpdateRocketWindowRim
import com.example.ui.theme.UpdateScreenBg
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
    val canDismiss = updateState !is UpdateDownloadState.Downloading

    Dialog(
        onDismissRequest = {
            if (canDismiss) onDismiss()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnBackPress = canDismiss,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(UpdateScreenBg)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .testTag("update_dialog")
        ) {
            // Keep top-left X button in exact physical top-left position like the reference design
            if (canDismiss) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, top = 8.dp),
                        contentAlignment = Alignment.TopStart
                    ) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("update_later_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.update_close_desc),
                                tint = UpdateCloseGray,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }

            // Centered content block
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .widthIn(max = 520.dp)
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    UpdateRocketEmblem(
                        isAnimating = updateState is UpdateDownloadState.Available ||
                            updateState is UpdateDownloadState.Downloading ||
                            updateState is UpdateDownloadState.ReadyToInstall
                    )

                    Spacer(modifier = Modifier.height(34.dp))

                    when (updateState) {
                        is UpdateDownloadState.Checking -> {
                            Text(
                                text = stringResource(R.string.update_title_checking),
                                color = TextPrimary,
                                fontSize = 23.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = stringResource(R.string.update_subtitle_checking),
                                color = UpdateCrimsonText,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(32.dp))
                            CircularProgressIndicator(
                                modifier = Modifier.size(34.dp),
                                color = UpdateCrimson,
                                strokeWidth = 3.dp
                            )
                        }

                        is UpdateDownloadState.Available -> {
                            val release = updateState.release
                            Text(
                                text = stringResource(R.string.update_title_available),
                                color = TextPrimary,
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = "${stringResource(R.string.update_subtitle_new)} • ${release.tagName}",
                                color = UpdateCrimsonText,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            val descriptionText = release.releaseNotes
                                .takeIf {
                                    it.isNotBlank() &&
                                        !it.startsWith("Performance improvements, stability fixes") &&
                                        !it.startsWith("New update ")
                                }
                                ?: stringResource(R.string.update_description_available)

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 110.dp)
                                    .verticalScroll(rememberScrollState()),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = descriptionText,
                                    color = TextSecondary,
                                    fontSize = 15.sp,
                                    lineHeight = 22.sp,
                                    textAlign = TextAlign.Center
                                )
                            }

                            Spacer(modifier = Modifier.height(36.dp))

                            Button(
                                onClick = { onStartDownload(release) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp)
                                    .testTag("update_now_btn"),
                                shape = RoundedCornerShape(50),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = UpdateCrimson,
                                    contentColor = Color.White
                                )
                            ) {
                                Text(
                                    text = stringResource(R.string.update_button_text),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        is UpdateDownloadState.Downloading -> {
                            val release = updateState.release
                            Text(
                                text = stringResource(R.string.update_title_downloading),
                                color = TextPrimary,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = release.tagName,
                                color = UpdateCrimsonText,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(26.dp))

                            if (updateState.progressPercent >= 0) {
                                LinearProgressIndicator(
                                    progress = { updateState.progressPercent / 100f },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(50)),
                                    color = UpdateCrimson,
                                    trackColor = UpdateHaloOuter
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "${updateState.progressPercent}%",
                                        color = UpdateCrimsonText,
                                        fontSize = 13.sp,
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
                            } else {
                                LinearProgressIndicator(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(50)),
                                    color = UpdateCrimson,
                                    trackColor = UpdateHaloOuter
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = formatBytes(updateState.downloadedBytes),
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        is UpdateDownloadState.ReadyToInstall -> {
                            Text(
                                text = stringResource(R.string.update_title_ready),
                                color = TextPrimary,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = updateState.release.tagName,
                                color = UpdateCrimsonText,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = stringResource(R.string.update_description_available),
                                color = TextSecondary,
                                fontSize = 15.sp,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(36.dp))

                            Button(
                                onClick = { onInstallApk(updateState.apkFile) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp)
                                    .testTag("install_apk_btn"),
                                shape = RoundedCornerShape(50),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = UpdateCrimson,
                                    contentColor = Color.White
                                )
                            ) {
                                Text(
                                    text = stringResource(R.string.update_install_button),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        is UpdateDownloadState.Error -> {
                            Text(
                                text = stringResource(R.string.update_title_error),
                                color = TextPrimary,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = updateState.message,
                                color = TextSecondary,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(32.dp))

                            Button(
                                onClick = {
                                    val release = updateState.release
                                    if (release != null) onStartDownload(release) else onRetryCheck()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp)
                                    .testTag("retry_update_btn"),
                                shape = RoundedCornerShape(50),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = UpdateCrimson,
                                    contentColor = Color.White
                                )
                            ) {
                                Text(
                                    text = stringResource(R.string.update_retry_button),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        UpdateDownloadState.Idle -> Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdateRocketEmblem(isAnimating: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "rocket_float")
    val floatY by infiniteTransition.animateFloat(
        initialValue = -2.5f,
        targetValue = 2.5f,
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

    val offsetY = if (isAnimating) floatY else 0f
    val currentFlameScale = if (isAnimating) flameScale else 1f

    Canvas(modifier = Modifier.size(164.dp)) {
        val w = size.width
        val h = size.height
        val cx = w * 0.5f
        val cy = h * 0.5f

        // 1. Concentric crimson glow halos
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
            style = Stroke(width = w * 0.022f)
        )

        // 2. Pixel space stars inside the inner ring
        val starSize = w * 0.024f
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
            color = Color(0xFFE05A5A),
            topLeft = Offset(w * 0.675f, h * 0.55f),
            size = Size(starSize, starSize)
        )

        // 3. Rocket (centered with subtle hover offset)
        val rCy = cy + offsetY.dp.toPx()
        val rW = w * 0.20f
        val rTop = rCy - h * 0.23f
        val rBot = rCy + h * 0.115f

        // Exhaust Flame (outer orange + inner yellow)
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

        // Side Red Fins
        val leftFin = Path().apply {
            moveTo(cx - rW * 0.36f, rCy - h * 0.01f)
            lineTo(cx - rW * 0.92f, rBot + h * 0.025f)
            lineTo(cx - rW * 0.85f, rBot + h * 0.045f)
            lineTo(cx - rW * 0.32f, rBot - h * 0.005f)
            close()
        }
        drawPath(path = leftFin, color = Color(0xFFB71C1C))

        val rightFin = Path().apply {
            moveTo(cx + rW * 0.36f, rCy - h * 0.01f)
            lineTo(cx + rW * 0.92f, rBot + h * 0.025f)
            lineTo(cx + rW * 0.85f, rBot + h * 0.045f)
            lineTo(cx + rW * 0.32f, rBot - h * 0.005f)
            close()
        }
        drawPath(path = rightFin, color = Color(0xFFB71C1C))

        // Fuselage Silhouette Path
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

        // White body
        drawPath(path = fuselage, color = UpdateRocketBody)

        // Crimson nose cone clipped neatly to fuselage
        clipPath(fuselage) {
            drawRect(
                color = Color(0xFFC61616),
                topLeft = Offset(cx - rW, rTop),
                size = Size(rW * 2f, h * 0.115f)
            )
        }

        // Center bottom stabilizer fin
        val centerFin = Path().apply {
            moveTo(cx, rBot - h * 0.065f)
            lineTo(cx + rW * 0.11f, rBot + h * 0.03f)
            lineTo(cx - rW * 0.11f, rBot + h * 0.03f)
            close()
        }
        drawPath(path = centerFin, color = Color(0xFFB71C1C))

        // Circular cyan porthole window
        val winCenter = Offset(cx, rCy - h * 0.035f)
        drawCircle(
            color = Color(0xFF4C5663),
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

        // Small crisp diagonal highlight on window
        drawLine(
            color = Color.White.copy(alpha = 0.7f),
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
    return String.format(Locale.US, "%.1f MB", mb)
}
