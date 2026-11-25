package io.github.machtvapps.machtv

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

const val LATEST_RELEASE_URL = "https://api.github.com/repos/machtvapps/mach-tv/releases/latest"
private const val APK_ASSET_NAME = "mach-tv-universal-release.apk"
private const val APK_TYPE = "application/vnd.android.package-archive"

data class Release(val version: String, val notes: String, val apkUrl: String)

fun parseRelease(release: JsonObject): Release {
    val apk = release.getValue("assets").jsonArray.map { it.jsonObject }
        .first { it["name"]?.jsonPrimitive?.contentOrNull == APK_ASSET_NAME }
    return Release(
        version = release.getValue("tag_name").jsonPrimitive.content,
        notes = (release["body"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty(),
        apkUrl = apk.getValue("browser_download_url").jsonPrimitive.content,
    )
}

fun isNewerVersion(latest: String, installed: String): Boolean {
    val a = latest.split(".").map { it.toInt() }
    val b = installed.split(".").map { it.toInt() }
    for (i in 0 until minOf(a.size, b.size)) {
        if (a[i] != b[i]) return a[i] > b[i]
    }
    return a.size > b.size
}

suspend fun fetchRelease(url: String): Release = withContext(Dispatchers.IO) {
    get(url, "application/vnd.github+json") { parseRelease(Json.parseToJsonElement(it.bufferedReader().readText()).jsonObject) }
}

suspend fun downloadApk(release: Release, cacheDir: File): File = withContext(Dispatchers.IO) {
    val file = File(cacheDir, "updates/$APK_ASSET_NAME")
    file.parentFile!!.mkdirs()
    get(release.apkUrl, "application/octet-stream") { input -> file.outputStream().use { input.copyTo(it) } }
    file
}

fun installIntent(context: Context, apk: File): Intent =
    Intent(Intent.ACTION_VIEW)
        .setDataAndType(FileProvider.getUriForFile(context, "${context.packageName}.files", apk), APK_TYPE)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)

private fun <T> get(url: String, accept: String, read: (InputStream) -> T): T {
    val connection = URL(url).openConnection() as HttpURLConnection
    try {
        connection.setRequestProperty("Accept", accept)
        connection.setRequestProperty("User-Agent", "mach-tv-updater")
        if (connection.responseCode != HttpURLConnection.HTTP_OK) {
            throw IOException("GET $url failed with status ${connection.responseCode}")
        }
        return connection.inputStream.use(read)
    } finally {
        connection.disconnect()
    }
}
