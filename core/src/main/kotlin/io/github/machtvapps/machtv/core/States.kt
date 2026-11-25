package io.github.machtvapps.machtv.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.decodeFromJsonElement

val coreJson = Json { ignoreUnknownKeys = true }

@Serializable
data class CtxState(
    val profile: ProfileState,
    val notifications: NotificationsState = NotificationsState(),
    val watched: List<String> = emptyList(),
    val library: List<LibraryEntry> = emptyList(),
)

@Serializable
data class NotificationsState(
    val items: Map<String, Map<String, JsonElement>> = emptyMap(),
    val lastUpdated: String? = null,
)

@Serializable
data class ProfileState(val addons: List<AddonDescriptor>, val settings: ProfileSettings = ProfileSettings())

@Serializable
data class ProfileSettings(
    val audioLanguage: String? = null,
    val subtitlesLanguage: String? = null,
    val subtitlesSize: Int = 100,
    val subtitlesTextColor: String = "#FFFFFFFF",
    val subtitlesBackgroundColor: String = "#00000000",
)

@Serializable
data class AddonDescriptor(val manifest: AddonManifest, val transportUrl: String, val flags: AddonFlags = AddonFlags())

@Serializable
data class AddonFlags(val official: Boolean = false, val protected: Boolean = false)

@Serializable
data class AddonManifest(
    val id: String,
    val name: String,
    val description: String? = null,
    val catalogs: List<ManifestCatalog> = emptyList(),
    val behaviorHints: ManifestHints = ManifestHints(),
)

@Serializable
data class ManifestHints(val configurable: Boolean = false)

@Serializable
data class ManifestCatalog(val type: String, val id: String, val name: String? = null)

@Serializable
data class BoardState(val selected: BoardSelected? = null, val catalogs: List<List<CatalogPage>> = emptyList())

@Serializable
data class BoardSelected(val type: String? = null)

@Serializable
internal data class BoardDelta(val version: Long, val selected: BoardSelected? = null, val count: Int, val rows: List<BoardDeltaRow> = emptyList())

@Serializable
internal data class BoardDeltaRow(val index: Int, val catalog: List<CatalogPage>)

internal fun BoardState?.patched(delta: BoardDelta): BoardState {
    val catalogs = this?.catalogs.orEmpty()
    val next = if (catalogs.size == delta.count) catalogs.toMutableList() else MutableList(delta.count) { emptyList() }
    delta.rows.forEach { next[it.index] = it.catalog }
    return BoardState(delta.selected, next)
}

@Serializable
data class CatalogPage(val request: ResourceRequest, val content: Loadable? = null)

@Serializable
data class ResourceRequest(val base: String, val path: ResourcePath)

@Serializable
data class ResourcePath(val resource: String, val type: String, val id: String, val extra: List<ExtraValue> = emptyList())

@Serializable(with = ExtraValueSerializer::class)
data class ExtraValue(val name: String, val value: String)

object ExtraValueSerializer : KSerializer<ExtraValue> {
    private val pair = ListSerializer(String.serializer())
    override val descriptor: SerialDescriptor = pair.descriptor
    override fun serialize(encoder: Encoder, value: ExtraValue) = pair.serialize(encoder, listOf(value.name, value.value))
    override fun deserialize(decoder: Decoder): ExtraValue = pair.deserialize(decoder).let { ExtraValue(it[0], it[1]) }
}

@Serializable
data class Loadable(val type: String, val content: JsonElement? = null)

@Serializable
data class AddonDetailsState(val remoteAddon: RemoteAddon? = null)

@Serializable
data class RemoteAddon(@SerialName("transport_url") val transportUrl: String? = null, val content: Loadable)

@Serializable
data class BackupImport(val restored: Int, val missingAddons: List<String>)

data class BackupRestore(val titles: Int, val addons: Int, val failedAddons: List<String>)

@Serializable
data class MetaDetailsState(
    val selected: MetaDetailsSelected? = null,
    val metaItems: List<MetaItemPage> = emptyList(),
    val streams: List<StreamList> = emptyList(),
    val libraryItem: LibraryItem? = null,
    val watchedVideos: List<String> = emptyList(),
    val lastUsedStream: LastUsedStream? = null,
)

@Serializable
data class LastUsedStream(val request: ResourceRequest, val content: Loadable? = null)

@Serializable
data class MetaDetailsSelected(val metaPath: ResourcePath, val streamPath: ResourcePath? = null)

@Serializable
data class MetaItemPage(val request: ResourceRequest, val content: Loadable? = null)

@Serializable
data class StreamList(val request: ResourceRequest, val content: Loadable? = null)

