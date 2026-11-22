package io.github.terminalis.machtv

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.terminalis.machtv.core.LibraryItem
import io.github.terminalis.machtv.core.MetaItem
import io.github.terminalis.machtv.core.MetaVideo
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val airDateFormat = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US).withZone(ZoneOffset.UTC)

fun airDate(released: String?): String? = released?.let { runCatching { airDateFormat.format(Instant.parse(it)) }.getOrNull() }

fun seasonsOf(videos: List<MetaVideo>): List<Int> = videos.mapNotNull { it.season }.filter { it > 0 }.distinct().sorted()

@Composable
fun episodeTag(video: MetaVideo): String = "S${video.season}, E${video.episode}"

@Composable
fun DetailsEpisodes(
    meta: MetaItem,
    library: LibraryItem?,
    watched: List<String>,
    focusVideo: String?,
    seasonFocus: FocusRequester,
    onPick: (MetaVideo) -> Unit,
    onMarkSeason: (Int, Boolean) -> Unit,
    onMarkVideo: (MetaVideo, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val seasons = remember(meta) { seasonsOf(meta.videos) }
    val current = meta.videos.firstOrNull { it.id == (focusVideo ?: library?.state?.videoId) }?.season
    var season by remember(meta.id) { mutableIntStateOf(current ?: seasons.firstOrNull() ?: 1) }
    val episodes = remember(meta, season) { meta.videos.filter { it.season == season }.sortedBy { it.episode } }
    val listState = rememberLazyListState(episodes.indexOfFirst { it.id == focusVideo }.coerceAtLeast(0))
    val returned = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (focusVideo != null) {
            withFrameNanos { }
            returned.requestFocus()
        }
    }
    MetalPanel(modifier) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (seasons.size > 1) {
                seasons.forEach {
                    PillButton(
                        "Season $it",
                        { season = it },
                        if (it == season) Modifier.focusRequester(seasonFocus) else Modifier,
                        compact = true,
                        selected = it == season,
                        onHold = { onMarkSeason(it, !seasonWatched(meta.videos, it, watched)) },
                    )
                }
            } else {
                BasicText("Season $season", style = MachText.rowTitle, modifier = Modifier.padding(horizontal = 8.dp))
            }
        }
        LazyColumn(Modifier.padding(top = 16.dp), state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(episodes, key = { it.id }) { video ->
                val state = library?.state
                val progress = if (state != null && state.videoId == video.id && state.duration > 0) {
                    (state.timeOffset.toFloat() / state.duration).coerceIn(0f, 1f)
                } else {
                    null
                }
                EpisodeCard(
                    video,
                    progress,
                    video.id in watched,
                    if (video.id == focusVideo) Modifier.focusRequester(returned) else Modifier,
                    onHold = { onMarkVideo(video, video.id !in watched) },
                ) { onPick(video) }
            }
        }
    }
}

@Composable
private fun EpisodeCard(video: MetaVideo, progress: Float?, watched: Boolean, modifier: Modifier, onHold: () -> Unit, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val hold = remember { HoldState() }
    Row(
        modifier
            .fillMaxWidth()
            .metalCard(focused, rest = false)
            .onFocusChanged { focused = it.isFocused }
            .holdPress(hold, onHold)
            .clickable(interactionSource = null, indication = null, onClick = hold.unlessHeld(onClick))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(144.dp, 81.dp).clip(RoundedCornerShape(8.dp)).background(TileSurface)) {
            AsyncImage(model = video.thumbnail, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            progress?.let {
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(ProgressTrack)) {
                    Box(Modifier.fillMaxWidth(it).height(3.dp).background(Color.White))
                }
            }
        }
        Column(Modifier.padding(start = 16.dp)) {
            BasicText("${video.episode}. ${video.title}", style = MachText.rowTitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
            listOfNotNull(airDate(video.released), if (watched) "Watched" else null)
                .joinToString(" · ")
                .takeIf { it.isNotEmpty() }
                ?.let { BasicText(it, style = MachText.small, modifier = Modifier.padding(top = 6.dp)) }
        }
    }
}
