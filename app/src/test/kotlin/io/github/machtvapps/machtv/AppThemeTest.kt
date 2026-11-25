package io.github.machtvapps.machtv

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppThemeTest {
    @Test
    fun missingThemeReadsAsDark() {
        assertEquals(AppTheme.Dark, parseTheme(null))
    }

    @Test
    fun storedThemesReadBack() {
        assertEquals(AppTheme.Light, parseTheme("light"))
        assertEquals(AppTheme.Dark, parseTheme("dark"))
    }

    @Test
    fun unknownThemeReadsAsDark() {
        assertEquals(AppTheme.Dark, parseTheme("sepia"))
    }

    @Test
    fun themeRowOffersLightThenDark() {
        assertEquals(listOf("Light", "Dark"), AppTheme.entries.map { it.label })
    }

    @Test
    fun lightInksTheSlateDark() {
        val palette = AppTheme.Light.palette
        assertEquals(Color(0xFF16181B), palette.ink)
        assertEquals(Color.White, palette.focusInk)
        assertFalse(palette.shadow)
    }

    @Test
    fun darkKeepsWhiteInk() {
        val palette = AppTheme.Dark.palette
        assertEquals(Color.White, palette.ink)
        assertEquals(Color(0xDD000000), palette.focusInk)
        assertTrue(palette.shadow)
    }
}
