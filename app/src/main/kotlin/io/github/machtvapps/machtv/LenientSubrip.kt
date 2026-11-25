package io.github.machtvapps.machtv

import android.content.Context
import android.os.Looper
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.text.SubtitleDecoderFactory
import androidx.media3.exoplayer.text.TextOutput
import androidx.media3.exoplayer.text.TextRenderer
import androidx.media3.extractor.text.SimpleSubtitleDecoder
import androidx.media3.extractor.text.Subtitle
import androidx.media3.extractor.text.SubtitleDecoder
import androidx.media3.extractor.text.subrip.SubripParser

fun trimSubtitleLines(data: ByteArray, offset: Int, length: Int): ByteArray =
    String(data, offset, length, Charsets.UTF_8).lineSequence().joinToString("\n") { it.trimEnd() }.toByteArray(Charsets.UTF_8)

@UnstableApi
private class TrimmedSubripDecoder : SimpleSubtitleDecoder("TrimmedSubripDecoder") {
    private val parser = SubripParser()

    override fun decode(data: ByteArray, length: Int, reset: Boolean): Subtitle {
        if (reset) parser.reset()
        val trimmed = trimSubtitleLines(data, 0, length)
        return parser.parseToLegacySubtitle(trimmed, 0, trimmed.size)
    }
}

@UnstableApi
private object LenientSubtitleDecoderFactory : SubtitleDecoderFactory {
    override fun supportsFormat(format: Format): Boolean = SubtitleDecoderFactory.DEFAULT.supportsFormat(format)

    override fun createDecoder(format: Format): SubtitleDecoder =
        if (format.sampleMimeType == MimeTypes.APPLICATION_SUBRIP) TrimmedSubripDecoder() else SubtitleDecoderFactory.DEFAULT.createDecoder(format)
}

@UnstableApi
class LenientRenderersFactory(context: Context) : DefaultRenderersFactory(context) {
    init {
        setExtensionRendererMode(EXTENSION_RENDERER_MODE_ON)
    }

    override fun buildTextRenderers(
        context: Context,
        output: TextOutput,
        outputLooper: Looper,
        extensionRendererMode: Int,
        out: ArrayList<Renderer>,
    ) {
        @Suppress("DEPRECATION")
        out.add(TextRenderer(output, outputLooper, LenientSubtitleDecoderFactory).apply { experimentalSetLegacyDecodingEnabled(true) })
    }

    override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioOutputPlaybackParameters: Boolean): AudioSink? =
        super.buildAudioSink(context, enableFloatOutput, enableAudioOutputPlaybackParameters)?.let(::Ac3DecodingSink)
}
