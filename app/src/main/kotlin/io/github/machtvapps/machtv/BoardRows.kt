package io.github.machtvapps.machtv

import kotlin.random.Random
import io.github.machtvapps.machtv.core.AddonDescriptor
import io.github.machtvapps.machtv.core.BoardState
import io.github.machtvapps.machtv.core.CatalogPage
import io.github.machtvapps.machtv.core.ContinueWatchingItem
import io.github.machtvapps.machtv.core.Loadable
import io.github.machtvapps.machtv.core.MetaLink
import io.github.machtvapps.machtv.core.MetaPreview
import io.github.machtvapps.machtv.core.ResourceRequest
import io.github.machtvapps.machtv.core.readyMetas

data class CardItem(
    val id: String,
    val type: String,
    val title: String,
    val subtitle: String,
    val poster: String?,
    val backdrop: String?,
    val logo: String?,
    val rating: String?,
    val facts: String,
    val progress: Float,
    val description: String? = null,
)

data class BoardRow(val key: String, val title: String, val items: List<CardItem>, val request: ResourceRequest? = null, val wide: Boolean = false)

private val imdbId = Regex("^tt\\d+$")

fun metahubBackground(id: String): String? =
    if (imdbId.matches(id)) "https://images.metahub.space/background/medium/$id/img" else null

fun metahubLogo(id: String): String? =
    if (imdbId.matches(id)) "https://images.metahub.space/logo/medium/$id/img" else null

fun imdbRating(links: List<MetaLink>): String? = links.firstOrNull { it.category == "imdb" }?.name?.takeIf { it.isNotBlank() }

fun cardFacts(links: List<MetaLink>, releaseInfo: String?, runtime: String?): String = listOfNotNull(
    links.filter { it.category == "Genres" }.take(2).joinToString(", ") { it.name }.ifEmpty { null },
    releaseInfo?.ifBlank { null },
    runtime?.ifBlank { null },
).joinToString(" · ")

private const val BILLBOARD_POOL = 30

fun <T> billboardPicks(titles: List<T>, count: Int, random: Random): List<T> = titles.take(BILLBOARD_POOL).shuffled(random).take(count)

fun billboardTitles(popular: List<List<CardItem>>, random: Random): List<CardItem> =
    popular.flatMap { billboardPicks(it, 3, random) }.distinctBy { it.id }

fun popularRow(rows: List<BoardRow>, type: String): List<CardItem> =
    rows.firstOrNull { it.request?.path?.id == "top" && it.request.path.type == type }?.items.orEmpty()

fun typeLabel(type: String): String = when (type) {
    "movie" -> "Movies"
    "series" -> "Series"
    else -> type.replaceFirstChar { it.uppercase() }
}

private fun catalogName(addons: List<AddonDescriptor>, base: String, type: String, id: String): String? =
    addons.firstOrNull { it.transportUrl == base }
        ?.manifest?.catalogs?.firstOrNull { it.type == type && it.id == id }
        ?.name

fun catalogTitle(addons: List<AddonDescriptor>, base: String, type: String, id: String): String =
    "${catalogName(addons, base, type, id) ?: id} · ${typeLabel(type)}"

fun typeRowTitle(addons: List<AddonDescriptor>, request: ResourceRequest): String {
    val genre = request.path.extra.firstOrNull { it.name == "genre" }?.value
    return when {
        genre == null -> catalogName(addons, request.base, request.path.type, request.path.id) ?: request.path.id
        request.path.id == "year" -> "New in $genre"
        else -> genre
    }
}

fun searchRowTitle(addons: List<AddonDescriptor>, request: ResourceRequest): String {
    val addon = addons.firstOrNull { it.transportUrl == request.base }?.manifest?.name ?: request.base
    return "$addon · ${typeLabel(request.path.type)}"
}

data class TypeFilter(val key: String, val label: String, val request: ResourceRequest)

fun typeFilters(rows: List<BoardRow>): List<TypeFilter> {
    val filters = rows.mapNotNull { row -> row.request?.let { TypeFilter(row.key, if (it.path.id == "year") "New" else row.title, it) } }
    val (new, rest) = filters.partition { it.request.path.id == "year" }
    val whole = rest.indexOfLast { filter -> filter.request.path.extra.none { it.name == "genre" } } + 1
    return rest.take(whole) + new + rest.drop(whole)
}

fun rowKey(request: ResourceRequest): String =
    (listOf(request.base, request.path.type, request.path.id) + request.path.extra.map { "${it.name}=${it.value}" })
        .joinToString("|")

fun boardRow(page: CatalogPage, title: String): BoardRow? {
    val content = page.content
    if (content?.type == "Err") return null
    val items = content?.readyMetas().orEmpty().map(::metaCard)
    if (content?.type == "Ready" && items.isEmpty()) return null
    return BoardRow(rowKey(page.request), title, items, page.request)
}

class BoardRowCache {
    private class Built(val content: Loadable?, val title: String, val row: BoardRow?)

    private var built = emptyMap<String, Built>()

    fun rows(board: BoardState?, title: (ResourceRequest) -> String): List<BoardRow> {
        val next = LinkedHashMap<String, Built>()
        board?.catalogs.orEmpty().forEach { catalog ->
            val page = catalog.firstOrNull() ?: return@forEach
            val key = rowKey(page.request)
            val name = title(page.request)
            next[key] = built[key]?.takeIf { it.content == page.content && it.title == name } ?: Built(page.content, name, boardRow(page, name))
        }
        built = next
        return next.values.mapNotNull { it.row }
    }
}

fun metaCard(meta: MetaPreview): CardItem = CardItem(
    id = meta.id,
    type = meta.type,
    title = meta.name,
    subtitle = meta.releaseInfo.orEmpty(),
    poster = meta.poster,
    backdrop = meta.background ?: metahubBackground(meta.id),
    logo = meta.logo,
    rating = imdbRating(meta.links),
    facts = cardFacts(meta.links, meta.releaseInfo, meta.runtime),
    progress = 0f,
    description = meta.description?.ifBlank { null },
)

fun continueWatchingCard(item: ContinueWatchingItem): CardItem {
    val parts = item.state.videoId?.split(':').orEmpty()
    val subtitle = when {
        item.type == "series" && parts.size == 3 -> "S${parts[1]}, E${parts[2]}"
        item.type == "movie" && item.state.duration > 0 -> "Runtime: ${(item.state.duration + 30_000) / 60_000} min"
        else -> ""
    }
    val progress = if (item.state.duration > 0) item.state.timeOffset.toFloat() / item.state.duration else 0f
    return CardItem(
        id = item.id,
        type = item.type,
        title = item.name,
        subtitle = subtitle,
        poster = item.poster,
        backdrop = metahubBackground(item.id) ?: item.poster,
        logo = metahubLogo(item.id),
        rating = null,
        facts = subtitle,
        progress = progress.coerceIn(0f, 1f),
    )
}
