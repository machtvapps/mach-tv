package io.github.terminalis.machtv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import kotlinx.coroutines.delay

fun codecName(mime: String?): String? = when (mime) {
    null -> null
    MimeTypes.VIDEO_H264 -> "H.264"
    MimeTypes.VIDEO_H265 -> "HEVC"
    MimeTypes.VIDEO_AV1 -> "AV1"
    MimeTypes.VIDEO_VP9 -> "VP9"
    MimeTypes.AUDIO_AAC -> "AAC"
    MimeTypes.AUDIO_AC3 -> "AC3"
    MimeTypes.AUDIO_E_AC3 -> "E-AC3"
    MimeTypes.AUDIO_E_AC3_JOC -> "E-AC3 Atmos"
    MimeTypes.AUDIO_TRUEHD -> "TrueHD"
    MimeTypes.AUDIO_DTS -> "DTS"
    MimeTypes.AUDIO_DTS_HD -> "DTS-HD"
    MimeTypes.AUDIO_MPEG -> "MP3"
    MimeTypes.AUDIO_OPUS -> "Opus"
    MimeTypes.AUDIO_FLAC -> "FLAC"
    else -> mime.substringAfter('/')
}

fun videoInfo(width: Int, height: Int, mime: String?): String =
    listOfNotNull("$width × $height".takeIf { width > 0 && height > 0 }, codecName(mime)).joinToString(" · ")

fun audioInfo(mime: String?, channels: Int): String =
    listOfNotNull(codecName(mime), "$channels ch".takeIf { channels > 0 }).joinToString(" · ")

@Composable
fun StreamInfoPanel(video: Format?, audio: Format?, engine: String?, infoHash: String, onClose: () -> Unit) {
    var swarm by remember { mutableStateOf<SwarmStats?>(null) }
    BackHandler(onBack = onClose)
    LaunchedEffect(engine, infoHash) {
        if (engine == null || infoHash.isEmpty()) return@LaunchedEffect
        while (true) {
            fetchSwarmStats(engine, infoHash)?.let { swarm = it }
            delay(SWARM_POLL_MS)
        }
    }
    val lines = listOfNotNull(
        video?.let { "Video" to videoInfo(it.width, it.height, it.sampleMimeType) },
        audio?.let { "Audio" to audioInfo(it.sampleMimeType, it.channelCount) },
        if (infoHash.isEmpty()) null else "Torrent" to (swarm?.let(::swarmText) ?: "Loading"),
    )
    Box(Modifier.fillMaxSize()) {
        MetalPanel(Modifier.align(Alignment.CenterEnd).padding(32.dp).width(560.dp)) {
            BasicText("Stream info", style = MachText.panelTitle)
            lines.forEach { (label, value) ->
                Row(Modifier.padding(top = 16.dp)) {
                    BasicText(label, style = MachText.small, modifier = Modifier.width(120.dp).alignByBaseline())
                    BasicText(value, style = MachText.rowTitle, modifier = Modifier.alignByBaseline())
                }
            }
        }
    }
}
