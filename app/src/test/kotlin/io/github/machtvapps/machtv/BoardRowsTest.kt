package io.github.machtvapps.machtv

import kotlin.random.Random
import io.github.machtvapps.machtv.core.AddonDescriptor
import io.github.machtvapps.machtv.core.AddonManifest
import io.github.machtvapps.machtv.core.BoardState
import io.github.machtvapps.machtv.core.CatalogPage
import io.github.machtvapps.machtv.core.ExtraValue
import io.github.machtvapps.machtv.core.Loadable
import io.github.machtvapps.machtv.core.ResourcePath
import io.github.machtvapps.machtv.core.ResourceRequest
import io.github.machtvapps.machtv.core.ContinueWatchingItem
import io.github.machtvapps.machtv.core.LibraryItemState
import io.github.machtvapps.machtv.core.ManifestCatalog
import io.github.machtvapps.machtv.core.MetaPreview
import io.github.machtvapps.machtv.core.MetaLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray

class BoardRowsTest {
    private val cinemeta = AddonDescriptor(
        AddonManifest(
            id = "com.linvo.cinemeta",
            name = "Cinemeta",
            catalogs = listOf(ManifestCatalog("movie", "top", "Popular"), ManifestCatalog("series", "imdbRating", "Featured")),
        ),
        transportUrl = "https://v3-cinemeta.strem.io/manifest.json",
    )

    @Test
    fun catalogTitleUsesManifestNameAndType() {
        assertEquals("Popular · Movies", catalogTitle(listOf(cinemeta), cinemeta.transportUrl, "movie", "top"))
        assertEquals("Featured · Series", catalogTitle(listOf(cinemeta), cinemeta.transportUrl, "series", "imdbRating"))
    }

    @Test
    fun catalogTitleFallsBackToTheCatalogId() {
        assertEquals("trending · Anime", catalogTitle(listOf(cinemeta), "https://example.org/manifest.json", "anime", "trending"))
    }

    @Test
    fun metaCardCarriesPosterBackdropRatingAndFacts() {
        val meta = MetaPreview(
            id = "tt1254207",
            type = "movie",
            name = "Big Buck Bunny",
            poster = "p",
            background = "b",
            logo = "l",
            releaseInfo = "2008",
            runtime = "10 min",
            links = listOf(MetaLink("6.4", "imdb"), MetaLink("Animation", "Genres"), MetaLink("Short", "Genres"), MetaLink("Comedy", "Genres")),
        )
        val card = metaCard(meta)
        assertEquals("p", card.poster)
        assertEquals("b", card.backdrop)
        assertEquals("l", card.logo)
        assertEquals("6.4", card.rating)
        assertEquals("Animation, Short · 2008 · 10 min", card.facts)
        assertEquals("2008", card.subtitle)
        assertEquals(0f, card.progress)
    }

    @Test
    fun metaCardWithoutBackgroundUsesMetahubForImdbIds() {
        assertEquals("https://images.metahub.space/background/medium/tt0111161/img", metaCard(MetaPreview(id = "tt0111161", type = "movie", name = "X")).backdrop)
        assertNull(metaCard(MetaPreview(id = "kitsu:1", type = "movie", name = "X")).backdrop)
    }

    @Test
    fun cardFactsSkipsWhatIsMissing() {
        assertEquals("2021–2024", cardFacts(emptyList(), "2021–2024", null))
        assertEquals("", cardFacts(emptyList(), null, " "))
    }

    @Test
    fun imdbRatingIgnoresBlankLinks() {
        assertNull(imdbRating(listOf(MetaLink("", "imdb"))))
        assertNull(imdbRating(listOf(MetaLink("Drama", "Genres"))))
    }

