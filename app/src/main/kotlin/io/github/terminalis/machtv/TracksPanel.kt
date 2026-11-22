package io.github.terminalis.machtv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer

const val ADDON_SUBTITLE_ID = "addon-subtitle"

data class TrackChoice(val key: String, val label: String, val selected: Boolean, val select: () -> Unit)

private fun Format.isAddonSubtitle(): Boolean = id?.endsWith(ADDON_SUBTITLE_ID) == true

fun subtitleMimeType(url: String): String {
    val name = url.substringBefore('?').substringAfterLast('/').lowercase()
    return when {
        name.endsWith(".vtt") -> MimeTypes.TEXT_VTT
        name.endsWith(".ass") || name.endsWith(".ssa") -> MimeTypes.TEXT_SSA
        else -> MimeTypes.APPLICATION_SUBRIP
    }
}

fun trackLabel(format: Format): String = listOfNotNull(
    format.label ?: format.language?.takeIf { it != "und" },
    format.channelCount.takeIf { it > 0 }?.let { "$it ch" },
    format.sampleMimeType?.substringAfter('/'),
).joinToString(" · ").ifEmpty { "Track" }

fun exoTrackChoices(player: ExoPlayer, tracks: Tracks, type: Int): List<TrackChoice> =
    tracks.groups.withIndex().filter { it.value.type == type }.flatMap { (groupIndex, group) ->
        (0 until group.length)
            .filter { group.isTrackSupported(it) && !group.getTrackFormat(it).isAddonSubtitle() }
            .map { index ->
                val format = group.getTrackFormat(index)
                TrackChoice("$type:$groupIndex:$index", trackLabel(format), group.isTrackSelected(index)) {
                    val parameters = player.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(type, false)
                        .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, index))
                    if (type == C.TRACK_TYPE_AUDIO) {
                        parameters.setPreferredAudioMimeType(format.sampleMimeType).setPreferredAudioLanguage(format.language)
                    }
                    player.trackSelectionParameters = parameters.build()
                }
            }
    }

fun textSelected(tracks: Tracks): Boolean = tracks.groups.any { it.type == C.TRACK_TYPE_TEXT && it.isSelected }

fun addonSubtitleSelected(tracks: Tracks): Boolean = tracks.groups.any { group ->
    group.type == C.TRACK_TYPE_TEXT &&
        (0 until group.length).any { group.isTrackSelected(it) && group.getTrackFormat(it).isAddonSubtitle() }
}

@Composable
fun TracksPanel(title: String, choices: List<TrackChoice>, onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    val focusIndex = choices.indexOfFirst { it.selected }.coerceAtLeast(0)
    val focusKey = choices.getOrNull(focusIndex)?.key
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = focusIndex)
    BackHandler(onBack = onClose)
    LaunchedEffect(focusKey != null) {
        if (focusKey != null) {
            withFrameNanos { }
            focus.requestFocus()
        }
    }
    Box(Modifier.fillMaxSize()) {
        MetalPanel(Modifier.align(Alignment.CenterEnd).padding(32.dp).width(560.dp).fillMaxHeight()) {
            BasicText(title, style = MachText.panelTitle)
            LazyColumn(state = listState, modifier = Modifier.padding(top = 16.dp)) {
                items(choices, key = { it.key }) { choice ->
                    ChoiceRow(choice, if (choice.key == focusKey) Modifier.focusRequester(focus) else Modifier)
                }
            }
        }
    }
}

@Composable
private fun ChoiceRow(choice: TrackChoice, modifier: Modifier) {
    FocusRow(onClick = choice.select, modifier = modifier) {
        BasicText(choice.label, style = MachText.rowTitle, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (choice.selected) BasicText("Selected", style = MachText.small, modifier = Modifier.padding(start = 16.dp))
    }
}
