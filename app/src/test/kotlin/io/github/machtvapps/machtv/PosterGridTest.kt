package io.github.machtvapps.machtv

import io.github.machtvapps.machtv.core.CatalogPage
import io.github.machtvapps.machtv.core.CatalogWithFiltersState
import io.github.machtvapps.machtv.core.Loadable
import io.github.machtvapps.machtv.core.ResourcePath
import io.github.machtvapps.machtv.core.ResourceRequest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PosterGridTest {
    @Test
    fun nearEndCoversTheLastTwoLines() {
        assertFalse(nearEnd(index = 17, count = 30, columns = 6))
        assertTrue(nearEnd(index = 18, count = 30, columns = 6))
        assertTrue(nearEnd(index = 29, count = 30, columns = 6))
        assertTrue(nearEnd(index = 0, count = 5, columns = 6))
    }

    private val request = ResourceRequest("https://v3-cinemeta.strem.io/manifest.json", ResourcePath("catalog", "movie", "top"))

    private fun page(ids: String) = CatalogPage(
        request,
        Loadable("Ready", Json.parseToJsonElement(ids.split(',').joinToString(",", "[", "]") { """{"id":"$it","type":"movie","name":"$it"}""" })),
    )

    @Test
    fun seeAllPostersJoinPagesWithoutRepeats() {
        val state = CatalogWithFiltersState(catalog = listOf(page("a,b"), page("b,c")))
        assertEquals(listOf("a", "b", "c"), SeeAllCache().posters(state).items.map { it.id })
    }

    @Test
    fun seeAllCacheKeepsLoadedPagesWhenAPageIsAdded() {
        val cache = SeeAllCache()
        val first = cache.posters(CatalogWithFiltersState(catalog = listOf(page("a,b"))))
        val second = cache.posters(CatalogWithFiltersState(catalog = listOf(page("a,b"), page("c"))))
        assertEquals(listOf("a", "b", "c"), second.items.map { it.id })
        assertSame(first.items[0], second.items[0])
    }
}
