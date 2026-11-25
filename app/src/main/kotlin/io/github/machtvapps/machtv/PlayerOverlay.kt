package io.github.machtvapps.machtv

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private val BarShade = Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000)))
private val BufferedTrack = Color(0x80FFFFFF)

@Composable
fun PlayerOverlay(
    title: String,
    position: Long,
    buffered: Long,
    duration: Long,
    paused: Boolean,
    series: Boolean,
    nextEpisode: Boolean,
    playFocus: FocusRequester,
    onSeekKey: (PlayerKey) -> Boolean,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onSubtitles: () -> Unit,
    onAudio: () -> Unit,
    onNextEpisode: () -> Unit,
    onEpisodes: () -> Unit,
    onStreams: () -> Unit,
    onStreamInfo: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(BarShade)
                .padding(start = 48.dp, end = 48.dp, top = 96.dp, bottom = 28.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText(playerClock(position), style = MachText.small)
                ProgressLine(
                    position,
                    buffered,
                    duration,
                    Modifier.weight(1f).padding(horizontal = 20.dp).focusProperties { down = playFocus },
                    onSeekKey,
                )
                BasicText("-" + playerClock(duration - position), style = MachText.small)
            }
            Box(Modifier.fillMaxWidth().padding(top = 28.dp)) {
                Row(Modifier.align(Alignment.CenterStart), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BarIcon(if (paused) PLAY_GLYPH else PAUSE_GLYPH, if (paused) "Play" else "Pause", onPlayPause, Modifier.focusRequester(playFocus))
                    BarIcon(BACK_10_GLYPH, "Back 10 seconds", { onSeek(-SEEK_MS) })
                    BarIcon(FORWARD_10_GLYPH, "Forward 10 seconds", { onSeek(SEEK_MS) })
                }
                BasicText(
                    title,
                    style = MachText.rowTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.Center).widthIn(max = 240.dp),
                )
                Row(Modifier.align(Alignment.CenterEnd), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BarIcon(SUBTITLES_GLYPH, "Subtitles", onSubtitles)
                    BarIcon(AUDIO_GLYPH, "Audio", onAudio)
                    if (nextEpisode) BarIcon(NEXT_EPISODE_GLYPH, "Next Episode", onNextEpisode)
                    if (series) BarIcon(EPISODES_GLYPH, "Episodes", onEpisodes)
                    BarIcon(STREAMS_GLYPH, "Choose Stream", onStreams)
                    BarIcon(INFO_GLYPH, "Stream info", onStreamInfo)
                }
            }
        }
    }
}

@Composable
private fun ProgressLine(position: Long, buffered: Long, duration: Long, modifier: Modifier, onKey: (PlayerKey) -> Boolean) {
    var focused by remember { mutableStateOf(false) }
    fun share(ms: Long) = if (duration > 0) (ms.toFloat() / duration).coerceIn(0f, 1f) else 0f
    Box(
        modifier
            .height(if (focused) 8.dp else 4.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(ProgressTrack)
            .onKeyEvent { event -> event.type == KeyEventType.KeyDown && onKey(playerKey(event.key)) }
            .onFocusChanged { focused = it.isFocused }
            .focusable(),
    ) {
        Box(Modifier.fillMaxWidth(share(buffered)).fillMaxHeight().background(BufferedTrack))
        Box(Modifier.fillMaxWidth(share(position)).fillMaxHeight().background(Color.White))
    }
}

@Composable
private fun BarIcon(glyph: String, name: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val t by animateFloatAsState(if (focused) 1f else 0f, tween(FOCUS_MS, easing = OutCubic), label = "icon")
    Box(Modifier.size(44.dp)) {
        BasicText(
            name,
            style = MachText.small.copy(color = Color.White),
            maxLines = 1,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .wrapContentWidth(unbounded = true)
                .offset(y = (-26).dp)
                .graphicsLayer { alpha = t },
        )
        Box(
            modifier
                .fillMaxSize()
                .metalBody(rest = false) { t }
                .onFocusChanged { focused = it.isFocused }
                .clickable(interactionSource = null, indication = null, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(glyph, style = MachText.barGlyph.copy(color = lerp(Color.White, MetalInk, t)))
        }
    }
}
