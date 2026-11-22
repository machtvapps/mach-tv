package io.github.terminalis.machtv

import io.github.terminalis.machtv.core.LastUsedStream
import io.github.terminalis.machtv.core.MetaVideo
import io.github.terminalis.machtv.core.StreamList
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

sealed interface StreamPick {
    data class Found(val list: StreamList, val stream: JsonObject) : StreamPick
    data object Waiting : StreamPick
    data object Missing : StreamPick
}

fun isPlayable(stream: JsonObject): Boolean {
    if ((stream["infoHash"] as? JsonPrimitive)?.contentOrNull != null) return true
    val url = (stream["url"] as? JsonPrimitive)?.contentOrNull ?: return false
    return url.startsWith("http://") || url.startsWith("https://")
}

private fun StreamList.readyStreams(): List<JsonObject> {
    val loaded = content?.takeIf { it.type == "Ready" }?.content as? JsonArray ?: return emptyList()
    return loaded.mapNotNull { it as? JsonObject }
}

fun pickStream(lists: List<StreamList>, lastUsed: LastUsedStream? = null): StreamPick {
    val used = lastUsed?.content
    if (used?.type == "Loading") return StreamPick.Waiting
    val usedStream = used?.takeIf { it.type == "Ready" }?.content as? JsonObject
    val usedList = lists.firstOrNull { it.request.base == lastUsed?.request?.base }
    if (usedStream != null && usedList != null && isPlayable(usedStream)) return StreamPick.Found(usedList, usedStream)
    for (list in lists) {
        val type = list.content?.type
        if (type == null || type == "Loading") return StreamPick.Waiting
        val stream = list.readyStreams().firstOrNull(::isPlayable)
        if (stream != null) return StreamPick.Found(list, stream)
    }
    return StreamPick.Missing
}

fun playableStreams(lists: List<StreamList>): List<Pair<StreamList, JsonObject>> =
    lists.flatMap { list -> list.readyStreams().filter(::isPlayable).map { list to it } }

fun seriesVideo(videos: List<MetaVideo>, lastVideoId: String?): MetaVideo? =
    videos.firstOrNull { it.id == lastVideoId }
        ?: videos.filter { (it.season ?: 0) > 0 }.minWithOrNull(compareBy<MetaVideo>({ it.season }, { it.episode }))

fun streamText(stream: JsonObject, key: String): String =
    (stream[key] as? JsonPrimitive)?.contentOrNull.orEmpty().replace('\n', ' ').trim()
