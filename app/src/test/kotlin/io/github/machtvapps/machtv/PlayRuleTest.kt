package io.github.machtvapps.machtv

import io.github.machtvapps.machtv.core.LastUsedStream
import io.github.machtvapps.machtv.core.Loadable
import io.github.machtvapps.machtv.core.MetaVideo
import io.github.machtvapps.machtv.core.ResourcePath
import io.github.machtvapps.machtv.core.ResourceRequest
import io.github.machtvapps.machtv.core.StreamList
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayRuleTest {
    private fun list(type: String?, vararg streams: JsonObject) = StreamList(
        ResourceRequest("https://addon.example/manifest.json", ResourcePath("stream", "movie", "tt1")),
        type?.let { Loadable(it, if (it == "Ready") JsonArray(streams.toList()) else null) },
    )

    private fun torrent(hash: String) = buildJsonObject { put("infoHash", hash) }

    private fun url(value: String) = buildJsonObject { put("url", value) }

    private fun video(id: String, season: Int?, episode: Int?) = MetaVideo(id = id, season = season, episode = episode)

    @Test
    fun torrentsAndHttpUrlsArePlayable() {
        assertTrue(isPlayable(torrent("ab")))
        assertTrue(isPlayable(url("https://cdn.example/video.mp4")))
        assertFalse(isPlayable(url("magnet:?xt=urn:btih:ab")))
        assertFalse(isPlayable(buildJsonObject { put("ytId", "x") }))
    }

    @Test
    fun firstAddonStillLoadingMeansWait() {
        assertEquals(StreamPick.Waiting, pickStream(listOf(list("Loading"), list("Ready", torrent("b")))))
    }

    @Test
    fun addonsWithoutPlayableStreamsAreSkipped() {
        val pick = pickStream(listOf(list("Ready", url("magnet:?x")), list("Err"), list("Ready", torrent("c"))))
        assertEquals("c", ((pick as StreamPick.Found).stream["infoHash"] as JsonPrimitive).content)
    }

    @Test
    fun nothingPlayableIsMissing() {
        assertEquals(StreamPick.Missing, pickStream(listOf(list("Err"))))
        assertEquals(StreamPick.Missing, pickStream(emptyList()))
    }

    @Test
    fun lastUsedStreamComesBeforeThePlayRule() {
        val lists = listOf(list("Ready", torrent("a"), torrent("b")))
        val request = lists[0].request
        val used = pickStream(lists, LastUsedStream(request, Loadable("Ready", torrent("b"))))
        assertEquals("b", ((used as StreamPick.Found).stream["infoHash"] as JsonPrimitive).content)
        assertEquals(StreamPick.Waiting, pickStream(lists, LastUsedStream(request, Loadable("Loading"))))
        val none = pickStream(lists, LastUsedStream(request, Loadable("Ready", JsonNull)))
        assertEquals("a", ((none as StreamPick.Found).stream["infoHash"] as JsonPrimitive).content)
    }

    @Test
    fun seriesVideoPrefersTheLastPlayedEpisode() {
        val videos = listOf(video("s0e1", 0, 1), video("s1e2", 1, 2), video("s1e1", 1, 1), video("s2e1", 2, 1))
        assertEquals("s2e1", seriesVideo(videos, "s2e1")?.id)
        assertEquals("s1e1", seriesVideo(videos, null)?.id)
        assertEquals("s1e1", seriesVideo(videos, "unknown")?.id)
    }
}
