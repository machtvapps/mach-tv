package io.github.terminalis.machtv

import io.github.terminalis.machtv.core.Action
import io.github.terminalis.machtv.core.Core
import io.github.terminalis.machtv.core.Field
import io.github.terminalis.machtv.core.MetaDetailsState
import io.github.terminalis.machtv.core.MetaVideo
import io.github.terminalis.machtv.core.StreamList
import io.github.terminalis.machtv.core.meta
import io.github.terminalis.machtv.core.metaRequestBase
import io.github.terminalis.machtv.core.resourcePath
import io.github.terminalis.machtv.core.resourceRequest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject

private const val LOAD_TIMEOUT_MS = 30_000L

data class Playback(
    val type: String,
    val metaId: String,
    val videoId: String,
    val name: String,
    val title: String,
    val stream: JsonObject,
    val streamRequest: JsonObject,
    val metaRequest: JsonObject?,
)

class PlayFailure(message: String) : Exception(message)

fun episodeTitle(name: String, video: MetaVideo): String = "$name · S${video.season} E${video.episode}"

private fun MetaDetailsState.metaSettled(): Boolean = meta() != null || metaItems.all { it.content?.type == "Err" }

suspend fun awaitDetails(
    core: Core,
    type: String,
    metaId: String,
    streamId: String?,
    ready: (MetaDetailsState) -> Boolean,
): MetaDetailsState {
    val current = core.metaDetails.value
    if (current?.selected?.metaPath?.id != metaId || current?.selected?.streamPath?.id != streamId) {
        core.dispatch(Action.LoadMetaDetails(type, metaId, streamId), Field.META_DETAILS)
    }
    return withTimeoutOrNull(LOAD_TIMEOUT_MS) {
        core.metaDetails.filterNotNull().first {
            it.selected?.metaPath?.id == metaId && it.selected?.streamPath?.id == streamId && ready(it)
        }
    } ?: throw PlayFailure("Timed out loading this title")
}

suspend fun resolvePlayback(core: Core, type: String, metaId: String): Playback {
    val videoId = if (type == "series") {
        val details = awaitDetails(core, type, metaId, null) { it.metaSettled() }
        val meta = details.meta() ?: throw PlayFailure("No details for this title")
        seriesVideo(meta.videos, details.libraryItem?.state?.videoId)?.id ?: throw PlayFailure("No episodes")
    } else {
        metaId
    }
    return resolveVideo(core, type, metaId, videoId)
}

suspend fun resolveVideo(core: Core, type: String, metaId: String, videoId: String): Playback {
    val details = awaitDetails(core, type, metaId, videoId) { it.metaSettled() && pickStream(it.streams, it.lastUsedStream) != StreamPick.Waiting }
    val pick = pickStream(details.streams, details.lastUsedStream) as? StreamPick.Found ?: throw PlayFailure("No playable streams")
    return playbackFor(details, type, metaId, videoId, pick.list, pick.stream)
}

fun playbackFor(
    details: MetaDetailsState,
    type: String,
    metaId: String,
    videoId: String,
    list: StreamList,
    stream: JsonObject,
): Playback {
    val meta = details.meta()
    val name = meta?.name ?: metaId
    val episode = meta?.videos?.firstOrNull { it.id == videoId && it.season != null }
    return Playback(
        type = type,
        metaId = metaId,
        videoId = videoId,
        name = name,
        title = episode?.let { episodeTitle(name, it) } ?: name,
        stream = stream,
        streamRequest = resourceRequest(list.request.base, resourcePath("stream", type, videoId)),
        metaRequest = details.metaRequestBase()?.let { resourceRequest(it, resourcePath("meta", type, metaId)) },
    )
}