    @Test
    fun continueWatchingCardShowsEpisodeAndProgress() {
        val item = ContinueWatchingItem(
            id = "tt1748166",
            type = "series",
            name = "Pioneer One",
            poster = "p",
            state = LibraryItemState(timeOffset = 600_000, duration = 2_400_000, videoId = "tt1748166:1:3"),
        )
        val card = continueWatchingCard(item)
        assertEquals("p", card.poster)
        assertEquals("https://images.metahub.space/background/medium/tt1748166/img", card.backdrop)
        assertEquals("https://images.metahub.space/logo/medium/tt1748166/img", card.logo)
        assertEquals("S1, E3", card.subtitle)
        assertEquals("S1, E3", card.facts)
        assertEquals(0.25f, card.progress)
    }

    @Test
    fun continueWatchingCardWithoutImdbIdUsesPoster() {
        val item = ContinueWatchingItem(id = "yt_id:abc", type = "movie", name = "Clip", poster = "p", state = LibraryItemState())
        assertEquals("p", continueWatchingCard(item).backdrop)
        assertNull(continueWatchingCard(item).logo)
        assertEquals("", continueWatchingCard(item).subtitle)
    }

    @Test
    fun continueWatchingCardShowsAMoviesRuntime() {
        val item = ContinueWatchingItem(id = "tt1254207", type = "movie", name = "Big Buck Bunny", state = LibraryItemState(timeOffset = 200_000, duration = 596_000))
        assertEquals("Runtime: 10 min", continueWatchingCard(item).subtitle)
    }

    @Test
    fun metahubBackgroundOnlyForImdbIds() {
        assertEquals("https://images.metahub.space/background/medium/tt0111161/img", metahubBackground("tt0111161"))
        assertNull(metahubBackground("kitsu:1"))
    }

    private fun request(type: String, id: String, vararg extra: Pair<String, String>) =
        ResourceRequest(cinemeta.transportUrl, ResourcePath("catalog", type, id, extra.map { ExtraValue(it.first, it.second) }))

    @Test
    fun typeRowTitles() {
        assertEquals("Popular", typeRowTitle(listOf(cinemeta), request("movie", "top")))
        assertEquals("New in 2026", typeRowTitle(listOf(cinemeta), request("movie", "year", "genre" to "2026")))
        assertEquals("Action", typeRowTitle(listOf(cinemeta), request("movie", "top", "genre" to "Action")))
    }

    @Test
    fun searchRowTitleNamesTheAddonAndType() {
        assertEquals("Cinemeta · Series", searchRowTitle(listOf(cinemeta), request("series", "top", "search" to "pioneer")))
    }

    @Test
    fun typeFiltersPutNewAfterTheWholeCatalogsAndKeepTheRowOrder() {
        fun row(id: String, title: String, genre: String? = null) = BoardRow(
            "$id|$genre",
            title,
            emptyList(),
            ResourceRequest("https://cinemeta/manifest.json", ResourcePath("catalog", "movie", id, listOfNotNull(genre?.let { ExtraValue("genre", it) }))),
        )
        val filters = typeFilters(listOf(row("top", "Popular"), row("year", "New in 2026", "2026"), row("imdbRating", "Featured"), row("top", "Action", "Action")))
        assertEquals(listOf("Popular", "Featured", "New", "Action"), filters.map { it.label })
        assertEquals("year", filters[2].request.path.id)
    }

    @Test
    fun rowKeysDifferByExtra() {
        assertEquals("${cinemeta.transportUrl}|movie|top", rowKey(request("movie", "top")))
        assertEquals("${cinemeta.transportUrl}|movie|top|genre=Action", rowKey(request("movie", "top", "genre" to "Action")))
    }

    @Test
    fun boardRowsDropFailedAndEmptyRowsAndKeepLoadingOnes() {
        val board = BoardState(
            catalogs = listOf(
                listOf(CatalogPage(request("movie", "top"), Loadable("Loading"))),
                listOf(CatalogPage(request("movie", "imdbRating"), Loadable("Err"))),
                listOf(CatalogPage(request("movie", "top", "genre" to "War"), Loadable("Ready", JsonArray(emptyList())))),
                listOf(CatalogPage(request("movie", "top", "genre" to "Action"))),
            ),
        )
        val rows = BoardRowCache().rows(board) { typeRowTitle(listOf(cinemeta), it) }
        assertEquals(listOf("Popular", "Action"), rows.map { it.title })
        assertEquals(request("movie", "top", "genre" to "Action"), rows[1].request)
    }

