package io.github.machtvapps.machtv.core

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonArray
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

private const val DEVICE = "machtv"

fun resourcePath(resource: String, type: String, id: String): JsonObject = buildJsonObject {
    put("resource", resource)
    put("type", type)
    put("id", id)
    putJsonArray("extra") {}
}

fun resourceRequest(base: String, path: JsonObject): JsonObject = buildJsonObject {
    put("base", base)
    put("path", path)
}

private fun ctxAction(name: String, args: JsonElement) = buildJsonObject {
    put("action", "Ctx")
    putJsonObject("args") {
        put("action", name)
        put("args", args)
    }
}

private fun playerAction(name: String, args: JsonElement? = null) = buildJsonObject {
    put("action", "Player")
    putJsonObject("args") {
        put("action", name)
        if (args != null) put("args", args)
    }
}

private fun playerTime(time: Long, duration: Long) = buildJsonObject {
    put("time", time)
    put("duration", duration)
    put("device", DEVICE)
}

sealed class Action {
    internal abstract fun toJson(): JsonObject

    data class LoadCatalogsWithExtra(val type: String?, val extra: List<ExtraValue> = emptyList()) : Action() {
        override fun toJson() = buildJsonObject {
            put("action", "Load")
            putJsonObject("args") {
                put("model", "CatalogsWithExtra")
                putJsonObject("args") {
                    put("type", type)
                    putJsonArray("extra") {
                        extra.forEach {
                            addJsonArray {
                                add(it.name)
                                add(it.value)
                            }
                        }
                    }
                }
            }
        }
    }

    data class LoadCatalogRange(val start: Int, val end: Int) : Action() {
        override fun toJson() = buildJsonObject {
            put("action", "CatalogsWithExtra")
            putJsonObject("args") {
                put("action", "LoadRange")
                putJsonObject("args") {
                    put("start", start)
                    put("end", end)
                }
            }
        }
    }

    data class LoadCatalogWithFilters(val request: ResourceRequest) : Action() {
        override fun toJson() = buildJsonObject {
            put("action", "Load")
            putJsonObject("args") {
                put("model", "CatalogWithFilters")
                putJsonObject("args") { put("request", request.toJson()) }
            }
        }
    }

    data object CatalogNextPage : Action() {
        override fun toJson() = buildJsonObject {
            put("action", "CatalogWithFilters")
            putJsonObject("args") { put("action", "LoadNextPage") }
        }
    }

    data object LoadLocalSearch : Action() {
        override fun toJson() = buildJsonObject {
            put("action", "Load")
            putJsonObject("args") { put("model", "LocalSearch") }
        }
    }

    data class Search(val query: String, val maxResults: Int) : Action() {
        override fun toJson() = buildJsonObject {
            put("action", "Search")
            putJsonObject("args") {
                put("action", "Search")
                putJsonObject("args") {
                    put("searchQuery", query)
                    put("maxResults", maxResults)
                }
            }
        }
    }

    data class LoadLibrary(val type: String?, val sort: String) : Action() {
        override fun toJson() = buildJsonObject {
            put("action", "Load")
            putJsonObject("args") {
                put("model", "LibraryWithFilters")
                putJsonObject("args") {
                    putJsonObject("request") {
                        put("type", type)
                        put("sort", sort)
                        put("page", 1)
                    }
                }
            }
        }
    }

    data object LibraryNextPage : Action() {
        override fun toJson() = buildJsonObject {
            put("action", "LibraryWithFilters")
            putJsonObject("args") { put("action", "LoadNextPage") }
        }
    }

    data object PullNotifications : Action() {
        override fun toJson() = buildJsonObject {
            put("action", "Ctx")
            putJsonObject("args") { put("action", "PullNotifications") }
        }
    }

    data class LoadAddonDetails(val transportUrl: String) : Action() {
        override fun toJson() = buildJsonObject {
            put("action", "Load")
            putJsonObject("args") {
                put("model", "AddonDetails")
                putJsonObject("args") {
                    put("transportUrl", transportUrl)
                }
            }
        }
    }

