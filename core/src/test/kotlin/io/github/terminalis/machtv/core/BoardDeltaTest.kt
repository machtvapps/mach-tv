package io.github.terminalis.machtv.core

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class BoardDeltaTest {
    private fun page(id: String, type: String = "Ready") = listOf(
        CatalogPage(
            ResourceRequest("https://v3-cinemeta.strem.io/manifest.json", ResourcePath("catalog", "movie", id)),
            Loadable(type, Json.parseToJsonElement("""[{"id":"$id","type":"movie","name":"$id"}]""")),
        ),
    )

    @Test
    fun changedRowsReplaceTheirOwnAndOthersAreKept() {
        val board = BoardState(BoardSelected("movie"), listOf(page("a"), page("b", "Loading"), page("c")))
        val patched = board.patched(BoardDelta(version = 7, selected = BoardSelected("movie"), count = 3, rows = listOf(BoardDeltaRow(1, page("b")))))
        assertSame(board.catalogs[0], patched.catalogs[0])
        assertSame(board.catalogs[2], patched.catalogs[2])
        assertEquals(page("b"), patched.catalogs[1])
    }

    @Test
    fun aNewRowCountTakesEveryRowFromTheDelta() {
        val board = BoardState(BoardSelected("movie"), listOf(page("a")))
        val patched = board.patched(BoardDelta(version = 2, selected = BoardSelected("series"), count = 2, rows = listOf(BoardDeltaRow(0, page("x")), BoardDeltaRow(1, page("y")))))
        assertEquals(listOf(page("x"), page("y")), patched.catalogs)
        assertEquals(BoardSelected("series"), patched.selected)
    }

    @Test
    fun theBridgesDeltaJsonDecodes() {
        val text = """{"version":3,"selected":{"type":"movie","extra":[]},"count":2,"rows":[{"index":1,"catalog":[{"request":{"base":"https://v3-cinemeta.strem.io/manifest.json","path":{"resource":"catalog","type":"movie","id":"top","extra":[]}},"content":{"type":"Loading"}}]}]}"""
        val delta = coreJson.decodeFromString<BoardDelta>(text)
        assertEquals(3L, delta.version)
        assertEquals(1, delta.rows.single().index)
        assertEquals("Loading", delta.rows.single().catalog.single().content?.type)
    }
}
