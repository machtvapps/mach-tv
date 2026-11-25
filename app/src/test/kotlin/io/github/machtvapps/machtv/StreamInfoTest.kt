package io.github.machtvapps.machtv

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamInfoTest {
    @Test
    fun videoShowsResolutionAndCodec() {
        assertEquals("1920 × 1080 · H.264", videoInfo(1920, 1080, "video/avc"))
    }

    @Test
    fun videoWithoutSizeShowsOnlyCodec() {
        assertEquals("HEVC", videoInfo(-1, -1, "video/hevc"))
    }

    @Test
    fun audioShowsCodecAndChannels() {
        assertEquals("AC3 · 6 ch", audioInfo("audio/ac3", 6))
    }

    @Test
    fun unknownCodecShowsItsMimeSubtype() {
        assertEquals("x-unknown · 2 ch", audioInfo("audio/x-unknown", 2))
    }

    @Test
    fun swarmShowsPeersAndSpeed() {
        assertEquals("4 seeders · 12 peers · 1.4 MB/s", swarmText(SwarmStats(seeders = 4, peers = 12, bytesPerSecond = 1_450_000)))
    }
}
