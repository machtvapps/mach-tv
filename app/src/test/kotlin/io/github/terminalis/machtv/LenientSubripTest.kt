package io.github.terminalis.machtv

import org.junit.Assert.assertEquals
import org.junit.Test

class LenientSubripTest {
    @Test
    fun subripLinesLoseTrailingWhitespace() {
        val raw = "xx1 \r\n00:00:45,000 --> 00:00:59,000\r\nHello, there...  \r\n\r\n".toByteArray()
        val trimmed = trimSubtitleLines(raw, 2, raw.size - 2)
        assertEquals("1\n00:00:45,000 --> 00:00:59,000\nHello, there...\n\n", String(trimmed))
    }
}
