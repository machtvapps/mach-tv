package io.github.terminalis.machtv

import io.github.terminalis.machtv.core.AddonDescriptor
import io.github.terminalis.machtv.core.CtxState
import io.github.terminalis.machtv.core.LibraryEntry
import io.github.terminalis.machtv.core.LibraryState
import io.github.terminalis.machtv.core.NotificationsState
import io.github.terminalis.machtv.core.ProfileState
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LibraryTest {
    @Test
    fun sortMenuListsTheSixSortsInOrder() {
        assertEquals(
            listOf("Last watched", "Not watched", "Title (asc)", "Title (desc)", "Released (asc)", "Released (desc)"),
            LibrarySort.entries.map { it.label },
        )
        assertEquals(listOf("lastwatched", "notwatched", "name", "namereverse", null, null), LibrarySort.entries.map { it.key })
        assertEquals(listOf(null, "movie", "series"), LibraryFilter.entries.map { it.type })
    }

    @Test
    fun releasedOrderSortsByDateAndKeepsUndatedLast() {
        val entries = listOf(
            LibraryEntry("a", "movie", "A"),
            LibraryEntry("b", "series", "B"),
            LibraryEntry("c", "movie", "C"),
            LibraryEntry("d", "movie", "D"),
        )
        val released = mapOf("a" to "2008-05-20T00:00:00.000Z", "b" to "2010-06-16T00:00:00.000Z", "c" to null, "d" to "1999-03-31T00:00:00.000Z")
        assertEquals(listOf("d", "a", "b", "c"), releasedOrder(entries, null, released, descending = false).map { it.id })
        assertEquals(listOf("b", "a", "d", "c"), releasedOrder(entries, null, released, descending = true).map { it.id })
        assertEquals(listOf("a", "d", "c"), releasedOrder(entries, "movie", released, descending = true).map { it.id })
    }

    @Test
    fun newEpisodesComeFromNonEmptyNotifications() {
        val ctx = CtxState(
            ProfileState(emptyList<AddonDescriptor>()),
            NotificationsState(items = mapOf("tt1" to mapOf("tt1:2:1" to JsonObject(emptyMap())), "tt2" to emptyMap())),
        )
        assertEquals(setOf("tt1"), newEpisodeIds(ctx))
    }

    @Test
    fun onlySeriesWithNewEpisodesGetTheBadge() {
        val state = LibraryState(
            catalog = listOf(
                LibraryEntry("tt1", "series", "One", "p1"),
                LibraryEntry("tt2", "series", "Two"),
                LibraryEntry("tt1254207", "movie", "Big Buck Bunny"),
            ),
        )
        val posters = libraryPosters(state.catalog, setOf("tt1", "tt1254207"))
        assertEquals("New episode", posters[0].badge)
        assertNull(posters[1].badge)
        assertNull(posters[2].badge)
        assertEquals("p1", posters[0].poster)
    }

    @Test
    fun metaFactsReadTheImdbScoreAndReleaseDate() {
        assertEquals(MetaFacts("6.4", "2008-05-20T00:00:00.000Z"), metaFacts("""{"meta":{"id":"tt1254207","imdbRating":"6.4","released":"2008-05-20T00:00:00.000Z"}}"""))
        assertEquals(MetaFacts(null, null), metaFacts("""{"meta":{"id":"tt1254207","imdbRating":""}}"""))
    }
}
