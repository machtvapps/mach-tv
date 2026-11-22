package io.github.terminalis.machtv

import androidx.media3.common.MimeTypes
import org.junit.Assert.assertEquals
import org.junit.Test

class SubtitleTypeTest {
    @Test
    fun subtitleTypeFollowsTheFileExtension() {
        assertEquals(MimeTypes.TEXT_VTT, subtitleMimeType("https://example.org/subs/film.en.VTT?token=1"))
        assertEquals(MimeTypes.TEXT_SSA, subtitleMimeType("https://example.org/subs/film.ass"))
        assertEquals(MimeTypes.TEXT_SSA, subtitleMimeType("https://example.org/subs/film.ssa"))
        assertEquals(MimeTypes.APPLICATION_SUBRIP, subtitleMimeType("https://subs5.strem.io/en/download/subencoding-stremio-utf8/src-api/file/1954412181"))
        assertEquals(MimeTypes.APPLICATION_SUBRIP, subtitleMimeType("https://example.org/subs/film.srt"))
    }
}
