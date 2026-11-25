package io.github.machtvapps.machtv.core

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals

internal const val NETWORK_TIMEOUT_MS = 30_000L
internal const val TORRENTIO_URL = "https://torrentio.strem.fun/manifest.json"
internal const val TORRENTIO_ID = "com.stremio.torrentio.addon"
internal val SETTLED = setOf("Ready", "Err")

internal suspend fun Core.installTorrentio() {
    dispatch(Action.LoadAddonDetails(TORRENTIO_URL), Field.ADDON_DETAILS)
    val details = withTimeout(NETWORK_TIMEOUT_MS) {
        addonDetails.first { it?.remoteAddon?.content?.type in SETTLED }
    }
    val remote = details!!.remoteAddon!!.content
    assertEquals("Ready", remote.type)
    dispatch(Action.InstallAddon(remote.content!!.jsonObject))
    withTimeout(NETWORK_TIMEOUT_MS) {
        ctx.first { ctx -> ctx?.profile?.addons?.any { it.manifest.id == TORRENTIO_ID } == true }
    }
}
