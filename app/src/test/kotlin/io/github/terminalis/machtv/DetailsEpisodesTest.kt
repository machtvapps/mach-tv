package io.github.terminalis.machtv

import io.github.terminalis.machtv.core.MetaVideo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DetailsEpisodesTest {
    @Test
    fun airDateReadsCinemetasTimestamps() {
        assertEquals("Aug 4, 2022", airDate("2022-08-04T11:00:00.000Z"))
        assertEquals("Nov 6, 2021", airDate("2021-11-06T23:30:00.000Z"))
    }

    @Test
    fun airDateIgnoresMissingOrUnreadableValues() {
        assertNull(airDate(null))
        assertNull(airDate("soon"))
    }

    @Test
    fun seasonsOfSkipsSpecialsAndSorts() {
        val videos = listOf(MetaVideo("a", season = 2), MetaVideo("b", season = 0), MetaVideo("c", season = 1), MetaVideo("d", season = 2), MetaVideo("e"))
        assertEquals(listOf(1, 2), seasonsOf(videos))
    }

    @Test
    fun seasonIsWatchedOnlyWhenEveryEpisodeIs() {
        val videos = listOf(MetaVideo("a", season = 1), MetaVideo("b", season = 1), MetaVideo("c", season = 2))
        assertEquals(true, seasonWatched(videos, 1, listOf("a", "b")))
        assertEquals(false, seasonWatched(videos, 1, listOf("a", "c")))
        assertEquals(false, seasonWatched(videos, 3, listOf("a", "b", "c")))
    }

    @Test
    fun posterMetaCarriesWhatCoreNeeds() {
        assertEquals("""{"id":"tt1254207","type":"movie","name":"Big Buck Bunny","poster":null}""", posterMeta("tt1254207", "movie", "Big Buck Bunny", null).toString())
    }
}
