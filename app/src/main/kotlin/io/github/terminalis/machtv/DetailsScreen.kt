package io.github.terminalis.machtv

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.transformations
import coil3.transform.Transformation
import io.github.terminalis.machtv.core.Action
import io.github.terminalis.machtv.core.Core
import io.github.terminalis.machtv.core.Field
import io.github.terminalis.machtv.core.MetaVideo
import io.github.terminalis.machtv.core.meta
import io.github.terminalis.machtv.core.metaJson
import kotlinx.coroutines.launch


private sealed interface DetailsPanel {
    data class Trailer(val ytId: String) : DetailsPanel
}

private object BackdropScrim : Transformation() {
    override val cacheKey = "backdrop-scrim"

    override suspend fun transform(input: Bitmap, size: coil3.size.Size): Bitmap {
        val output = input.copy(Bitmap.Config.ARGB_8888, true)
        val width = output.width.toFloat()
        val height = output.height.toFloat()
        val canvas = Canvas(output)
        canvas.drawRect(0f, 0f, width, height, Paint().apply {
            shader = LinearGradient(0f, 0f, width * 0.7f, 0f, 0xE6000000.toInt(), 0x00000000, Shader.TileMode.CLAMP)
        })
        canvas.drawRect(0f, 0f, width, height, Paint().apply {
            shader = LinearGradient(0f, height * 0.5f, 0f, height, 0x00000000, 0xCC000000.toInt(), Shader.TileMode.CLAMP)
        })
        return output
    }
}

