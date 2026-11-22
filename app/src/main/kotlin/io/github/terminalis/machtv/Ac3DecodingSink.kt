package io.github.terminalis.machtv

import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink

private val DECODED_MIME_TYPES = setOf(MimeTypes.AUDIO_AC3, MimeTypes.AUDIO_E_AC3, MimeTypes.AUDIO_E_AC3_JOC)

@UnstableApi
class Ac3DecodingSink(sink: AudioSink) : ForwardingAudioSink(sink) {
    override fun supportsFormat(format: Format): Boolean =
        format.sampleMimeType !in DECODED_MIME_TYPES && super.supportsFormat(format)

    override fun getFormatSupport(format: Format): Int =
        if (format.sampleMimeType in DECODED_MIME_TYPES) AudioSink.SINK_FORMAT_UNSUPPORTED else super.getFormatSupport(format)
}
