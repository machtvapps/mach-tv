package io.github.terminalis.machtv

import io.github.terminalis.machtv.core.ContinueWatchingItem
import io.github.terminalis.machtv.core.ContinueWatchingState
import io.github.terminalis.machtv.core.LibraryEntry
import io.github.terminalis.machtv.core.LibraryItemState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeScreenChannelsTest {
    private val library = listOf(
        LibraryEntry("tt1748166", "series", "Pioneer One", "https://example.org/pioneer.jpg"),
        LibraryEntry("tt1254207", "movie", "Big Buck Bunny", "https://example.org/bunny.jpg"),
        LibraryEntry("tt0807840", "movie", "Elephants Dream", null),
        LibraryEntry("tt5311514", "movie", "Your Name", "https://example.org/name.jpg"),
    )

    @Test
    fun movieLibraryKeepsMoviesWithPostersInOrder() {
        assertEquals(
            listOf(
                ChannelProgram("tt1254207", "movie", "Big Buck Bunny", "https://example.org/bunny.jpg"),
                ChannelProgram("tt5311514", "movie", "Your Name", "https://example.org/name.jpg"),
            ),
            channelPrograms(HomeChannel.MovieLibrary, null, library),
        )
    }

    @Test
    fun seriesLibraryKeepsSeries() {
        assertEquals(
            listOf(ChannelProgram("tt1748166", "series", "Pioneer One", "https://example.org/pioneer.jpg")),
            channelPrograms(HomeChannel.SeriesLibrary, null, library),
        )
    }

    @Test
    fun continueWatchingCarriesProgress() {
        val watching = ContinueWatchingState(
            listOf(
                ContinueWatchingItem("tt1254207", "movie", "Big Buck Bunny", "https://example.org/bunny.jpg", LibraryItemState(24_000, 634_533)),
                ContinueWatchingItem("tt0807840", "movie", "Elephants Dream", null, LibraryItemState(1_000, 2_000)),
            ),
        )
        assertEquals(
            listOf(ChannelProgram("tt1254207", "movie", "Big Buck Bunny", "https://example.org/bunny.jpg", 24_000, 634_533)),
            channelPrograms(HomeChannel.ContinueWatching, watching, library),
        )
    }

    @Test
    fun channelsAreNamedAsShown() {
        assertEquals(listOf("Continue Watching", "Movie Library", "Series Library"), HomeChannel.entries.map { it.title })
    }

    @Test
    fun detailsLinkReadsBack() {
        assertEquals(Screen.Details("series", "kitsu:7442"), detailsScreen(detailsLink("series", "kitsu:7442")))
        assertNull(detailsScreen("tt1254207"))
        assertNull(detailsScreen(null))
    }
}