@Composable
fun DetailsScreen(core: Core, type: String, id: String, onPlay: (Playback) -> Unit) {
    val details by core.metaDetails.collectAsState()
    val ctx by core.ctx.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var panel by remember { mutableStateOf<DetailsPanel?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var episode by remember { mutableStateOf<MetaVideo?>(null) }
    var returnTo by remember { mutableStateOf<String?>(null) }
    val firstPill = remember { FocusRequester() }
    val seasonFocus = remember { FocusRequester() }
    val streamFocus = remember { FocusRequester() }

    LaunchedEffect(type, id) {
        core.dispatch(Action.LoadMetaDetails(type, id, if (type == "series") null else id), Field.META_DETAILS)
    }
    val state = details?.takeIf { it.selected?.metaPath?.id == id }
    val meta = state?.meta()
    val metaJson = state?.metaJson()
    val library = state?.libraryItem
    val inLibrary = library?.removed == false
    val watched = library != null && (library.state.timesWatched > 0 || library.state.flaggedWatched > 0)
    val resumable = library != null && library.state.timeOffset > 0
    val trailer = meta?.trailerStreams?.firstNotNullOfOrNull { it.ytId }
    val streamsListed = type != "series" && state?.selected?.streamPath?.id == id && playableStreams(state.streams).isNotEmpty()

    fun play(videoId: String?) {
        scope.launch {
            message = null
            runCatching { if (videoId == null) resolvePlayback(core, type, id) else resolveVideo(core, type, id, videoId) }
                .onSuccess(onPlay)
                .onFailure { message = it.message }
        }
    }

    fun trackSeries() {
        if (library != null || metaJson == null) return
        core.dispatch(Action.MarkWatched(metaJson, true))
        core.dispatch(Action.MarkWatched(metaJson, false))
    }

    BackHandler(enabled = episode != null && panel == null) {
        returnTo = episode?.id
        episode = null
    }
    LaunchedEffect(meta != null, panel) {
        if (meta != null && panel == null && episode == null) {
            withFrameNanos { }
            firstPill.requestFocus()
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        meta?.background?.let { url ->
            val request = remember(url) { ImageRequest.Builder(context).data(url).transformations(BackdropScrim).build() }
            AsyncImage(model = request, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        if (meta != null && panel == null) {
            Row(Modifier.fillMaxSize().padding(start = 64.dp, end = 48.dp, top = 48.dp, bottom = 48.dp)) {
                Column(Modifier.weight(1f).fillMaxHeight().padding(end = 32.dp), verticalArrangement = Arrangement.Center) {
                    if (meta.logo != null) {
                        AsyncImage(
                            model = meta.logo,
                            contentDescription = meta.name,
                            contentScale = ContentScale.Fit,
                            alignment = Alignment.CenterStart,
                            modifier = Modifier.height(96.dp).widthIn(max = 480.dp),
                        )
                    } else {
                        BasicText(meta.name, style = MachText.heroTitle.copy(shadow = heroTitleShadow()), maxLines = 2)
                    }
                    val nextEpisode = if (type == "series") seriesVideo(meta.videos, library?.state?.videoId) else null
                    val playLabel = listOfNotNull(if (resumable) "Resume" else "Play", nextEpisode?.let { episodeTag(it) }).joinToString(" ")
                    Row(Modifier.padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                        val facts = cardFacts(meta.links, meta.releaseInfo, meta.runtime)
                        if (facts.isNotEmpty()) BasicText(facts, style = MachText.rowTitle.copy(shadow = heroBodyShadow()), modifier = Modifier.padding(end = 16.dp))
                        imdbRating(meta.links)?.let { RatingBadge(it) }
                    }
                    meta.description?.let {
                        BasicText(
                            it,
                            style = MachText.heroBody.copy(shadow = heroBodyShadow()),
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 16.dp),
                        )
                    }
                    Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        CreditLine("Director", meta.links.filter { it.category == "Directors" }.map { it.name }.take(2))
                        CreditLine("Cast", meta.links.filter { it.category == "Cast" }.map { it.name }.take(4))
                    }
                    Row(
                        Modifier.padding(top = 24.dp).focusProperties { if (streamsListed) up = streamFocus },
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        PillButton(playLabel, { play(null) }, Modifier.focusRequester(firstPill), compact = true)
                        trailer?.let { ytId ->
                            PillButton(
                                "Trailer",
                                {
                                    returnTo = null
                                    panel = DetailsPanel.Trailer(ytId)
                                },
                                compact = true,
                            )
                        }
                        PillButton(
                            if (inLibrary) BOOKMARK_FILL_GLYPH else BOOKMARK_GLYPH,
                            { metaJson?.let { core.dispatch(if (inLibrary) Action.RemoveFromLibrary(id) else Action.AddToLibrary(it)) } },
                            compact = true,
                            selected = inLibrary,
                            glyph = true,
                        )
                        PillButton(
                            if (watched) EYE_FILL_GLYPH else EYE_GLYPH,
                            {
                                metaJson?.let { core.dispatch(Action.MarkWatched(it, !watched)) }
                                if (type == "series") seasonsOf(meta.videos).forEach { core.dispatch(Action.MarkSeasonWatched(it, !watched)) }
                            },
                            modifier = Modifier.focusProperties {
                                if (type == "series" && episode == null && seasonsOf(meta.videos).size > 1) right = seasonFocus
                                if (streamsListed) right = streamFocus
                            },
                            compact = true,
                            selected = watched,
                            glyph = true,
                        )
                    }
                    message?.let { BasicText(it, style = MachText.body, modifier = Modifier.padding(top = 16.dp)) }
                }
                val addons = ctx?.profile?.addons.orEmpty()
                val panelSize = Modifier.width(440.dp).fillMaxHeight()
                val open = episode
                when {
                    type != "series" -> StreamChoices(core, type, id, id, addons, "Streams", onPlay, panelSize, first = streamFocus)
                    open != null -> StreamChoices(core, type, id, open.id, addons, "${episodeTag(open)} · ${open.title}", onPlay, panelSize, focusFirst = true)
                    else -> DetailsEpisodes(
                        meta = meta,
                        library = library,
                        watched = state?.watchedVideos.orEmpty(),
                        focusVideo = returnTo,
                        seasonFocus = seasonFocus,
                        onPick = { episode = it },
                        onMarkSeason = { season, mark ->
                            trackSeries()
                            core.dispatch(Action.MarkSeasonWatched(season, mark))
                        },
                        onMarkVideo = { video, mark ->
                            trackSeries()
                            core.dispatch(Action.MarkVideoWatched(video, mark))
                        },
                        modifier = panelSize,
                    )
                }
            }
        }
        when (val open = panel) {
            is DetailsPanel.Trailer -> TrailerPlayer(open.ytId) { panel = null }
            null -> Unit
        }
    }
}

@Composable
private fun CreditLine(label: String, names: List<String>) {
    if (names.isEmpty()) return
    BasicText("$label: ${names.joinToString(", ")}", style = MachText.small.copy(shadow = heroBodyShadow()), maxLines = 1, overflow = TextOverflow.Ellipsis)
}
