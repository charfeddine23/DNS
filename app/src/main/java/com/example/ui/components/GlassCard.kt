package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassSurface
import com.example.ui.theme.GlassTopSpecular

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 22.dp,
    borderColor: Color = GlassBorder,
    borderWidth: Dp = 1.dp,
    backgroundColor: Color = GlassSurface,
    breathingGlow: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val animatedBorderColor by animateColorAsState(
        targetValue = borderColor,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "glass_border_color"
    )

    val breathAlpha = if (breathingGlow) {
        val transition = rememberInfiniteTransition(label = "card_breath")
        val value by transition.animateFloat(
            initialValue = 0.25f,
            targetValue = 0.65f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2400, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "card_border_alpha"
        )
        value
    } else {
        0.40f
    }

    Box(
        modifier = modifier
            .shadow(
                elevation = if (breathingGlow) 14.dp else 6.dp,
                shape = shape,
                ambientColor = animatedBorderColor.copy(alpha = 0.25f),
                spotColor = animatedBorderColor.copy(alpha = 0.30f)
            )
            .clip(shape)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        backgroundColor.copy(alpha = 0.88f),
                        backgroundColor.copy(alpha = 0.70f)
                    )
                )
            )
            .border(
                width = borderWidth,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        if (breathingGlow) animatedBorderColor.copy(alpha = breathAlpha) else GlassTopSpecular,
                        animatedBorderColor.copy(alpha = 0.22f),
                        animatedBorderColor.copy(alpha = 0.06f)
                    )
                ),
                shape = shape
            )
    ) {
        content()
    }
}
