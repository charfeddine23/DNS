package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import com.example.ui.theme.ChromaticAmber
import com.example.ui.theme.ChromaticCyan
import com.example.ui.theme.ChromaticMagenta
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldMint
import com.example.ui.theme.FrostedBorder
import com.example.ui.theme.FrostedTint
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassSurface
import com.example.ui.theme.GlassTopSpecular
import com.example.ui.theme.HoloMagenta
import com.example.ui.theme.HoloViolet
import com.example.ui.theme.LocalAppThemeStyle
import com.example.ui.theme.LocalSimpleMode
import com.example.ui.theme.NeuBaseDark
import com.example.ui.theme.NeuHighlightEdge
import com.example.ui.theme.NeuShadowEdge
import com.example.ui.theme.NeuSurfaceLight
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
    content: @Composable BoxScope.() -> Unit
) {
    val simpleMode = LocalSimpleMode.current
    val themeStyle = overrideThemeStyle ?: LocalAppThemeStyle.current
    val shape = RoundedCornerShape(cornerRadius)

    if (simpleMode && overrideThemeStyle == null) {
        Box(
            modifier = modifier
                .clip(shape)
                .background(DarkSurfaceElevated)
                .border(1.dp, GlassBorder, shape)
        ) {
            content()
        }
        return
    }

    val animatedBorderColor by animateColorAsState(
        targetValue = borderColor,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "glass_border_color"
    )

    val needsContinuousAnim = breathingGlow || themeStyle == AppThemeStyle.HOLOGRAPHIC_LIQUID_GLASS
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

    val baseModifier = when (themeStyle) {
        AppThemeStyle.NEUMORPHISM -> {
            modifier
                .drawBehind {
                    val cr = cornerRadius.toPx()
                    val offsetPx = 4.5.dp.toPx()
                    // Top-left sculpted bevel highlight
                    drawRoundRect(
                        color = NeuHighlightEdge.copy(alpha = 0.78f),
                        topLeft = Offset(-offsetPx * 0.65f, -offsetPx * 0.65f),
                        size = Size(size.width, size.height),
                        cornerRadius = CornerRadius(cr, cr)
                    )
                    // Bottom-right deep shadow extrusion
                    drawRoundRect(
                        color = NeuShadowEdge.copy(alpha = 0.92f),
                        topLeft = Offset(offsetPx, offsetPx),
                        size = Size(size.width, size.height),
                        cornerRadius = CornerRadius(cr, cr)
                    )
                }
                .clip(shape)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(NeuSurfaceLight, NeuBaseDark),
                        start = Offset.Zero,
                        end = Offset(600f, 600f)
                    )
                )
                .border(
                    width = if (breathingGlow) 1.2.dp else 0.9.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            if (breathingGlow) animatedBorderColor.copy(alpha = breathAlpha)
                            else NeuHighlightEdge.copy(alpha = 0.8f),
                            NeuShadowEdge.copy(alpha = 0.9f)
                        )
                    ),
                    shape = shape
                )
        }

        AppThemeStyle.GLASS_PSD_MOCKUP -> {
            modifier
                .shadow(
                    elevation = 16.dp,
                    shape = shape,
                    ambientColor = Color.Black.copy(alpha = 0.65f),
                    spotColor = animatedBorderColor.copy(alpha = 0.35f)
                )
                .clip(shape)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.14f),
                            backgroundColor.copy(alpha = 0.62f),
                            Color.White.copy(alpha = 0.05f)
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(900f, 900f)
                    )
                )
                .drawWithContent {
                    drawContent()
                    // Classic 45-degree PSD studio specular glare band
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = 0.09f),
                                Color.White.copy(alpha = 0.02f),
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
                            Color.White.copy(alpha = 0.45f),
                            animatedBorderColor.copy(alpha = if (breathingGlow) breathAlpha else 0.25f),
                            Color.White.copy(alpha = 0.07f)
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
                    elevation = 12.dp,
                    shape = shape,
                    ambientColor = Color(0xFF8CD8FF).copy(alpha = 0.16f),
                    spotColor = Color.Black.copy(alpha = 0.55f)
                )
                .clip(shape)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            FrostedTint,
                            Color(0xA6162231),
                            Color(0xBD0F1722)
                        )
                    )
                )
                .drawWithContent {
                    drawContent()
                    // Subtle deterministic arctic frost micro-grain speckles
                    val step = 18.dp.toPx()
                    var x = 8f
                    var idx = 0
                    while (x < size.width) {
                        var y = 8f
                        while (y < size.height) {
                            val jitter = ((idx * 37) % 11) / 11f
                            if (idx % 3 == 0) {
                                drawCircle(
                                    color = Color.White.copy(alpha = 0.035f + jitter * 0.025f),
                                    radius = 0.9.dp.toPx(),
                                    center = Offset(x + jitter * 6f, y + jitter * 6f)
                                )
                            }
                            idx++
                            y += step
                        }
                        x += step
                    }
                    // Soft satin frost inner rim
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.08f),
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
                            FrostedBorder,
                            if (breathingGlow) animatedBorderColor.copy(alpha = breathAlpha) else GlassBorder,
                            Color.White.copy(alpha = 0.12f)
                        )
                    ),
                    shape = shape
                )
        }

        AppThemeStyle.HOLOGRAPHIC_LIQUID_GLASS -> {
            val shift = (cos(animPhase) + 1f) * 0.5f
            modifier
                .shadow(
                    elevation = 16.dp,
                    shape = shape,
                    ambientColor = HoloViolet.copy(alpha = 0.30f),
                    spotColor = ElectricCyan.copy(alpha = 0.35f)
                )
                .clip(shape)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            ElectricCyan.copy(alpha = 0.09f),
                            HoloViolet.copy(alpha = 0.11f),
                            backgroundColor.copy(alpha = 0.80f),
                            HoloMagenta.copy(alpha = 0.07f)
                        ),
                        start = Offset(shift * 260f, 0f),
                        end = Offset(700f + shift * 220f, 700f)
                    )
                )
                .drawWithContent {
                    drawContent()
                    // Liquid prism highlight ribbon
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.Transparent,
                                ElectricCyan.copy(alpha = 0.07f),
                                HoloMagenta.copy(alpha = 0.06f),
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
                            ElectricCyan.copy(alpha = 0.72f),
                            HoloViolet.copy(alpha = 0.65f),
                            HoloMagenta.copy(alpha = 0.62f),
                            EmeraldMint.copy(alpha = 0.55f)
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
                    ambientColor = ChromaticCyan.copy(alpha = 0.22f),
                    spotColor = ChromaticMagenta.copy(alpha = 0.22f)
                )
                .clip(shape)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0x28E8F5FF),
                            Color(0xC2111824),
                            Color(0xD90A0E16)
                        )
                    )
                )
                .drawWithContent {
                    drawContent()
                    val cr = cornerRadius.toPx()
                    // Authentic recessed Inner Shadow along top, bottom, and side rims
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
                    // Chromatic Dispersion edge refraction channels (Cyan / Magenta / Amber split)
                    drawRoundRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                ChromaticCyan.copy(alpha = 0.55f),
                                Color.Transparent,
                                ChromaticAmber.copy(alpha = 0.35f)
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
                                ChromaticMagenta.copy(alpha = 0.45f),
                                Color.Transparent,
                                ChromaticCyan.copy(alpha = 0.42f)
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
                            GlassTopSpecular,
                            if (breathingGlow) animatedBorderColor.copy(alpha = breathAlpha) else ChromaticCyan.copy(alpha = 0.28f),
                            ChromaticMagenta.copy(alpha = 0.22f)
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
