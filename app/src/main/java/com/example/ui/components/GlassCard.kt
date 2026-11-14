package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AppThemeStyle
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassSurface
import com.example.ui.theme.LocalAppThemeStyle
import com.example.ui.theme.LocalPulsePalette
import com.example.ui.theme.LocalSimpleMode
import com.example.ui.theme.paletteForTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 22.dp,
    borderColor: Color = GlassBorder,
    borderWidth: Dp = 1.dp,
    backgroundColor: Color = GlassSurface,
    breathingGlow: Boolean = false,
    overrideThemeStyle: AppThemeStyle? = null,
    surfaceAlphaOverride: Float? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val simpleMode = LocalSimpleMode.current
    val activePalette = LocalPulsePalette.current
    val themeStyle = overrideThemeStyle ?: LocalAppThemeStyle.current
    val palette = if (overrideThemeStyle != null) {
        paletteForTheme(overrideThemeStyle, isDark = activePalette.isDark)
    } else {
        activePalette
    }
    val isDark = palette.isDark
    val shape = RoundedCornerShape(cornerRadius)

    val effectiveBackground = if (overrideThemeStyle != null) palette.glassSurface else backgroundColor
    val effectiveBorderColor = if (overrideThemeStyle != null && borderColor == activePalette.glassBorder) {
        palette.glassBorder
    } else {
        borderColor
    }

    if (simpleMode && overrideThemeStyle == null) {
        Box(
            modifier = modifier
                .clip(shape)
                .background(
                    if (surfaceAlphaOverride != null) {
                        DarkSurfaceElevated.copy(alpha = surfaceAlphaOverride)
                    } else {
                        DarkSurfaceElevated
                    }
                )
                .border(1.dp, GlassBorder, shape)
        ) {
            content()
        }
        return
    }

    val animatedBorderColor by animateColorAsState(
        targetValue = effectiveBorderColor,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "glass_border_color"
    )

    val needsContinuousAnim = breathingGlow ||
        themeStyle == AppThemeStyle.HOLOGRAPHIC_LIQUID_GLASS ||
        themeStyle == AppThemeStyle.SYNTHWAVE_84
    val animPhase = if (needsContinuousAnim) {
        val transition = rememberInfiniteTransition(label = "card_optic_anim")
        val value by transition.animateFloat(
            initialValue = 0f,
            targetValue = (2f * PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 4800, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "card_phase"
        )
        value
    } else {
        0f
    }

    val breathAlpha = if (breathingGlow) 0.30f + 0.38f * ((sin(animPhase) + 1f) * 0.5f) else 0.42f

    // Snapshot palette colors for non-@Composable draw blocks
    val neuHighlight = palette.neuHighlightEdge
    val neuShadow = palette.neuShadowEdge
    val neuSurface = palette.neuSurfaceLight
    val neuBase = palette.neuBase
    val primaryAccent = palette.primaryAccent
    val tertiaryAccent = palette.tertiaryAccent
    val secondaryGlow = palette.secondaryGlowAccent
    val emeraldAccent = palette.emeraldAccent
    val amberAccent = palette.amberAccent
    val frostedTint = palette.frostedTint
    val frostedBorder = palette.frostedBorder
    val topSpecular = palette.glassTopSpecular
    val surfaceElev = palette.surfaceElevated
    val surfaceVar = palette.glassSurfaceVariant

    val baseModifier = when (themeStyle) {
        AppThemeStyle.NEUMORPHISM -> {
            modifier
                .drawBehind {
                    val cr = cornerRadius.toPx()
                    val offsetPx = 4.5.dp.toPx()
                    drawRoundRect(
                        color = neuHighlight.copy(alpha = if (isDark) 0.78f else 0.95f),
                        topLeft = Offset(-offsetPx * 0.65f, -offsetPx * 0.65f),
                        size = Size(size.width, size.height),
                        cornerRadius = CornerRadius(cr, cr)
                    )
                    drawRoundRect(
                        color = neuShadow.copy(alpha = if (isDark) 0.92f else 0.68f),
                        topLeft = Offset(offsetPx, offsetPx),
                        size = Size(size.width, size.height),
                        cornerRadius = CornerRadius(cr, cr)
                    )
                }
                .clip(shape)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(neuSurface, neuBase),
                        start = Offset.Zero,
                        end = Offset(600f, 600f)
                    )
                )
                .border(
                    width = if (breathingGlow) 1.2.dp else 0.9.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            if (breathingGlow) animatedBorderColor.copy(alpha = breathAlpha)
                            else neuHighlight.copy(alpha = 0.85f),
                            neuShadow.copy(alpha = if (isDark) 0.9f else 0.45f)
                        )
                    ),
                    shape = shape
                )
        }

        AppThemeStyle.GLASS_PSD_MOCKUP -> {
            val baseBackdrop = if (surfaceAlphaOverride != null) {
                Modifier.background(effectiveBackground.copy(alpha = surfaceAlphaOverride))
            } else {
                Modifier
            }
            modifier
                .shadow(
                    elevation = if (isDark) 16.dp else 10.dp,
                    shape = shape,
                    ambientColor = if (isDark) Color.Black.copy(alpha = 0.65f) else Color.Black.copy(alpha = 0.14f),
                    spotColor = animatedBorderColor.copy(alpha = 0.35f)
                )
                .clip(shape)
                .then(baseBackdrop)
                .background(
                    brush = Brush.linearGradient(
                        colors = if (surfaceAlphaOverride != null) {
                            listOf(
                                Color.White.copy(alpha = if (isDark) 0.06f else 0.25f),
                                Color.Transparent,
                                Color.Black.copy(alpha = if (isDark) 0.14f else 0.03f)
                            )
                        } else if (isDark) {
                            listOf(
                                Color.White.copy(alpha = 0.14f),
                                effectiveBackground.copy(alpha = 0.62f),
                                Color.White.copy(alpha = 0.05f)
                            )
                        } else {
                            listOf(
                                Color.White.copy(alpha = 0.96f),
                                effectiveBackground.copy(alpha = 0.88f),
                                surfaceVar.copy(alpha = 0.72f)
                            )
                        },
                        start = Offset(0f, 0f),
                        end = Offset(900f, 900f)
                    )
                )
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = if (surfaceAlphaOverride != null) 0.05f else 0.09f),
                                Color.White.copy(alpha = 0.015f),
                                Color.Transparent
                            ),
                            start = Offset(0f, 0f),
                            end = Offset(size.width * 0.85f, size.height * 0.85f)
                        )
                    )
                }
                .border(
                    width = 1.2.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = if (isDark) 0.45f else 0.90f),
                            animatedBorderColor.copy(alpha = if (breathingGlow) breathAlpha else 0.30f),
                            palette.glassBorder
                        ),
                        start = Offset.Zero,
                        end = Offset(700f, 700f)
                    ),
                    shape = shape
                )
        }

        AppThemeStyle.FROSTED_GLASSMORPHISM -> {
            modifier
                .shadow(
                    elevation = if (isDark) 12.dp else 8.dp,
                    shape = shape,
                    ambientColor = primaryAccent.copy(alpha = 0.16f),
                    spotColor = Color.Black.copy(alpha = if (isDark) 0.55f else 0.12f)
                )
                .clip(shape)
                .then(
                    if (surfaceAlphaOverride != null) {
                        Modifier.background(effectiveBackground.copy(alpha = surfaceAlphaOverride))
                    } else {
                        Modifier
                    }
                )
                .background(
                    brush = Brush.verticalGradient(
                        colors = if (!isDark) {
                            listOf(
                                frostedTint,
                                surfaceElev.copy(alpha = surfaceAlphaOverride ?: 0.90f),
                                surfaceVar.copy(alpha = surfaceAlphaOverride ?: 0.85f)
                            )
                        } else if (surfaceAlphaOverride != null) {
                            listOf(
                                Color(0x18DCEEFF),
                                Color(0xE6121A26),
                                Color(0xE60D131D)
                            )
                        } else {
                            listOf(
                                frostedTint,
                                Color(0xA6162231),
                                Color(0xBD0F1722)
                            )
                        }
                    )
                )
                .drawWithContent {
                    drawContent()
                    val step = 18.dp.toPx()
                    var x = 8f
                    var idx = 0
                    val grainTint = if (isDark) Color.White else primaryAccent
                    while (x < size.width) {
                        var y = 8f
                        while (y < size.height) {
                            val jitter = ((idx * 37) % 11) / 11f
                            if (idx % 3 == 0) {
                                drawCircle(
                                    color = grainTint.copy(alpha = 0.035f + jitter * 0.025f),
                                    radius = 0.9.dp.toPx(),
                                    center = Offset(x + jitter * 6f, y + jitter * 6f)
                                )
                            }
                            idx++
                            y += step
                        }
                        x += step
                    }
                    drawRoundRect(
                        color = if (isDark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.65f),
                        topLeft = Offset(1.dp.toPx(), 1.dp.toPx()),
                        size = Size(size.width - 2.dp.toPx(), size.height - 2.dp.toPx()),
                        cornerRadius = CornerRadius(cornerRadius.toPx(), cornerRadius.toPx()),
                        style = Stroke(width = 1.dp.toPx())
                    )
                }
                .border(
                    width = borderWidth,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            frostedBorder,
                            if (breathingGlow) animatedBorderColor.copy(alpha = breathAlpha) else palette.glassBorder,
                            if (isDark) Color.White.copy(alpha = 0.12f) else primaryAccent.copy(alpha = 0.18f)
                        )
                    ),
                    shape = shape
                )
        }

        AppThemeStyle.HOLOGRAPHIC_LIQUID_GLASS -> {
            val shift = (cos(animPhase) + 1f) * 0.5f
            modifier
                .shadow(
                    elevation = if (isDark) 16.dp else 10.dp,
                    shape = shape,
                    ambientColor = tertiaryAccent.copy(alpha = if (isDark) 0.30f else 0.16f),
                    spotColor = primaryAccent.copy(alpha = if (isDark) 0.35f else 0.18f)
                )
                .clip(shape)
                .then(
                    if (surfaceAlphaOverride != null) {
                        Modifier.background(effectiveBackground.copy(alpha = surfaceAlphaOverride))
                    } else {
                        Modifier
                    }
                )
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            primaryAccent.copy(alpha = if (surfaceAlphaOverride != null) 0.06f else 0.10f),
                            tertiaryAccent.copy(alpha = if (surfaceAlphaOverride != null) 0.08f else 0.12f),
                            effectiveBackground.copy(alpha = surfaceAlphaOverride ?: if (isDark) 0.82f else 0.92f),
                            secondaryGlow.copy(alpha = if (surfaceAlphaOverride != null) 0.05f else 0.08f)
                        ),
                        start = Offset(shift * 260f, 0f),
                        end = Offset(700f + shift * 220f, 700f)
                    )
                )
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.Transparent,
                                primaryAccent.copy(alpha = 0.07f),
                                secondaryGlow.copy(alpha = 0.06f),
                                Color.Transparent
                            ),
                            start = Offset(size.width * (shift - 0.3f), 0f),
                            end = Offset(size.width * (shift + 0.6f), size.height)
                        )
                    )
                }
                .border(
                    width = 1.4.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            primaryAccent.copy(alpha = 0.72f),
                            tertiaryAccent.copy(alpha = 0.65f),
                            secondaryGlow.copy(alpha = 0.62f),
                            emeraldAccent.copy(alpha = 0.55f)
                        ),
                        start = Offset(shift * 420f, 0f),
                        end = Offset(520f - shift * 300f, 520f)
                    ),
                    shape = shape
                )
        }

        AppThemeStyle.CHROMATIC_FROSTED_LIQUID -> {
            modifier
                .shadow(
                    elevation = if (breathingGlow) 18.dp else 10.dp,
                    shape = shape,
                    ambientColor = primaryAccent.copy(alpha = if (isDark) 0.22f else 0.14f),
                    spotColor = secondaryGlow.copy(alpha = if (isDark) 0.22f else 0.14f)
                )
                .clip(shape)
                .then(
                    if (surfaceAlphaOverride != null) {
                        Modifier.background(effectiveBackground.copy(alpha = surfaceAlphaOverride))
                    } else {
                        Modifier
                    }
                )
                .background(
                    brush = Brush.verticalGradient(
                        colors = if (!isDark) {
                            listOf(
                                Color.White.copy(alpha = 0.96f),
                                effectiveBackground.copy(alpha = surfaceAlphaOverride ?: 0.90f),
                                surfaceVar.copy(alpha = surfaceAlphaOverride ?: 0.88f)
                            )
                        } else if (surfaceAlphaOverride != null) {
                            listOf(
                                Color(0x14E8F5FF),
                                Color(0xE6111824),
                                Color(0xE60A0E16)
                            )
                        } else {
                            listOf(
                                Color(0x28E8F5FF),
                                Color(0xC2111824),
                                Color(0xD90A0E16)
                            )
                        }
                    )
                )
                .drawWithContent {
                    drawContent()
                    val cr = cornerRadius.toPx()
                    if (isDark) {
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.38f),
                                    Color.Transparent,
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.45f)
                                )
                            )
                        )
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.24f),
                                    Color.Transparent,
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.24f)
                                )
                            )
                        )
                    }
                    drawRoundRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                primaryAccent.copy(alpha = if (isDark) 0.55f else 0.42f),
                                Color.Transparent,
                                amberAccent.copy(alpha = if (isDark) 0.35f else 0.25f)
                            ),
                            start = Offset.Zero,
                            end = Offset(size.width, size.height)
                        ),
                        topLeft = Offset(0.6.dp.toPx(), 0.6.dp.toPx()),
                        size = Size(size.width - 1.2.dp.toPx(), size.height - 1.2.dp.toPx()),
                        cornerRadius = CornerRadius(cr, cr),
                        style = Stroke(width = 1.dp.toPx())
                    )
                    drawRoundRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                secondaryGlow.copy(alpha = if (isDark) 0.45f else 0.32f),
                                Color.Transparent,
                                primaryAccent.copy(alpha = if (isDark) 0.42f else 0.32f)
                            ),
                            start = Offset(size.width, 0f),
                            end = Offset(0f, size.height)
                        ),
                        topLeft = Offset(1.8.dp.toPx(), 1.8.dp.toPx()),
                        size = Size(size.width - 3.6.dp.toPx(), size.height - 3.6.dp.toPx()),
                        cornerRadius = CornerRadius(cr, cr),
                        style = Stroke(width = 0.8.dp.toPx())
                    )
                }
                .border(
                    width = if (breathingGlow) 1.4.dp else borderWidth,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            topSpecular,
                            if (breathingGlow) animatedBorderColor.copy(alpha = breathAlpha) else primaryAccent.copy(alpha = 0.28f),
                            secondaryGlow.copy(alpha = 0.22f)
                        )
                    ),
                    shape = shape
                )
        }

        AppThemeStyle.TOKYO_NIGHT -> {
            modifier
                .shadow(
                    elevation = if (isDark) 14.dp else 8.dp,
                    shape = shape,
                    ambientColor = primaryAccent.copy(alpha = 0.24f),
                    spotColor = tertiaryAccent.copy(alpha = 0.24f)
                )
                .clip(shape)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            surfaceVar.copy(alpha = surfaceAlphaOverride ?: if (isDark) 0.92f else 0.95f),
                            effectiveBackground.copy(alpha = surfaceAlphaOverride ?: if (isDark) 0.88f else 0.94f),
                            surfaceElev.copy(alpha = surfaceAlphaOverride ?: 0.94f)
                        ),
                        start = Offset.Zero,
                        end = Offset(700f, 700f)
                    )
                )
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                primaryAccent.copy(alpha = if (isDark) 0.08f else 0.06f),
                                Color.Transparent,
                                tertiaryAccent.copy(alpha = if (isDark) 0.08f else 0.05f)
                            )
                        )
                    )
                }
                .border(
                    width = if (breathingGlow) 1.4.dp else borderWidth,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            primaryAccent.copy(alpha = if (breathingGlow) breathAlpha + 0.25f else 0.48f),
                            tertiaryAccent.copy(alpha = 0.42f),
                            emeraldAccent.copy(alpha = 0.28f)
                        )
                    ),
                    shape = shape
                )
        }

        AppThemeStyle.CATPPUCCIN_MOCHA -> {
            modifier
                .shadow(
                    elevation = if (isDark) 10.dp else 7.dp,
                    shape = shape,
                    ambientColor = tertiaryAccent.copy(alpha = 0.16f),
                    spotColor = primaryAccent.copy(alpha = 0.14f)
                )
                .clip(shape)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            surfaceVar.copy(alpha = surfaceAlphaOverride ?: 0.92f),
                            effectiveBackground.copy(alpha = surfaceAlphaOverride ?: 0.90f),
                            surfaceElev.copy(alpha = surfaceAlphaOverride ?: 0.94f)
                        )
                    )
                )
                .drawWithContent {
                    drawContent()
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                amberAccent.copy(alpha = if (isDark) 0.07f else 0.05f),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.9f, 0f),
                            radius = size.width * 0.65f
                        ),
                        radius = size.width * 0.65f,
                        center = Offset(size.width * 0.9f, 0f)
                    )
                }
                .border(
                    width = if (breathingGlow) 1.3.dp else borderWidth,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            tertiaryAccent.copy(alpha = if (breathingGlow) breathAlpha + 0.20f else 0.42f),
                            primaryAccent.copy(alpha = 0.32f),
                            amberAccent.copy(alpha = 0.25f)
                        )
                    ),
                    shape = shape
                )
        }

        AppThemeStyle.SYNTHWAVE_84 -> {
            val shift = (cos(animPhase) + 1f) * 0.5f
            modifier
                .shadow(
                    elevation = if (isDark) 18.dp else 9.dp,
                    shape = shape,
                    ambientColor = secondaryGlow.copy(alpha = 0.30f),
                    spotColor = primaryAccent.copy(alpha = 0.28f)
                )
                .clip(shape)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            effectiveBackground.copy(alpha = surfaceAlphaOverride ?: 0.90f),
                            surfaceVar.copy(alpha = surfaceAlphaOverride ?: 0.88f),
                            secondaryGlow.copy(alpha = if (isDark) 0.14f else 0.06f)
                        )
                    )
                )
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                secondaryGlow.copy(alpha = if (isDark) 0.10f else 0.05f)
                            ),
                            startY = size.height * 0.5f,
                            endY = size.height
                        )
                    )
                }
                .border(
                    width = 1.3.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            secondaryGlow.copy(alpha = 0.72f),
                            primaryAccent.copy(alpha = 0.68f),
                            amberAccent.copy(alpha = 0.45f)
                        ),
                        start = Offset(shift * 380f, 0f),
                        end = Offset(620f - shift * 220f, 520f)
                    ),
                    shape = shape
                )
        }

        AppThemeStyle.NORD -> {
            modifier
                .shadow(
                    elevation = if (isDark) 8.dp else 6.dp,
                    shape = shape,
                    ambientColor = primaryAccent.copy(alpha = 0.12f),
                    spotColor = Color.Black.copy(alpha = if (isDark) 0.35f else 0.08f)
                )
                .clip(shape)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            surfaceVar.copy(alpha = surfaceAlphaOverride ?: 0.94f),
                            effectiveBackground.copy(alpha = surfaceAlphaOverride ?: 0.92f)
                        )
                    )
                )
                .border(
                    width = if (breathingGlow) 1.25.dp else 1.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            topSpecular.copy(alpha = if (isDark) 0.45f else 0.85f),
                            if (breathingGlow) animatedBorderColor.copy(alpha = breathAlpha + 0.18f)
                            else palette.glassBorder
                        )
                    ),
                    shape = shape
                )
        }

        AppThemeStyle.DRACULA_PRO -> {
            modifier
                .shadow(
                    elevation = if (isDark) 16.dp else 9.dp,
                    shape = shape,
                    ambientColor = primaryAccent.copy(alpha = 0.28f),
                    spotColor = emeraldAccent.copy(alpha = 0.20f)
                )
                .clip(shape)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            surfaceVar.copy(alpha = surfaceAlphaOverride ?: 0.92f),
                            effectiveBackground.copy(alpha = surfaceAlphaOverride ?: 0.90f),
                            surfaceElev.copy(alpha = surfaceAlphaOverride ?: 0.95f)
                        )
                    )
                )
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                primaryAccent.copy(alpha = if (isDark) 0.07f else 0.04f),
                                Color.Transparent,
                                tertiaryAccent.copy(alpha = if (isDark) 0.06f else 0.04f)
                            ),
                            start = Offset.Zero,
                            end = Offset(size.width, size.height)
                        )
                    )
                }
                .border(
                    width = if (breathingGlow) 1.4.dp else borderWidth,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            primaryAccent.copy(alpha = if (breathingGlow) breathAlpha + 0.25f else 0.52f),
                            secondaryGlow.copy(alpha = 0.36f),
                            emeraldAccent.copy(alpha = 0.40f)
                        )
                    ),
                    shape = shape
                )
        }
    }

    Box(modifier = baseModifier) {
        content()
    }
}
