package com.example

import com.example.ui.theme.AppAppearanceMode
import com.example.ui.theme.AppThemeStyle
import com.example.ui.theme.paletteForTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun multiThemeEngine_hasAllTenThemesWithRequestedBadges() {
        assertEquals(10, AppThemeStyle.entries.size)
        assertEquals("Tokyo Night", AppThemeStyle.TOKYO_NIGHT.displayName)
        assertEquals("CYBER", AppThemeStyle.TOKYO_NIGHT.shortBadge)
        assertEquals("Catppuccin Mocha", AppThemeStyle.CATPPUCCIN_MOCHA.displayName)
        assertEquals("COZY", AppThemeStyle.CATPPUCCIN_MOCHA.shortBadge)
        assertEquals("SynthWave '84", AppThemeStyle.SYNTHWAVE_84.displayName)
        assertEquals("RETRO-GLOW", AppThemeStyle.SYNTHWAVE_84.shortBadge)
        assertEquals("Nord", AppThemeStyle.NORD.displayName)
        assertEquals("ARCTIC MINIMAL", AppThemeStyle.NORD.shortBadge)
        assertEquals("Dracula Pro", AppThemeStyle.DRACULA_PRO.displayName)
        assertEquals("ELITE", AppThemeStyle.DRACULA_PRO.shortBadge)
    }

    @Test
    fun appearanceModes_andPalettesProduceDistinctDarkAndLightSchemes() {
        assertEquals(listOf(AppAppearanceMode.DARK, AppAppearanceMode.LIGHT, AppAppearanceMode.SYSTEM), AppAppearanceMode.entries)
        AppThemeStyle.entries.forEach { style ->
            val darkPalette = paletteForTheme(style, isDark = true)
            val lightPalette = paletteForTheme(style, isDark = false)
            assertTrue(darkPalette.isDark)
            assertTrue(!lightPalette.isDark)
            assertNotEquals(darkPalette.background, lightPalette.background)
            assertNotEquals(darkPalette.textPrimary, lightPalette.textPrimary)
            assertNotEquals(darkPalette.onAccent, lightPalette.onAccent)
        }
    }
}
