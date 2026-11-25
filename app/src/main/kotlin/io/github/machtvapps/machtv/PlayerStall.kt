package io.github.machtvapps.machtv

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import io.github.machtvapps.machtv.core.coreJson
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

const val LOADING_CHIP_DELAY_MS = 5_000L
const val SWARM_POLL_MS = 1_000L
private const val SWARM_TIMEOUT_MS = 2_000
const val UNPLAYABLE_MESSAGE = "This stream can't be played."
private const val NO_DATA_MESSAGE = "This stream isn't sending data."

data class SwarmStats(val seeders: Int, val peers: Int, val bytesPerSecond: Long)

fun parseSwarmStats(json: String): SwarmStats? = runCatching {
    val stats = coreJson.parseToJsonElement(json).jsonObject
    SwarmStats(stats.getValue("seeds").jsonPrimitive.int, stats.getValue("peers").jsonPrimitive.int, stats.getValue("downloadSpeed").jsonPrimitive.double.toLong())
}.getOrNull()

suspend fun fetchSwarmStats(engine: String, infoHash: String): SwarmStats? = withContext(Dispatchers.IO) {
    runCatching {
        val connection = URL("$engine/$infoHash/stats.json").openConnection() as HttpURLConnection
        connection.connectTimeout = SWARM_TIMEOUT_MS
        connection.readTimeout = SWARM_TIMEOUT_MS
        try {
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                parseSwarmStats(connection.inputStream.bufferedReader().use { it.readText() })
            } else {
                null
            }
        } finally {
            connection.disconnect()
        }
    }.getOrNull()
}

fun speedText(bytesPerSecond: Long): String = when {
    bytesPerSecond < 1_000 -> "$bytesPerSecond B/s"
    bytesPerSecond < 1_000_000 -> "${bytesPerSecond / 1_000} KB/s"
    else -> "${bytesPerSecond / 1_000_000}.${bytesPerSecond % 1_000_000 / 100_000} MB/s"
}

fun swarmText(stats: SwarmStats): String {
    val peers = if (stats.peers == 1) "1 peer" else "${stats.peers} peers"
    val seeders = if (stats.seeders == 1) "1 seeder" else "${stats.seeders} seeders"
    return "$seeders · $peers · ${speedText(stats.bytesPerSecond)}"
}

fun loadingText(stats: SwarmStats?): String = if (stats == null) "Loading" else "Loading · ${swarmText(stats)}"

fun failureMessage(errorCode: Int): String =
    if (errorCode in 2000..2999) NO_DATA_MESSAGE else UNPLAYABLE_MESSAGE

@Composable
fun LoadingChip(text: String, modifier: Modifier = Modifier) {
    BasicText(
        text,
        style = MachText.small,
        modifier = modifier
            .metalPanel(corner = null)
            .padding(vertical = 10.dp, horizontal = 20.dp),
    )
}

@Composable
fun FailurePanel(message: String, pillFocus: FocusRequester, onChooseStream: () -> Unit, modifier: Modifier = Modifier) {
    MetalPanel(modifier) {
        BasicText(message, style = MachText.body)
        PillButton("Choose Stream", onChooseStream, Modifier.padding(top = 16.dp).focusRequester(pillFocus))
    }
}
