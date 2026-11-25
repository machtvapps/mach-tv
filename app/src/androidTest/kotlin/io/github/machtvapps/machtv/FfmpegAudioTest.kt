package io.github.machtvapps.machtv

import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.decoder.ffmpeg.FfmpegLibrary
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@UnstableApi
@RunWith(AndroidJUnit4::class)
class FfmpegAudioTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private data class Outcome(val decoder: String?, val error: PlaybackException?)

    private fun playToEnd(asset: String, extensionRendererMode: Int = DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON): Outcome {
        val ended = CountDownLatch(1)
        var decoder: String? = null
        var error: PlaybackException? = null
        lateinit var player: ExoPlayer
        val context = instrumentation.targetContext
        val file = File(context.cacheDir, asset)
        instrumentation.context.assets.open(asset).use { input -> file.outputStream().use { input.copyTo(it) } }
        instrumentation.runOnMainSync {
            player = ExoPlayer.Builder(context, LenientRenderersFactory(context).setExtensionRendererMode(extensionRendererMode)).build()
            player.addAnalyticsListener(object : AnalyticsListener {
                override fun onAudioDecoderInitialized(
                    eventTime: AnalyticsListener.EventTime,
                    decoderName: String,
                    initializedTimestampMs: Long,
                    initializationDurationMs: Long,
                ) {
                    decoder = decoderName
                }
            })
            player.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_ENDED) ended.countDown()
                }

                override fun onPlayerError(playbackError: PlaybackException) {
                    error = playbackError
                    ended.countDown()
                }
            })
            player.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            player.prepare()
            player.play()
        }
        assertTrue("$asset did not finish", ended.await(30, TimeUnit.SECONDS))
        instrumentation.runOnMainSync { player.release() }
        file.delete()
        Log.i("FfmpegAudioTest", "$asset decoder=$decoder error=$error")
        return Outcome(decoder, error)
    }

    @Test
    fun ffmpegHasEveryBuiltDecoder() {
        assertTrue(FfmpegLibrary.isAvailable())
        for (mimeType in listOf(MimeTypes.AUDIO_TRUEHD, MimeTypes.AUDIO_DTS, MimeTypes.AUDIO_AC3, MimeTypes.AUDIO_E_AC3)) {
            assertTrue(mimeType, FfmpegLibrary.supportsFormat(mimeType))
        }
    }

    @Test
    fun trueHdPlaysThroughFfmpeg() {
        val outcome = playToEnd("truehd-5.1.mkv")
        assertNull(outcome.error)
        assertTrue(outcome.decoder.toString(), outcome.decoder?.startsWith("ffmpeg") == true)
    }

    @Test
    fun dtsPlaysToTheEnd() {
        assertNull(playToEnd("dts-5.1.mkv").error)
    }

    @Test
    fun ac3AndEac3AreDecodedRatherThanPassedThrough() {
        for (asset in listOf("ac3-5.1.mkv", "eac3-5.1.mkv")) {
            val outcome = playToEnd(asset)
            assertNull(outcome.error)
            assertNotNull(asset, outcome.decoder)
        }
    }

    @Test
    fun ffmpegDecodesAc3AndEac3() {
        for (asset in listOf("ac3-5.1.mkv", "eac3-5.1.mkv")) {
            val outcome = playToEnd(asset, DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            assertNull(outcome.error)
            assertTrue(outcome.decoder.toString(), outcome.decoder?.startsWith("ffmpeg") == true)
        }
    }
}