    private fun metas(vararg ids: String) =
        Loadable("Ready", Json.parseToJsonElement(ids.joinToString(",", "[", "]") { """{"id":"$it","type":"movie","name":"$it"}""" }))

    private fun board(vararg pages: CatalogPage) = BoardState(catalogs = pages.map { listOf(it) })

    @Test
    fun boardRowCacheKeepsUnchangedRowsAndBuildsNewOnes() {
        val cache = BoardRowCache()
        val popular = request("movie", "top")
        val action = request("movie", "top", "genre" to "Action")
        val first = cache.rows(board(CatalogPage(popular, metas("a")), CatalogPage(action, Loadable("Loading")))) { typeRowTitle(listOf(cinemeta), it) }
        val second = cache.rows(board(CatalogPage(popular, metas("a")), CatalogPage(action, metas("b")))) { typeRowTitle(listOf(cinemeta), it) }
        assertSame(first[0], second[0])
        assertEquals(listOf("b"), second[1].items.map { it.id })
    }

    @Test
    fun boardRowCacheRebuildsARowWhoseItemsOrTitleChanged() {
        val cache = BoardRowCache()
        val popular = request("movie", "top")
        cache.rows(board(CatalogPage(popular, metas("a")))) { typeRowTitle(listOf(cinemeta), it) }
        val newItems = cache.rows(board(CatalogPage(popular, metas("c")))) { typeRowTitle(listOf(cinemeta), it) }
        val newTitle = cache.rows(board(CatalogPage(popular, metas("c")))) { "Top" }
        assertEquals(listOf("c"), newItems[0].items.map { it.id })
        assertEquals("Top", newTitle[0].title)
    }

    private fun card(id: String) = metaCard(MetaPreview(id = id, type = "movie", name = id))

    @Test
    fun billboardPicksComeFromTheFirstThirtyWithoutRepeats() {
        val titles = (1..60).toList()
        val picks = billboardPicks(titles, 5, Random(7))
        assertEquals(5, picks.distinct().size)
        assertTrue(picks.all { it <= 30 })
        assertTrue((1..20L).map { billboardPicks(titles, 5, Random(it)) }.distinct().size > 1)
        assertEquals(listOf(1, 2), billboardPicks(listOf(1, 2), 5, Random(7)).sorted())
    }

    @Test
    fun billboardShowsThreeOfEachPopularRowAndATitleOnce() {
        val movies = (1..40).map { card("m$it") }
        val series = (1..40).map { card("s$it") }
        val ids = billboardTitles(listOf(movies, series), Random(7)).map { it.id }
        assertEquals(listOf("m", "m", "m", "s", "s", "s"), ids.map { it.take(1) })
        assertEquals(ids, billboardTitles(listOf(movies, series), Random(7)).map { it.id })
        assertEquals(3, billboardTitles(listOf(movies.take(3), movies.take(3)), Random(7)).size)
    }

    @Test
    fun popularRowFindsTheTopCatalogOfAType() {
        val top = BoardRow("a", "Popular", listOf(card("m1")), ResourceRequest("https://cinemeta/manifest.json", ResourcePath("catalog", "movie", "top")))
        val other = BoardRow("b", "Featured", listOf(card("m2")), ResourceRequest("https://cinemeta/manifest.json", ResourcePath("catalog", "movie", "imdbRating")))
        assertEquals(listOf("m1"), popularRow(listOf(other, top), "movie").map { it.id })
        assertEquals(emptyList<CardItem>(), popularRow(listOf(other, top), "series"))
    }
}
