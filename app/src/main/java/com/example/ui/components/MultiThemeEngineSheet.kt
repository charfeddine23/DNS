package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AppThemeStyle
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.HoloViolet
import com.example.ui.theme.LocalIsDarkTheme
import com.example.ui.theme.NeuBaseDark
import com.example.ui.theme.OnAccentInk
import com.example.ui.theme.SubtleSurfaceBorder
import com.example.ui.theme.SubtleSurfaceFill
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.paletteForTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiThemeEngineSheet(
    selectedTheme: AppThemeStyle,
    onSelectTheme: (AppThemeStyle) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = if (selectedTheme == AppThemeStyle.NEUMORPHISM) NeuBaseDark else DarkSurfaceElevated
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .testTag("multi_theme_engine_sheet")
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(ElectricCyan.copy(alpha = 0.28f), HoloViolet.copy(alpha = 0.28f))
                            ),
                            shape = CircleShape
                        )
                        .border(1.dp, ElectricCyan.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Multi-Theme Engine",
                        tint = ElectricCyan,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Multi-Theme Engine",
                        color = TextPrimary,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "10 optical themes • Adapts smoothly to Dark & Light",
                        color = TextSecondary,
                        fontSize = 11.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AppThemeStyle.entries.forEach { style ->
                    ThemeOptionCard(
                        style = style,
                        isSelected = style == selectedTheme,
                        onSelect = { onSelectTheme(style) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeOptionCard(
    style: AppThemeStyle,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    val previewPalette = remember(style, isDark) { paletteForTheme(style, isDark) }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.975f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "theme_card_scale"
    )

    val badgeColor = when (style) {
        AppThemeStyle.CHROMATIC_FROSTED_LIQUID -> previewPalette.primaryAccent
        AppThemeStyle.HOLOGRAPHIC_LIQUID_GLASS -> previewPalette.secondaryGlowAccent
        AppThemeStyle.FROSTED_GLASSMORPHISM -> if (isDark) Color(0xFF8BE0FF) else previewPalette.primaryAccent
        AppThemeStyle.GLASS_PSD_MOCKUP -> if (isDark) Color(0xFF6BA6FF) else previewPalette.primaryAccent
        AppThemeStyle.NEUMORPHISM -> previewPalette.emeraldAccent
        AppThemeStyle.TOKYO_NIGHT -> previewPalette.primaryAccent
        AppThemeStyle.CATPPUCCIN_MOCHA -> previewPalette.tertiaryAccent
        AppThemeStyle.SYNTHWAVE_84 -> previewPalette.secondaryGlowAccent
        AppThemeStyle.NORD -> previewPalette.primaryAccent
        AppThemeStyle.DRACULA_PRO -> previewPalette.primaryAccent
    }

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale),
        cornerRadius = 18.dp,
        borderColor = if (isSelected) previewPalette.primaryAccent else badgeColor.copy(alpha = 0.38f),
        borderWidth = if (isSelected) 1.6.dp else 1.dp,
        breathingGlow = isSelected,
        overrideThemeStyle = style
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(color = badgeColor),
                    onClick = onSelect
                )
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .testTag("theme_option_${style.name}"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ThemeSwatchOrb(style = style, isDark = isDark)

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = style.displayName,
                        color = previewPalette.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(badgeColor.copy(alpha = 0.16f))
                            .border(0.8.dp, badgeColor.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = style.shortBadge,
                            color = badgeColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.6.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = style.description,
                    color = previewPalette.textSecondary,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) ElectricCyan else SubtleSurfaceFill)
                    .border(
                        width = 1.dp,
                        color = if (isSelected) ElectricCyan else SubtleSurfaceBorder,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = OnAccentInk,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeSwatchOrb(style: AppThemeStyle, isDark: Boolean) {
    val palette = remember(style, isDark) { paletteForTheme(style, isDark) }
    val brush = when (style) {
        AppThemeStyle.CHROMATIC_FROSTED_LIQUID -> Brush.sweepGradient(
            listOf(palette.primaryAccent, palette.secondaryGlowAccent, palette.amberAccent, palette.primaryAccent)
        )
        AppThemeStyle.HOLOGRAPHIC_LIQUID_GLASS -> Brush.sweepGradient(
            listOf(palette.primaryAccent, palette.tertiaryAccent, palette.secondaryGlowAccent, palette.emeraldAccent, palette.primaryAccent)
        )
        AppThemeStyle.FROSTED_GLASSMORPHISM -> Brush.verticalGradient(
            listOf(Color(0xFFE0F4FF), Color(0xFF4CA5D9))
        )
        AppThemeStyle.GLASS_PSD_MOCKUP -> Brush.linearGradient(
            listOf(Color(0xFFFFFFFF), Color(0xFF3A86FF), Color(0xFF0A1428))
        )
        AppThemeStyle.NEUMORPHISM -> Brush.linearGradient(
            listOf(palette.neuHighlightEdge, palette.neuBase, palette.neuShadowEdge)
        )
        AppThemeStyle.TOKYO_NIGHT -> Brush.sweepGradient(
            listOf(Color(0xFF7DCFFF), Color(0xFF7AA2F7), Color(0xFFBB9AF7), Color(0xFF73DACA), Color(0xFF7DCFFF))
        )
        AppThemeStyle.CATPPUCCIN_MOCHA -> Brush.sweepGradient(
            listOf(Color(0xFFCBA6F7), Color(0xFF89B4FA), Color(0xFFFAB387), Color(0xFFA6E3A1), Color(0xFFCBA6F7))
        )
        AppThemeStyle.SYNTHWAVE_84 -> Brush.sweepGradient(
            listOf(Color(0xFFFF7EDB), Color(0xFF36F9F6), Color(0xFFFEDE5D), Color(0xFF241B2F), Color(0xFFFF7EDB))
        )
        AppThemeStyle.NORD -> Brush.linearGradient(
            listOf(Color(0xFFECEFF4), Color(0xFF88C0D0), Color(0xFF5E81AC), Color(0xFF2E3440))
        )
        AppThemeStyle.DRACULA_PRO -> Brush.sweepGradient(
            listOf(Color(0xFF9580FF), Color(0xFF80FFEA), Color(0xFF8AFF80), Color(0xFFFF80BF), Color(0xFF9580FF))
        )
    }

    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(brush)
            .border(1.dp, SubtleSurfaceBorder, CircleShape)
    )
}
