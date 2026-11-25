package io.github.machtvapps.machtv

import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalViewConfiguration
import io.github.machtvapps.machtv.core.Action
import io.github.machtvapps.machtv.core.MetaVideo
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

val OkKeys = setOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER)

class HoldState {
    var held = false

    fun unlessHeld(onClick: () -> Unit): () -> Unit = { if (!held) onClick() }
}

@Composable
fun Modifier.holdPress(state: HoldState, onHold: (() -> Unit)?): Modifier {
    if (onHold == null) return this
    val holdMs = LocalViewConfiguration.current.longPressTimeoutMillis
    return onPreviewKeyEvent { event ->
        val key = event.nativeKeyEvent
        if (key.keyCode in OkKeys && key.action == KeyEvent.ACTION_DOWN) {
            if (key.repeatCount == 0) {
                state.held = false
            } else if (!state.held && key.eventTime - key.downTime >= holdMs) {
                state.held = true
                onHold()
            }
        }
        false
    }
}

fun posterMeta(id: String, type: String, name: String, poster: String?): JsonObject = buildJsonObject {
    put("id", id)
    put("type", type)
    put("name", name)
    put("poster", poster)
}

fun seasonWatched(videos: List<MetaVideo>, season: Int, watched: List<String>): Boolean {
    val episodes = videos.filter { it.season == season }
    return episodes.isNotEmpty() && episodes.all { it.id in watched }
}

@Composable
fun movieWatchedToggle(id: String, type: String, name: String, poster: String?): (() -> Unit)? {
    if (type != "movie") return null
    val core = (LocalContext.current.applicationContext as MachTvApp).core
    val watched = id in LocalWatched.current
    return { core.dispatch(Action.MarkWatched(posterMeta(id, type, name, poster), !watched)) }
}
