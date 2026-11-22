package io.github.terminalis.machtv

import androidx.compose.ui.input.key.Key

const val SEEK_MS = 10_000L
const val OVERLAY_IDLE_MS = 4_000L
private const val NEXT_EPISODE_SHARE = 0.95

enum class PlayerKey { Left, Right, Up, Down, Ok, Back, Other }

data class KeyAction(
    val showOverlay: Boolean = false,
    val seekMs: Long = 0,
    val togglePause: Boolean = false,
    val consumed: Boolean = true,
)

fun playerKey(key: Key): PlayerKey = when (key) {
    Key.DirectionLeft -> PlayerKey.Left
    Key.DirectionRight -> PlayerKey.Right
    Key.DirectionUp -> PlayerKey.Up
    Key.DirectionDown -> PlayerKey.Down
    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> PlayerKey.Ok
    Key.Back -> PlayerKey.Back
    else -> PlayerKey.Other
}

fun hiddenOverlayKey(key: PlayerKey): KeyAction = when (key) {
    PlayerKey.Back -> KeyAction(consumed = false)
    PlayerKey.Left -> KeyAction(showOverlay = true, seekMs = -SEEK_MS)
    PlayerKey.Right -> KeyAction(showOverlay = true, seekMs = SEEK_MS)
    PlayerKey.Ok -> KeyAction(showOverlay = true, togglePause = true)
    else -> KeyAction(showOverlay = true)
}

fun seekBarKey(key: PlayerKey): KeyAction = when (key) {
    PlayerKey.Left -> KeyAction(seekMs = -SEEK_MS)
    PlayerKey.Right -> KeyAction(seekMs = SEEK_MS)
    PlayerKey.Ok -> KeyAction(togglePause = true)
    else -> KeyAction(consumed = false)
}

fun nextEpisodeVisible(positionMs: Long, durationMs: Long, hasNext: Boolean): Boolean =
    hasNext && durationMs > 0 && positionMs >= durationMs * NEXT_EPISODE_SHARE

fun playerClock(ms: Long): String {
    val total = ms.coerceAtLeast(0) / 1000
    val hours = total / 3600
    val minutes = total % 3600 / 60
    val seconds = total % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}
