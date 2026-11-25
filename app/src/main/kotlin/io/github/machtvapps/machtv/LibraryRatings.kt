package io.github.machtvapps.machtv

import io.github.machtvapps.machtv.core.AddonDescriptor
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

private const val CINEMETA_ID = "com.linvo.cinemeta"

fun cinemetaMetaUrl(addons: List<AddonDescriptor>, type: String, id: String): String? =
    addons.firstOrNull { it.manifest.id == CINEMETA_ID }
        ?.takeIf { id.startsWith("tt") }
        ?.let { it.transportUrl.removeSuffix("manifest.json") + "meta/$type/$id.json" }

data class MetaFacts(val rating: String?, val released: String?)

fun metaFacts(body: String): MetaFacts {
    val meta = (Json.parseToJsonElement(body) as? JsonObject)?.get("meta") as? JsonObject
    fun text(key: String) = (meta?.get(key) as? JsonPrimitive)?.contentOrNull?.ifBlank { null }
    return MetaFacts(text("imdbRating"), text("released"))
}

fun fetchFacts(url: String): MetaFacts? {
    val connection = URL(url).openConnection() as HttpURLConnection
    try {
        if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
        return metaFacts(connection.inputStream.use { it.readBytes().decodeToString() })
    } finally {
        connection.disconnect()
    }
}
