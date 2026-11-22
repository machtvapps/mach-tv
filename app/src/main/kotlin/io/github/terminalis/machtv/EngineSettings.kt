package io.github.terminalis.machtv

import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

private const val GIGABYTE = 1024L * 1024 * 1024
private const val LISTEN_PORTS = "42000-42010"
private const val DEFAULT_LISTEN_INTERFACES = "0.0.0.0:42000-42010,[::]:42000-42010"

enum class CacheSize(val bytes: Long, val label: String) {
    Two(2 * GIGABYTE, "2 GB"),
    Five(5 * GIGABYTE, "5 GB"),
    Ten(10 * GIGABYTE, "10 GB"),
    Unlimited(0, "No limit"),
}

enum class ProxyKind(val label: String) {
    Off("Off"),
    Socks5("SOCKS5"),
    Http("HTTP"),
}

data class EngineSettings(
    val cacheSize: Long,
    val dht: Boolean,
    val proxy: ProxyKind,
    val proxyHost: String,
    val proxyPort: Int,
    val proxyUsername: String,
    val proxyPassword: String,
    val outgoingInterfaces: String,
)

data class NetInterface(val name: String, val address: String)

private fun JsonObject.primitive(key: String) = this[key] as? JsonPrimitive

private fun JsonObject.text(key: String) = primitive(key)?.contentOrNull.orEmpty()

fun engineSettings(values: JsonObject): EngineSettings = EngineSettings(
    cacheSize = values.primitive("cacheSize")?.doubleOrNull?.toLong() ?: 0L,
    dht = values.primitive("btEnableDht")?.booleanOrNull ?: true,
    proxy = when (values.text("btProxyType").lowercase()) {
        "socks5", "socks5password" -> ProxyKind.Socks5
        "http", "httppassword" -> ProxyKind.Http
        else -> ProxyKind.Off
    },
    proxyHost = values.text("btProxyHost"),
    proxyPort = values.primitive("btProxyPort")?.intOrNull ?: 0,
    proxyUsername = values.text("btProxyUsername"),
    proxyPassword = values.text("btProxyPassword"),
    outgoingInterfaces = values.text("btOutgoingInterfaces"),
)

fun cacheLabel(bytes: Long): String = CacheSize.entries.firstOrNull { it.bytes == bytes }?.label ?: "${bytes / GIGABYTE} GB"

fun proxyType(kind: ProxyKind, username: String): String = when (kind) {
    ProxyKind.Off -> "none"
    ProxyKind.Socks5 -> if (username.isEmpty()) "socks5" else "socks5Password"
    ProxyKind.Http -> if (username.isEmpty()) "http" else "httpPassword"
}

fun interfaceFields(name: String?): JsonObject = buildJsonObject {
    put("btOutgoingInterfaces", name.orEmpty())
    put("btListenInterfaces", if (name == null) DEFAULT_LISTEN_INTERFACES else "$name:$LISTEN_PORTS")
}

fun networkInterfaces(): List<NetInterface> =
    NetworkInterface.getNetworkInterfaces().toList()
        .filter { it.isUp && !it.isLoopback }
        .mapNotNull { network ->
            network.inetAddresses.toList().filterIsInstance<Inet4Address>().firstOrNull()?.hostAddress?.let { NetInterface(network.name, it) }
        }

class EngineSettingsClient(private val base: String) {
    suspend fun read(): JsonObject = withContext(Dispatchers.IO) {
        val connection = URL("$base/settings").openConnection() as HttpURLConnection
        try {
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            Json.parseToJsonElement(body).jsonObject.getValue("values").jsonObject
        } finally {
            connection.disconnect()
        }
    }

    suspend fun write(fields: JsonObject) = withContext(Dispatchers.IO) {
        val connection = URL("$base/settings").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(fields.toString().toByteArray()) }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            check(Json.parseToJsonElement(body).jsonObject["success"]?.jsonPrimitive?.booleanOrNull == true) { body }
        } finally {
            connection.disconnect()
        }
    }
}
