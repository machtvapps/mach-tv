package io.github.terminalis.machtv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.terminalis.machtv.core.Action
import io.github.terminalis.machtv.core.AddonDescriptor
import io.github.terminalis.machtv.core.Core
import io.github.terminalis.machtv.core.Field

@Composable
fun StreamsPanel(
    core: Core,
    type: String,
    metaId: String,
    videoId: String,
    addons: List<AddonDescriptor>,
    onPick: (Playback) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    Box(Modifier.fillMaxSize().background(Color(0xCC000000)).padding(48.dp)) {
        StreamChoices(core, type, metaId, videoId, addons, "Choose Stream", onPick, Modifier.fillMaxSize(), focusFirst = true)
    }
}

@Composable
fun StreamChoices(
    core: Core,
    type: String,
    metaId: String,
    videoId: String,
    addons: List<AddonDescriptor>,
    title: String,
    onPick: (Playback) -> Unit,
    modifier: Modifier = Modifier,
    focusFirst: Boolean = false,
    first: FocusRequester = remember { FocusRequester() },
) {
    val details by core.metaDetails.collectAsState()
    LaunchedEffect(videoId) {
        if (core.metaDetails.value?.selected?.streamPath?.id != videoId) {
            core.dispatch(Action.LoadMetaDetails(type, metaId, videoId), Field.META_DETAILS)
        }
    }
    val state = details?.takeIf { it.selected?.metaPath?.id == metaId && it.selected?.streamPath?.id == videoId }
    val choices = state?.let { playableStreams(it.streams) }.orEmpty()
    val loading = state == null || pickStream(state.streams) == StreamPick.Waiting
    LaunchedEffect(choices.isNotEmpty()) {
        if (focusFirst && choices.isNotEmpty()) {
            withFrameNanos { }
            first.requestFocus()
        }
    }
    MetalPanel(modifier) {
        BasicText(title, style = MachText.panelTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        when {
            choices.isNotEmpty() -> LazyColumn(Modifier.padding(top = 16.dp)) {
                itemsIndexed(choices) { index, (list, stream) ->
                    FocusRow(
                        onClick = { state?.let { onPick(playbackFor(it, type, metaId, videoId, list, stream)) } },
                        modifier = if (index == 0) Modifier.focusRequester(first) else Modifier,
                    ) {
                        Column {
                            val addon = addons.firstOrNull { it.transportUrl == list.request.base }?.manifest?.name ?: list.request.base
                            BasicText("$addon · ${streamText(stream, "name")}", style = MachText.rowTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            BasicText(streamText(stream, "description"), style = MachText.body, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
            loading -> BasicText("Loading streams…", style = MachText.body, modifier = Modifier.padding(top = 16.dp))
            else -> BasicText("No playable streams", style = MachText.body, modifier = Modifier.padding(top = 16.dp))
        }
    }
}
