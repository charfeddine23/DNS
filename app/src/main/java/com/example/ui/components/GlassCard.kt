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
    isStackCard: Boolean = false,
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
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "glass_border_color"
    )
    val animatedBgColor by animateColorAsState(
        targetValue = effectiveBackground,
        animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
        label = "glass_bg_color"
    )

    val needsContinuousAnim = breathingGlow ||
        (!isStackCard && (themeStyle == AppThemeStyle.HOLOGRAPHIC_LIQUID_GLASS ||
            themeStyle == AppThemeStyle.SYNTHWAVE_84))
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

    val breathAlpha = if (breathingGlow) 0.42f + 0.36f * ((sin(animPhase) + 1f) * 0.5f) else 0.34f
    val hasExplicitAccentBorder = effectiveBorderColor != palette.glassBorder
    val stackSurfaceAlpha = surfaceAlphaOverride ?: if (isDark) 0.88f else 0.94f

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

    fun Modifier.optionalElevation(elevation: Dp, ambient: Color, spot: Color): Modifier =
        if (isStackCard) this else this.shadow(
            elevation = elevation,
            shape = shape,
            ambientColor = ambient,
            spotColor = spot
        )

    val baseModifier = when (themeStyle) {
        AppThemeStyle.NEUMORPHISM -> {
            val baseMod = if (isStackCard) {
                modifier
                    .clip(shape)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                neuSurface.copy(alpha = stackSurfaceAlpha),
                                animatedBgColor.copy(alpha = stackSurfaceAlpha),
                                neuBase.copy(alpha = stackSurfaceAlpha)
                            ),
                            start = Offset.Zero,
                            end = Offset(600f, 600f)
                        )
                    )
                    .drawWithContent {
                        drawContent()
                        val cr = cornerRadius.toPx()
                        val inset = 1.dp.toPx()
                        val innerCr = (cr - inset).coerceAtLeast(0f)
                        drawRoundRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    neuHighlight.copy(alpha = if (isDark) 0.52f else 0.92f),
                                    Color.Transparent,
                                    neuShadow.copy(alpha = if (isDark) 0.55f else 0.30f)
                                )
                            ),
                            topLeft = Offset(inset, inset),
                            size = Size(
                                (size.width - inset * 2f).coerceAtLeast(1f),
                                (size.height - inset * 2f).coerceAtLeast(1f)
                            ),
                            cornerRadius = CornerRadius(innerCr, innerCr),
                            style = Stroke(width = 1.dp.toPx())
                        )
                    }
            } else {
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
            }

            baseMod.border(
                width = if (isStackCard) borderWidth else if (breathingGlow) 1.2.dp else 0.9.dp,
                brush = Brush.linearGradient(
                    colors = if (hasExplicitAccentBorder) {
                        listOf(
                            animatedBorderColor.copy(alpha = if (breathingGlow) breathAlpha + 0.25f else 0.85f),
                            neuHighlight.copy(alpha = 0.7f),
                            animatedBorderColor.copy(alpha = 0.50f)
                        )
                    } else {
                        listOf(
                            neuHighlight.copy(alpha = if (isDark) 0.85f else 0.95f),
                            neuShadow.copy(alpha = if (isDark) 0.78f else 0.40f)
                        )
                    }
                ),
                shape = shape
            )
        }

        AppThemeStyle.GLASS_PSD_MOCKUP -> {
            modifier
                .optionalElevation(
                    elevation = if (isDark) 16.dp else 10.dp,
                    ambient = if (isDark) Color.Black.copy(alpha = 0.65f) else Color.Black.copy(alpha = 0.14f),
                    spot = animatedBorderColor.copy(alpha = 0.35f)
                )
                .clip(shape)
                .background(
                    brush = Brush.linearGradient(
                        colors = if (isDark) {
                            listOf(
                                animatedBgColor.copy(alpha = stackSurfaceAlpha),
                                surfaceVar.copy(alpha = (stackSurfaceAlpha - 0.06f).coerceAtLeast(0.75f)),
                                animatedBgColor.copy(alpha = stackSurfaceAlpha)
                            )
                        } else {
                            listOf(
                                Color.White.copy(alpha = stackSurfaceAlpha),
                                animatedBgColor.copy(alpha = stackSurfaceAlpha),
                                surfaceVar.copy(alpha = (stackSurfaceAlpha - 0.08f).coerceAtLeast(0.78f))
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
                                Color.White.copy(alpha = if (isStackCard) 0.045f else 0.085f),
                                Color.White.copy(alpha = 0.015f),
                                Color.Transparent
                            ),
                            start = Offset(0f, 0f),
                            end = Offset(size.width * 0.85f, size.height * 0.85f)
                        )
                    )
                }
                .border(
                    width = if (isStackCard) borderWidth else 1.2.dp,
                    brush = Brush.linearGradient(
                        colors = if (hasExplicitAccentBorder) {
                            listOf(
                                Color.White.copy(alpha = if (isDark) 0.55f else 0.90f),
                                animatedBorderColor.copy(alpha = if (breathingGlow) breathAlpha + 0.2f else 0.85f),
                                animatedBorderColor.copy(alpha = 0.45f)
                            )
                        } else {
                            listOf(
                                Color.White.copy(alpha = if (isDark) 0.32f else 0.88f),
                                palette.glassBorder,
                                palette.glassBorder.copy(alpha = 0.14f)
                            )
                        },
                        start = Offset.Zero,
                        end = Offset(700f, 700f)
                    ),
                    shape = shape
                )
        }

        AppThemeStyle.FROSTED_GLASSMORPHISM -> {
            modifier
                .optionalElevation(
                    elevation = if (isDark) 12.dp else 8.dp,
                    ambient = primaryAccent.copy(alpha = 0.16f),
                    spot = Color.Black.copy(alpha = if (isDark) 0.55f else 0.12f)
                )
                .clip(shape)
                .background(
                    brush = Brush.verticalGradient(
                        colors = if (!isDark) {
                            listOf(
                                frostedTint.copy(alpha = stackSurfaceAlpha),
                                animatedBgColor.copy(alpha = stackSurfaceAlpha),
                                surfaceVar.copy(alpha = (stackSurfaceAlpha - 0.05f).coerceAtLeast(0.80f))
                            )
                        } else {
                            listOf(
                                frostedTint.copy(alpha = if (isStackCard) 0.16f else 0.22f),
                                animatedBgColor.copy(alpha = stackSurfaceAlpha),
                                surfaceElev.copy(alpha = stackSurfaceAlpha)
                            )
                        }
                    )
                )
                .drawWithContent {
                    drawContent()
                    if (!isStackCard) {
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
                                        color = grainTint.copy(alpha = 0.032f + jitter * 0.022f),
                                        radius = 0.9.dp.toPx(),
                                        center = Offset(x + jitter * 6f, y + jitter * 6f)
                                    )
                                }
                                idx++
                                y += step
                            }
                            x += step
                        }
                    }
                    val cr = cornerRadius.toPx()
                    val inset = 1.dp.toPx()
                    val innerCr = (cr - inset).coerceAtLeast(0f)
                    drawRoundRect(
                        color = if (isDark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.65f),
                        topLeft = Offset(inset, inset),
                        size = Size(
                            (size.width - inset * 2f).coerceAtLeast(1f),
                            (size.height - inset * 2f).coerceAtLeast(1f)
                        ),
                        cornerRadius = CornerRadius(innerCr, innerCr),
                        style = Stroke(width = 0.9.dp.toPx())
                    )
                }
                .border(
                    width = borderWidth,
                    brush = Brush.verticalGradient(
                        colors = if (hasExplicitAccentBorder) {
                            listOf(
                                animatedBorderColor.copy(alpha = 0.85f),
                                frostedBorder.copy(alpha = 0.65f),
                                animatedBorderColor.copy(alpha = 0.45f)
                            )
                        } else {
                            listOf(
                                frostedBorder.copy(alpha = if (isStackCard) 0.45f else 0.70f),
                                palette.glassBorder,
                                palette.glassBorder.copy(alpha = 0.14f)
                            )
                        }
                    ),
                    shape = shape
                )
        }

        AppThemeStyle.HOLOGRAPHIC_LIQUID_GLASS -> {
            val shift = (cos(animPhase) + 1f) * 0.5f
            modifier
                .optionalElevation(
                    elevation = if (isDark) 16.dp else 10.dp,
                    ambient = tertiaryAccent.copy(alpha = if (isDark) 0.30f else 0.16f),
                    spot = primaryAccent.copy(alpha = if (isDark) 0.35f else 0.18f)
                )
                .clip(shape)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            animatedBgColor.copy(alpha = stackSurfaceAlpha),
                            surfaceVar.copy(alpha = (stackSurfaceAlpha - 0.04f).coerceAtLeast(0.82f)),
                            animatedBgColor.copy(alpha = stackSurfaceAlpha)
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
                                primaryAccent.copy(alpha = if (isStackCard) 0.04f else 0.07f),
                                tertiaryAccent.copy(alpha = if (isStackCard) 0.05f else 0.08f),
                                secondaryGlow.copy(alpha = if (isStackCard) 0.03f else 0.06f),
                                Color.Transparent
                            ),
                            start = Offset(size.width * (shift - 0.3f), 0f),
                            end = Offset(size.width * (shift + 0.6f), size.height)
                        )
                    )
                }
                .border(
                    width = if (isStackCard) borderWidth else 1.4.dp,
                    brush = Brush.linearGradient(
                        colors = if (isStackCard && !hasExplicitAccentBorder) {
                            listOf(
                                primaryAccent.copy(alpha = 0.28f),
                                tertiaryAccent.copy(alpha = 0.25f),
                                secondaryGlow.copy(alpha = 0.22f)
                            )
                        } else if (hasExplicitAccentBorder) {
                            listOf(
                                animatedBorderColor.copy(alpha = 0.90f),
                                primaryAccent.copy(alpha = 0.72f),
                                tertiaryAccent.copy(alpha = 0.68f),
                                emeraldAccent.copy(alpha = 0.72f)
                            )
                        } else {
                            listOf(
                                primaryAccent.copy(alpha = 0.72f),
                                tertiaryAccent.copy(alpha = 0.65f),
                                secondaryGlow.copy(alpha = 0.62f),
                                emeraldAccent.copy(alpha = 0.55f)
                            )
                        },
                        start = Offset(shift * 420f, 0f),
                        end = Offset(520f - shift * 300f, 520f)
                    ),
                    shape = shape
                )
        }

        AppThemeStyle.CHROMATIC_FROSTED_LIQUID -> {
            modifier
                .optionalElevation(
                    elevation = if (breathingGlow) 18.dp else 10.dp,
                    ambient = primaryAccent.copy(alpha = if (isDark) 0.22f else 0.14f),
                    spot = secondaryGlow.copy(alpha = if (isDark) 0.22f else 0.14f)
                )
                .clip(shape)
                .background(
                    brush = Brush.verticalGradient(
                        colors = if (!isDark) {
                            listOf(
                                Color.White.copy(alpha = stackSurfaceAlpha),
                                animatedBgColor.copy(alpha = stackSurfaceAlpha),
                                surfaceVar.copy(alpha = (stackSurfaceAlpha - 0.04f).coerceAtLeast(0.84f))
                            )
                        } else {
                            listOf(
                                surfaceVar.copy(alpha = (stackSurfaceAlpha - 0.04f).coerceAtLeast(0.82f)),
                                animatedBgColor.copy(alpha = stackSurfaceAlpha),
                                surfaceElev.copy(alpha = stackSurfaceAlpha)
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
                                    Color.Black.copy(alpha = if (isStackCard) 0.18f else 0.34f),
                                    Color.Transparent,
                                    Color.Transparent,
                                    Color.Black.copy(alpha = if (isStackCard) 0.22f else 0.40f)
                                )
                            )
                        )
                    }
                    // Concentric Chromatic Dispersion edge refraction channels
                    val inset1 = 0.75.dp.toPx()
                    val cr1 = (cr - inset1).coerceAtLeast(0f)
                    val dispAlphaScale = if (isStackCard && !hasExplicitAccentBorder) 0.35f else 1f
                    drawRoundRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                primaryAccent.copy(alpha = (if (isDark) 0.42f else 0.32f) * dispAlphaScale),
                                Color.Transparent,
                                amberAccent.copy(alpha = (if (isDark) 0.26f else 0.20f) * dispAlphaScale)
                            ),
                            start = Offset.Zero,
                            end = Offset(size.width, size.height)
                        ),
                        topLeft = Offset(inset1, inset1),
                        size = Size(
                            (size.width - inset1 * 2f).coerceAtLeast(1f),
                            (size.height - inset1 * 2f).coerceAtLeast(1f)
                        ),
                        cornerRadius = CornerRadius(cr1, cr1),
                        style = Stroke(width = 0.9.dp.toPx())
                    )
                    if (!isStackCard) {
                        val inset2 = 1.6.dp.toPx()
                        val cr2 = (cr - inset2).coerceAtLeast(0f)
                        drawRoundRect(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    secondaryGlow.copy(alpha = if (isDark) 0.35f else 0.26f),
                                    Color.Transparent,
                                    primaryAccent.copy(alpha = if (isDark) 0.34f else 0.26f)
                                ),
                                start = Offset(size.width, 0f),
                                end = Offset(0f, size.height)
                            ),
                            topLeft = Offset(inset2, inset2),
                            size = Size(
                                (size.width - inset2 * 2f).coerceAtLeast(1f),
                                (size.height - inset2 * 2f).coerceAtLeast(1f)
                            ),
                            cornerRadius = CornerRadius(cr2, cr2),
                            style = Stroke(width = 0.75.dp.toPx())
                        )
                    }
                }
                .border(
                    width = if (isStackCard) borderWidth else if (breathingGlow) 1.4.dp else borderWidth,
                    brush = Brush.verticalGradient(
                        colors = if (hasExplicitAccentBorder) {
                            listOf(
                                topSpecular.copy(alpha = 0.45f),
                                animatedBorderColor.copy(alpha = if (breathingGlow) breathAlpha + 0.28f else 0.80f),
                                animatedBorderColor.copy(alpha = 0.42f)
                            )
                        } else {
                            listOf(
                                topSpecular.copy(alpha = if (isStackCard) 0.24f else 0.45f),
                                primaryAccent.copy(alpha = if (isStackCard) 0.16f else 0.28f),
                                secondaryGlow.copy(alpha = if (isStackCard) 0.12f else 0.22f)
                            )
                        }
                    ),
                    shape = shape
                )
        }

        AppThemeStyle.TOKYO_NIGHT -> {
            modifier
                .optionalElevation(
                    elevation = if (isDark) 14.dp else 8.dp,
                    ambient = primaryAccent.copy(alpha = 0.24f),
                    spot = tertiaryAccent.copy(alpha = 0.24f)
                )
                .clip(shape)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            surfaceVar.copy(alpha = stackSurfaceAlpha),
                            animatedBgColor.copy(alpha = stackSurfaceAlpha),
                            surfaceElev.copy(alpha = stackSurfaceAlpha)
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
                                primaryAccent.copy(alpha = if (isDark) 0.07f else 0.05f),
                                Color.Transparent,
                                tertiaryAccent.copy(alpha = if (isDark) 0.07f else 0.04f)
                            )
                        )
                    )
                }
                .border(
                    width = if (isStackCard) borderWidth else if (breathingGlow) 1.4.dp else borderWidth,
                    brush = Brush.linearGradient(
                        colors = if (isStackCard && !hasExplicitAccentBorder) {
                            listOf(
                                primaryAccent.copy(alpha = 0.28f),
                                tertiaryAccent.copy(alpha = 0.22f)
                            )
                        } else if (hasExplicitAccentBorder) {
                            listOf(
                                animatedBorderColor.copy(alpha = 0.90f),
                                primaryAccent.copy(alpha = 0.68f),
                                tertiaryAccent.copy(alpha = 0.52f)
                            )
                        } else {
                            listOf(
                                primaryAccent.copy(alpha = if (breathingGlow) breathAlpha + 0.25f else 0.48f),
                                tertiaryAccent.copy(alpha = 0.42f),
                                emeraldAccent.copy(alpha = 0.28f)
                            )
                        }
                    ),
                    shape = shape
                )
        }

        AppThemeStyle.CATPPUCCIN_MOCHA -> {
            modifier
                .optionalElevation(
                    elevation = if (isDark) 10.dp else 7.dp,
                    ambient = tertiaryAccent.copy(alpha = 0.16f),
                    spot = primaryAccent.copy(alpha = 0.14f)
                )
                .clip(shape)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            surfaceVar.copy(alpha = stackSurfaceAlpha),
                            animatedBgColor.copy(alpha = stackSurfaceAlpha),
                            surfaceElev.copy(alpha = stackSurfaceAlpha)
                        )
                    )
                )
                .drawWithContent {
                    drawContent()
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                amberAccent.copy(alpha = if (isDark) 0.06f else 0.04f),
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
                    width = if (isStackCard) borderWidth else if (breathingGlow) 1.3.dp else borderWidth,
                    brush = Brush.linearGradient(
                        colors = if (isStackCard && !hasExplicitAccentBorder) {
                            listOf(
                                tertiaryAccent.copy(alpha = 0.24f),
                                primaryAccent.copy(alpha = 0.20f)
                            )
                        } else if (hasExplicitAccentBorder) {
                            listOf(
                                animatedBorderColor.copy(alpha = 0.88f),
                                tertiaryAccent.copy(alpha = 0.55f),
                                amberAccent.copy(alpha = 0.40f)
                            )
                        } else {
                            listOf(
                                tertiaryAccent.copy(alpha = if (breathingGlow) breathAlpha + 0.20f else 0.42f),
                                primaryAccent.copy(alpha = 0.32f),
                                amberAccent.copy(alpha = 0.25f)
                            )
                        }
                    ),
                    shape = shape
                )
        }

        AppThemeStyle.SYNTHWAVE_84 -> {
            val shift = (cos(animPhase) + 1f) * 0.5f
            modifier
                .optionalElevation(
                    elevation = if (isDark) 18.dp else 9.dp,
                    ambient = secondaryGlow.copy(alpha = 0.30f),
                    spot = primaryAccent.copy(alpha = 0.28f)
                )
                .clip(shape)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            animatedBgColor.copy(alpha = stackSurfaceAlpha),
                            surfaceVar.copy(alpha = (stackSurfaceAlpha - 0.03f).coerceAtLeast(0.84f)),
                            animatedBgColor.copy(alpha = stackSurfaceAlpha)
                        )
                    )
                )
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                secondaryGlow.copy(alpha = if (isDark) 0.09f else 0.045f)
                            ),
                            startY = size.height * 0.5f,
                            endY = size.height
                        )
                    )
                }
                .border(
                    width = if (isStackCard) borderWidth else 1.3.dp,
                    brush = Brush.linearGradient(
                        colors = if (isStackCard && !hasExplicitAccentBorder) {
                            listOf(
                                secondaryGlow.copy(alpha = 0.28f),
                                primaryAccent.copy(alpha = 0.25f)
                            )
                        } else if (hasExplicitAccentBorder) {
                            listOf(
                                animatedBorderColor.copy(alpha = 0.90f),
                                primaryAccent.copy(alpha = 0.72f),
                                secondaryGlow.copy(alpha = 0.65f)
                            )
                        } else {
                            listOf(
                                secondaryGlow.copy(alpha = 0.72f),
                                primaryAccent.copy(alpha = 0.68f),
                                amberAccent.copy(alpha = 0.45f)
                            )
                        },
                        start = Offset(shift * 380f, 0f),
                        end = Offset(620f - shift * 220f, 520f)
                    ),
                    shape = shape
                )
        }

        AppThemeStyle.NORD -> {
            modifier
                .optionalElevation(
                    elevation = if (isDark) 8.dp else 6.dp,
                    ambient = primaryAccent.copy(alpha = 0.12f),
                    spot = Color.Black.copy(alpha = if (isDark) 0.35f else 0.08f)
                )
                .clip(shape)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            surfaceVar.copy(alpha = stackSurfaceAlpha),
                            animatedBgColor.copy(alpha = stackSurfaceAlpha)
                        )
                    )
                )
                .border(
                    width = if (isStackCard) borderWidth else if (breathingGlow) 1.25.dp else 1.dp,
                    brush = Brush.verticalGradient(
                        colors = if (hasExplicitAccentBorder) {
                            listOf(
                                animatedBorderColor.copy(alpha = 0.85f),
                                topSpecular.copy(alpha = if (isDark) 0.45f else 0.85f),
                                animatedBorderColor.copy(alpha = 0.50f)
                            )
                        } else {
                            listOf(
                                topSpecular.copy(alpha = if (isDark) 0.35f else 0.85f),
                                palette.glassBorder
                            )
                        }
                    ),
                    shape = shape
                )
        }

        AppThemeStyle.DRACULA_PRO -> {
            modifier
                .optionalElevation(
                    elevation = if (isDark) 16.dp else 9.dp,
                    ambient = primaryAccent.copy(alpha = 0.28f),
                    spot = emeraldAccent.copy(alpha = 0.20f)
                )
                .clip(shape)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            surfaceVar.copy(alpha = stackSurfaceAlpha),
                            animatedBgColor.copy(alpha = stackSurfaceAlpha),
                            surfaceElev.copy(alpha = stackSurfaceAlpha)
                        )
                    )
                )
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                primaryAccent.copy(alpha = if (isDark) 0.06f else 0.035f),
                                Color.Transparent,
                                tertiaryAccent.copy(alpha = if (isDark) 0.05f else 0.035f)
                            ),
                            start = Offset.Zero,
                            end = Offset(size.width, size.height)
                        )
                    )
                }
                .border(
                    width = if (isStackCard) borderWidth else if (breathingGlow) 1.4.dp else borderWidth,
                    brush = Brush.linearGradient(
                        colors = if (isStackCard && !hasExplicitAccentBorder) {
                            listOf(
                                primaryAccent.copy(alpha = 0.28f),
                                tertiaryAccent.copy(alpha = 0.22f)
                            )
                        } else if (hasExplicitAccentBorder) {
                            listOf(
                                animatedBorderColor.copy(alpha = 0.90f),
                                primaryAccent.copy(alpha = 0.65f),
                                secondaryGlow.copy(alpha = 0.45f)
                            )
                        } else {
                            listOf(
                                primaryAccent.copy(alpha = if (breathingGlow) breathAlpha + 0.25f else 0.52f),
                                secondaryGlow.copy(alpha = 0.36f),
                                emeraldAccent.copy(alpha = 0.40f)
                            )
                        }
                    ),
                    shape = shape
                )
        }
    }

    Box(modifier = baseModifier) {
        content()
    }
}
