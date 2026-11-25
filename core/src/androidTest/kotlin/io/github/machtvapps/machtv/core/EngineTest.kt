package io.github.machtvapps.machtv.core

import android.content.Context
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EngineTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var dataDir: File

    @Before
    fun createDataDir() {
        dataDir = File(context.cacheDir, "engine-${System.nanoTime()}").apply { mkdirs() }
    }

    @After
    fun deleteDataDir() {
        dataDir.deleteRecursively()
    }

    @Test
    fun torrentPlaysThroughEngine() {
        val url = runBlocking { streamingUrl() }
        assertTrue(url, url.startsWith("$ENGINE_URL/$INFO_HASH/0"))
        Engine.start(context)
        val player = onMain {
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(url))
                prepare()
                play()
            }
        }
        try {
            val deadline = SystemClock.elapsedRealtime() + PLAYBACK_TIMEOUT_MS
            var playback = onMain { Playback.of(player) }
            while (playback.positionMs < PLAYED_MS && playback.error == null && SystemClock.elapsedRealtime() < deadline) {
                Thread.sleep(POLL_MS)
                playback = onMain { Playback.of(player) }
            }
            assertNull(playback.error)
            assertTrue("position ${playback.positionMs} ms", playback.positionMs >= PLAYED_MS)
            assertEquals("1920x1080", playback.size)
            assertTrue("decoded ${playback.decodedFrames} frames", playback.decodedFrames > 0)
        } finally {
            onMain { player.release() }
            Engine.stop()
        }
    }

    @Test
    fun stopReleasesPort() {
        try {
            assertEquals(ENGINE_URL, Engine.start(context))
            assertTrue(accepts())
        } finally {
            Engine.stop()
        }
        assertFalse(accepts())
    }

    private suspend fun streamingUrl(): String {
        val core = Core.start(dataDir)
        try {
            core.installTorrentio()
            core.dispatch(Action.LoadMetaDetails(type = "movie", id = MOVIE_ID, streamId = MOVIE_ID), Field.META_DETAILS)
            val details = withTimeout(NETWORK_TIMEOUT_MS) {
                core.metaDetails.first { torrentioStreams(it)?.content?.type in SETTLED }
            }
            val streams = torrentioStreams(details)!!.content!!
            assertEquals("Ready", streams.type)
            val stream = streams.content!!.jsonArray.map { it.jsonObject }
                .firstOrNull { it["infoHash"]?.jsonPrimitive?.content == INFO_HASH }
            assertNotNull("Torrentio no longer lists $INFO_HASH", stream)
            core.dispatch(Action.LoadPlayer(stream!!), Field.PLAYER)
            val player = withTimeout(NETWORK_TIMEOUT_MS) {
                core.player.first { it?.stream?.type in SETTLED }
            }
            val loaded = player!!.stream!!
            assertEquals("Ready", loaded.type)
            return loaded.content!!.jsonArray[0].jsonObject.getValue("streaming_url").jsonPrimitive.content
        } finally {
            core.close()
        }
    }

    private fun torrentioStreams(details: MetaDetailsState?): StreamList? =
        details?.streams?.firstOrNull { it.request.base == TORRENTIO_URL }

    private fun accepts(): Boolean =
        runCatching {
            Socket().use { it.connect(InetSocketAddress(ENGINE_HOST, ENGINE_PORT), CONNECT_TIMEOUT_MS) }
        }.isSuccess

    private fun <T> onMain(block: () -> T): T {
        var result: Result<T>? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync { result = runCatching(block) }
        return result!!.getOrThrow()
    }

    private data class Playback(
        val positionMs: Long,
        val error: PlaybackException?,
        val size: String?,
        val decodedFrames: Int,
    ) {
        companion object {
            @OptIn(UnstableApi::class)
            fun of(player: ExoPlayer): Playback {
                val counters = player.videoDecoderCounters?.apply { ensureUpdated() }
                return Playback(
                    positionMs = player.currentPosition,
                    error = player.playerError,
                    size = player.videoFormat?.let { "${it.width}x${it.height}" },
                    decodedFrames = counters?.let {
                        it.renderedOutputBufferCount + it.skippedOutputBufferCount + it.droppedBufferCount
                    } ?: 0,
                )
            }
        }
    }

    private companion object {
        const val MOVIE_ID = "tt1254207"
        const val INFO_HASH = "88594aaacbde40ef3e2510c47374ec0aa396c08e"
        const val ENGINE_HOST = "127.0.0.1"
        const val ENGINE_PORT = 11479
        const val ENGINE_URL = "http://127.0.0.1:11479"
        const val PLAYED_MS = 10_000L
        const val PLAYBACK_TIMEOUT_MS = 300_000L
        const val POLL_MS = 500L
        const val CONNECT_TIMEOUT_MS = 1_000
    }
}
