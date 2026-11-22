package io.github.terminalis.machtv

import androidx.compose.ui.graphics.Color
import io.github.terminalis.machtv.core.Subtitle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleSettingsTest {
    private val subtitles = listOf(Subtitle("1", "spa", "a"), Subtitle("2", "eng", "b"), Subtitle("3", "eng", "c"))

    @Test
    fun languageCodesAreDistinctThreeLetterCodes() {
        assertEquals(28, LANGUAGES.size)
        assertEquals(LANGUAGES.size, LANGUAGES.map { it.code }.toSet().size)
        assertTrue(LANGUAGES.all { it.code.length == 3 })
    }

    @Test
    fun languagesMatchAcrossCodeForms() {
        assertTrue(sameLanguage("fre", "fr"))
        assertTrue(sameLanguage("fra", "fre"))
        assertTrue(sameLanguage("en-US", "eng"))
        assertFalse(sameLanguage("eng", "spa"))
        assertFalse(sameLanguage(null, "eng"))
    }

    @Test
    fun languageNames() {
        assertEquals("English", languageName("eng", "Off"))
        assertEquals("French", languageName("fr", "Off"))
        assertEquals("Off", languageName(null, "Off"))
        assertEquals("xyz", languageName("xyz", "Off"))
    }

    @Test
    fun optionsCarryCoreValues() {
        assertEquals(listOf(75, 100, 125, 150), SubtitleSize.entries.map { it.percent })
        assertEquals(listOf("#FFFFFFFF", "#FFFF00FF"), SubtitleColour.entries.map { it.value })
        assertEquals(listOf("#00000000", "#00000099", "#000000FF"), SubtitleBackground.entries.map { it.value })
    }

    @Test
    fun parsesRgba() {
        assertEquals(Color(255, 255, 0, 255), parseRgba("#FFFF00FF"))
        assertEquals(Color(0, 0, 0, 0x99), parseRgba("#00000099"))
        assertNull(parseRgba("white"))
    }

    @Test
    fun attachesTheFirstAddonSubtitleInTheLanguage() {
        assertEquals(subtitles[1], autoSubtitle("eng", listOf(null, "es"), subtitles))
    }

    @Test
    fun attachesNothingWhenEmbeddedMatchesOrSubtitlesAreOff() {
        assertNull(autoSubtitle("eng", listOf("en"), subtitles))
        assertNull(autoSubtitle(null, emptyList(), subtitles))
        assertNull(autoSubtitle("jpn", emptyList(), subtitles))
    }
}
