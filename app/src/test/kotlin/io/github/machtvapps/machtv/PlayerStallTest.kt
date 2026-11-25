package io.github.machtvapps.machtv

import androidx.media3.common.PlaybackException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayerStallTest {
    @Test
    fun loadingTextWithoutStatsSaysLoading() {
        assertEquals("Loading", loadingText(null))
    }

    @Test
    fun loadingTextShowsPeersAndSpeed() {
        assertEquals("Loading · 3 seeders · 5 peers · 120 B/s", loadingText(SwarmStats(seeders = 3, peers = 5, bytesPerSecond = 120)))
    }

    @Test
    fun loadingTextCountsOnePeerInTheSingular() {
        assertEquals("Loading · 1 seeder · 1 peer · 1.4 MB/s", loadingText(SwarmStats(seeders = 1, peers = 1, bytesPerSecond = 1_450_000)))
    }

    @Test
    fun speedTextUsesBytesKilobytesAndMegabytes() {
        assertEquals("999 B/s", speedText(999))
        assertEquals("1 KB/s", speedText(1_000))
        assertEquals("999 KB/s", speedText(999_999))
        assertEquals("1.0 MB/s", speedText(1_000_000))
        assertEquals("12.3 MB/s", speedText(12_345_678))
    }

    @Test
    fun failureMessageForAnIoErrorSaysTheStreamIsNotSendingData() {
        assertEquals("This stream isn't sending data.", failureMessage(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT))
    }

    @Test
    fun failureMessageForAnotherErrorSaysTheStreamCannotBePlayed() {
        assertEquals(UNPLAYABLE_MESSAGE, failureMessage(PlaybackException.ERROR_CODE_DECODER_INIT_FAILED))
        assertEquals("This stream can't be played.", UNPLAYABLE_MESSAGE)
    }

    @Test
    fun parseSwarmStatsReadsPeersAndDownloadSpeed() {
        val json = """
            {"connectionTries":0,"downloadSpeed":5371.0,"downloaded":0,"files":[],"hasMetadata":false,
             "infoHash":"944a0251967aa4cfa96ca63617f874df03176a0e","peers":2,"seeds":1,"queued":0,"streamProgress":0.0175,
             "swarmConnections":2,"swarmPaused":false,"unchoked":0,"uploadSpeed":134.0,"uploaded":0}
        """
        assertEquals(SwarmStats(seeders = 1, peers = 2, bytesPerSecond = 5371), parseSwarmStats(json))
    }

    @Test
    fun parseSwarmStatsWithoutPeersIsNull() {
        assertNull(parseSwarmStats("""{"downloadSpeed":5371.0}"""))
    }

    @Test
    fun parseSwarmStatsOfAnErrorBodyIsNull() {
        assertNull(parseSwarmStats("Engine not found"))
    }
}
