package io.github.machtvapps.machtv

import kotlin.random.Random
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.machtvapps.machtv.core.Action
import io.github.machtvapps.machtv.core.CatalogWithFiltersState
import io.github.machtvapps.machtv.core.Core
import io.github.machtvapps.machtv.core.Field
import io.github.machtvapps.machtv.core.Loadable
import io.github.machtvapps.machtv.core.ResourceRequest
import io.github.machtvapps.machtv.core.readyMetas

private const val FEATURED = 5

data class SeeAllPosters(val request: ResourceRequest?, val first: String?, val items: List<PosterItem>, val featured: List<CardItem> = emptyList())

class SeeAllCache {
    private class Page(val request: ResourceRequest, val content: Loadable?, val posters: List<PosterItem>, val featured: List<CardItem>)

    private var pages = emptyList<Page>()

    fun posters(state: CatalogWithFiltersState?): SeeAllPosters {
        val next = state?.catalog.orEmpty().mapIndexed { index, page ->
            pages.getOrNull(index)?.takeIf { it.request == page.request && it.content == page.content }
                ?: page.content?.readyMetas().orEmpty().let { Page(page.request, page.content, it.map(::metaPoster), billboardPicks(it, FEATURED, Random.Default).map(::metaCard)) }
        }
        pages = next
        return SeeAllPosters(state?.selected?.request, state?.catalog?.firstOrNull()?.content?.type, next.flatMap { it.posters }.distinctBy { it.id }, next.firstOrNull()?.featured.orEmpty())
    }
}

@Composable
fun SeeAllScreen(core: Core, request: ResourceRequest, title: String, onDetails: (type: String, id: String) -> Unit) {
    val app = LocalContext.current.applicationContext as MachTvApp
    val posters by app.rows.seeAll.collectAsState()
    LaunchedEffect(request) {
        if (core.catalog.value?.selected?.request != request) core.dispatch(Action.LoadCatalogWithFilters(request), Field.CATALOG)
    }
    val current = posters.takeIf { it.request == request }
    val items = current?.items.orEmpty()
    val message = when {
        current?.first == "Err" -> "This catalog didn't load."
        current?.first == "Ready" && items.isEmpty() -> "Nothing here."
        else -> null
    }
    Box(Modifier.fillMaxSize().slate()) {
        PosterGrid(
            items = items,
            onClick = { onDetails(it.type, it.id) },
            onNearEnd = { if (core.catalog.value?.selectable?.nextPage != null) core.dispatch(Action.CatalogNextPage, Field.CATALOG) },
            message = message,
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                BasicText(title, style = MachText.heroTitle.onSlate(), modifier = Modifier.padding(top = 40.dp, bottom = 8.dp))
            }
        }
    }
}
