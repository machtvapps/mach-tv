package io.github.machtvapps.machtv

import io.github.machtvapps.machtv.core.PlayerState
import io.github.machtvapps.machtv.core.coreJson
import io.github.machtvapps.machtv.core.readySubtitles
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerStateTest {
    @Test
    fun playerStateReadsTheNextStreamAndAddonSubtitles() {
        val json = """
            {"selected":null,"stream":null,"libraryItem":null,
             "nextVideo":{"id":"tt1748166:1:2","title":"The Man From Mars","released":"2010-06-27T00:00:00Z","season":1,"episode":2},
             "nextStreams":{"request":{"base":"https://torrentio.strem.fun/sort=seeders/manifest.json","path":{"resource":"stream","type":"series","id":"tt1748166:1:2","extra":[]}},
                            "content":{"type":"Ready","content":[{"infoHash":"91b0ef3800459497247cb9173c643a60aa12c9b9","fileIdx":1}]}},
             "nextStream":{"infoHash":"91b0ef3800459497247cb9173c643a60aa12c9b9","fileIdx":1},
             "subtitles":[{"request":{"base":"https://opensubtitles-v3.strem.io/manifest.json","path":{"resource":"subtitles","type":"movie","id":"tt1254207","extra":[]}},
                           "content":{"type":"Ready","content":[{"id":"5833874","lang":"eng","url":"https://subs5.strem.io/en/download/file/1954412181"}]}}]}
        """
        val state = coreJson.decodeFromString<PlayerState>(json)
        assertEquals("tt1748166:1:2", state.nextVideo?.id)
        assertEquals(2, state.nextVideo?.episode)
        assertEquals("https://torrentio.strem.fun/sort=seeders/manifest.json", state.nextStreams?.request?.base)
        assertEquals(listOf("eng"), state.subtitles.flatMap { it.readySubtitles() }.map { it.lang })
    }
}
