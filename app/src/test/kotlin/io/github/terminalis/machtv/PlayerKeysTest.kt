package io.github.terminalis.machtv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerKeysTest {
    @Test
    fun hiddenOverlayShowsOnAnyKeyExceptBack() {
        PlayerKey.entries.filter { it != PlayerKey.Back }.forEach { assertTrue(it.name, hiddenOverlayKey(it).showOverlay) }
        assertFalse(hiddenOverlayKey(PlayerKey.Back).consumed)
        assertFalse(hiddenOverlayKey(PlayerKey.Back).showOverlay)
    }

    @Test
    fun hiddenOverlayLeftAndRightAlsoSeek() {
        assertEquals(-SEEK_MS, hiddenOverlayKey(PlayerKey.Left).seekMs)
        assertEquals(SEEK_MS, hiddenOverlayKey(PlayerKey.Right).seekMs)
        assertEquals(0L, hiddenOverlayKey(PlayerKey.Down).seekMs)
    }

    @Test
    fun hiddenOverlayOkAlsoTogglesPause() {
        assertTrue(hiddenOverlayKey(PlayerKey.Ok).togglePause)
        assertFalse(hiddenOverlayKey(PlayerKey.Right).togglePause)
    }

    @Test
    fun seekBarSeeksAndLeavesUpAndDownToFocus() {
        assertEquals(-SEEK_MS, seekBarKey(PlayerKey.Left).seekMs)
        assertEquals(SEEK_MS, seekBarKey(PlayerKey.Right).seekMs)
        assertTrue(seekBarKey(PlayerKey.Ok).togglePause)
        assertFalse(seekBarKey(PlayerKey.Down).consumed)
        assertFalse(seekBarKey(PlayerKey.Up).consumed)
    }

    @Test
    fun nextEpisodeAppearsAtNinetyFivePercent() {
        assertFalse(nextEpisodeVisible(949_000, 1_000_000, hasNext = true))
        assertTrue(nextEpisodeVisible(950_000, 1_000_000, hasNext = true))
        assertFalse(nextEpisodeVisible(990_000, 1_000_000, hasNext = false))
        assertFalse(nextEpisodeVisible(0, 0, hasNext = true))
    }

    @Test
    fun clockShowsHoursOnlyWhenNeeded() {
        assertEquals("0:05", playerClock(5_000))
        assertEquals("12:34", playerClock(754_000))
        assertEquals("1:02:03", playerClock(3_723_000))
    }
}