    data class LoadMetaDetails(val type: String, val id: String, val streamId: String?) : Action() {
        override fun toJson() = buildJsonObject {
            put("action", "Load")
            putJsonObject("args") {
                put("model", "MetaDetails")
                putJsonObject("args") {
                    put("metaPath", resourcePath("meta", type, id))
                    put("streamPath", streamId?.let { resourcePath("stream", type, it) } ?: JsonNull)
                    put("guessStream", false)
                }
            }
        }
    }

    data class LoadPlayer(
        val stream: JsonObject,
        val streamRequest: JsonObject? = null,
        val metaRequest: JsonObject? = null,
        val subtitlesPath: JsonObject? = null,
    ) : Action() {
        override fun toJson() = buildJsonObject {
            put("action", "Load")
            putJsonObject("args") {
                put("model", "Player")
                putJsonObject("args") {
                    put("stream", stream)
                    put("streamRequest", streamRequest ?: JsonNull)
                    put("metaRequest", metaRequest ?: JsonNull)
                    put("subtitlesPath", subtitlesPath ?: JsonNull)
                }
            }
        }
    }

    data class InstallAddon(val descriptor: JsonObject) : Action() {
        override fun toJson() = ctxAction("InstallAddon", descriptor)
    }

    data class UninstallAddon(val descriptor: JsonObject) : Action() {
        override fun toJson() = ctxAction("UninstallAddon", descriptor)
    }

    data class AddToLibrary(val meta: JsonObject) : Action() {
        override fun toJson() = ctxAction("AddToLibrary", meta)
    }

    data class RemoveFromLibrary(val id: String) : Action() {
        override fun toJson() = ctxAction("RemoveFromLibrary", JsonPrimitive(id))
    }

    data class RewindLibraryItem(val id: String) : Action() {
        override fun toJson() = ctxAction("RewindLibraryItem", JsonPrimitive(id))
    }

    data class DismissNotificationItem(val id: String) : Action() {
        override fun toJson() = ctxAction("DismissNotificationItem", JsonPrimitive(id))
    }

    data class MarkWatched(val meta: JsonObject, val watched: Boolean) : Action() {
        override fun toJson() = ctxAction(
            "MetaItemMarkAsWatched",
            buildJsonObject {
                put("meta_item", meta)
                put("is_watched", watched)
            },
        )
    }

    data class MarkSeasonWatched(val season: Int, val watched: Boolean) : Action() {
        override fun toJson() = buildJsonObject {
            put("action", "MetaDetails")
            putJsonObject("args") {
                put("action", "MarkSeasonAsWatched")
                putJsonArray("args") {
                    add(season)
                    add(watched)
                }
            }
        }
    }

    data class MarkVideoWatched(val video: MetaVideo, val watched: Boolean) : Action() {
        override fun toJson() = buildJsonObject {
            put("action", "MetaDetails")
            putJsonObject("args") {
                put("action", "MarkVideoAsWatched")
                putJsonArray("args") {
                    addJsonObject {
                        put("id", video.id)
                        put("title", video.title)
                        put("released", video.released)
                        put("season", video.season)
                        put("episode", video.episode)
                    }
                    add(watched)
                }
            }
        }
    }

    data class UpdateSettings(val settings: JsonObject) : Action() {
        override fun toJson() = ctxAction("UpdateSettings", settings)
    }

    data object Unload : Action() {
        override fun toJson() = buildJsonObject { put("action", "Unload") }
    }

    data class PlayerTimeChanged(val time: Long, val duration: Long) : Action() {
        override fun toJson() = playerAction("TimeChanged", playerTime(time, duration))
    }

    data class PlayerSeek(val time: Long, val duration: Long) : Action() {
        override fun toJson() = playerAction("Seek", playerTime(time, duration))
    }

    data class PlayerPausedChanged(val paused: Boolean) : Action() {
        override fun toJson() = playerAction("PausedChanged", buildJsonObject { put("paused", paused) })
    }

    data object PlayerNextVideo : Action() {
        override fun toJson() = playerAction("NextVideo")
    }

    data object PlayerEnded : Action() {
        override fun toJson() = playerAction("Ended")
    }
}