@Serializable
data class PlayerState(
    val stream: Loadable? = null,
    val libraryItem: LibraryItem? = null,
    val nextVideo: MetaVideo? = null,
    val nextStreams: StreamList? = null,
    val nextStream: JsonObject? = null,
    val subtitles: List<AddonSubtitles> = emptyList(),
)

@Serializable
data class AddonSubtitles(val request: ResourceRequest, val content: Loadable? = null)

@Serializable
data class Subtitle(val id: String, val lang: String, val url: String)

@Serializable
data class MetaPreview(
    val id: String,
    val type: String,
    val name: String,
    val poster: String? = null,
    val background: String? = null,
    val logo: String? = null,
    val description: String? = null,
    val releaseInfo: String? = null,
    val runtime: String? = null,
    val links: List<MetaLink> = emptyList(),
)

@Serializable
data class MetaLink(val name: String, val category: String, val url: String? = null)

@Serializable
data class MetaItem(
    val id: String,
    val type: String,
    val name: String,
    val poster: String? = null,
    val background: String? = null,
    val logo: String? = null,
    val description: String? = null,
    val releaseInfo: String? = null,
    val runtime: String? = null,
    val links: List<MetaLink> = emptyList(),
    val trailerStreams: List<TrailerStream> = emptyList(),
    val videos: List<MetaVideo> = emptyList(),
)

@Serializable
data class TrailerStream(val ytId: String? = null)

@Serializable
data class MetaVideo(
    val id: String,
    val title: String = "",
    val released: String? = null,
    val overview: String? = null,
    val thumbnail: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
)

@Serializable
data class ContinueWatchingState(val items: List<ContinueWatchingItem>)

@Serializable
data class ContinueWatchingItem(
    @SerialName("_id") val id: String,
    val type: String,
    val name: String,
    val poster: String? = null,
    val state: LibraryItemState,
)

@Serializable
data class LibraryItem(
    @SerialName("_id") val id: String,
    val removed: Boolean = false,
    val temp: Boolean = false,
    val state: LibraryItemState,
)

@Serializable
data class LibraryItemState(
    val timeOffset: Long = 0,
    val duration: Long = 0,
    @SerialName("video_id") val videoId: String? = null,
    val timesWatched: Int = 0,
    val flaggedWatched: Int = 0,
)

fun Loadable.readyMetas(): List<MetaPreview> {
    val items = content
    return if (type == "Ready" && items != null) coreJson.decodeFromJsonElement(items) else emptyList()
}

fun Loadable.readyMeta(): MetaItem? {
    val item = content
    return if (type == "Ready" && item != null) coreJson.decodeFromJsonElement(item) else null
}

fun MetaDetailsState.meta(): MetaItem? = metaItems.firstNotNullOfOrNull { it.content?.readyMeta() }

fun AddonSubtitles.readySubtitles(): List<Subtitle> {
    val items = content?.takeIf { it.type == "Ready" }?.content
    return if (items != null) coreJson.decodeFromJsonElement(items) else emptyList()
}

fun MetaDetailsState.metaJson(): JsonObject? =
    metaItems.firstOrNull { it.content?.type == "Ready" }?.content?.content as? JsonObject

fun MetaDetailsState.metaRequestBase(): String? = metaItems.firstOrNull { it.content?.type == "Ready" }?.request?.base

@Serializable
data class CatalogWithFiltersState(
    val selected: CatalogSelected? = null,
    val selectable: CatalogSelectable = CatalogSelectable(),
    val catalog: List<CatalogPage> = emptyList(),
)

@Serializable
data class CatalogSelected(val request: ResourceRequest)

@Serializable
data class CatalogSelectable(val nextPage: JsonElement? = null)

@Serializable
data class LocalSearchState(val searchResults: List<Searchable> = emptyList(), val ready: Boolean = false)

@Serializable
data class Searchable(val id: String, val name: String = "", val type: String, val releaseInfo: String? = null)

@Serializable
data class LibraryState(
    val selected: LibrarySelected? = null,
    val selectable: LibrarySelectable = LibrarySelectable(),
    val catalog: List<LibraryEntry> = emptyList(),
)

@Serializable
data class LibrarySelected(val request: LibraryRequest)

@Serializable
data class LibraryRequest(val type: String? = null, val sort: String = "lastwatched")

@Serializable
data class LibrarySelectable(@SerialName("next_page") val nextPage: JsonElement? = null)

@Serializable
data class LibraryEntry(
    @SerialName("_id") val id: String,
    val type: String,
    val name: String,
    val poster: String? = null,
)

fun ResourceRequest.toJson(): JsonObject = buildJsonObject {
    put("base", base)
    putJsonObject("path") {
        put("resource", path.resource)
        put("type", path.type)
        put("id", path.id)
        putJsonArray("extra") {
            path.extra.forEach { extra ->
                addJsonArray {
                    add(extra.name)
                    add(extra.value)
                }
            }
        }
    }
}
