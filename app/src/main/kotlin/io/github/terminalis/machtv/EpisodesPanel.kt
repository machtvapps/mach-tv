package io.github.terminalis.machtv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.terminalis.machtv.core.LibraryItem
import io.github.terminalis.machtv.core.MetaItem
import io.github.terminalis.machtv.core.MetaVideo

@Composable
fun EpisodesPanel(
    meta: MetaItem,
    library: LibraryItem?,
    watched: List<String>,
    onPick: (MetaVideo) -> Unit,
    onClose: () -> Unit,
) {
    val seasons = remember(meta) { meta.videos.mapNotNull { it.season }.filter { it > 0 }.distinct().sorted() }
    val current = meta.videos.firstOrNull { it.id == library?.state?.videoId }?.season
    var season by remember { mutableIntStateOf(current ?: seasons.firstOrNull() ?: 1) }
    val episodes = meta.videos.filter { it.season == season }.sortedBy { it.episode }
    val firstEpisode = remember { FocusRequester() }
    BackHandler(onBack = onClose)
    LaunchedEffect(season, episodes.isNotEmpty()) {
        if (episodes.isNotEmpty()) {
            withFrameNanos { }
            firstEpisode.requestFocus()
        }
    }
    Box(Modifier.fillMaxSize().background(Color(0xCC000000)).padding(48.dp)) {
        MetalPanel(Modifier.fillMaxSize()) {
            BasicText(meta.name, style = MachText.panelTitle)
            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                seasons.forEach { number -> PillButton("Season $number", { season = number }, compact = true) }
            }
            LazyColumn(Modifier.padding(top = 16.dp)) {
                itemsIndexed(episodes, key = { _, video -> video.id }) { index, video ->
                    FocusRow(
                        onClick = { onPick(video) },
                        modifier = if (index == 0) Modifier.focusRequester(firstEpisode) else Modifier,
                    ) {
                        AsyncImage(
                            model = video.thumbnail,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(192.dp, 108.dp).clip(RoundedCornerShape(8.dp)).background(TileSurface),
                        )
                        Column(Modifier.padding(start = 16.dp)) {
                            BasicText("E${video.episode} · ${video.title}", style = MachText.rowTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            video.overview?.let {
                                BasicText(it, style = MachText.body, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                            }
                            if (video.id in watched) {
                                BasicText("Watched", style = MachText.small, modifier = Modifier.padding(top = 8.dp))
                            }
                            val state = library?.state
                            if (state != null && state.videoId == video.id && state.duration > 0) {
                                val fraction = (state.timeOffset.toFloat() / state.duration).coerceIn(0f, 1f)
                                Box(Modifier.padding(top = 8.dp).size(192.dp, 3.dp).background(ProgressTrack)) {
                                    Box(Modifier.fillMaxWidth(fraction).height(3.dp).background(Color.White))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
