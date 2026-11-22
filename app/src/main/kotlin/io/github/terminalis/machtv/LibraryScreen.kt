package io.github.terminalis.machtv

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.github.terminalis.machtv.core.Action
import io.github.terminalis.machtv.core.Core
import io.github.terminalis.machtv.core.CtxState
import io.github.terminalis.machtv.core.Field
import io.github.terminalis.machtv.core.LibraryEntry

enum class LibrarySort(val key: String?, val label: String) {
    LastWatched("lastwatched", "Last watched"),
    NotWatched("notwatched", "Not watched"),
    TitleAscending("name", "Title (asc)"),
    TitleDescending("namereverse", "Title (desc)"),
    ReleasedAscending(null, "Released (asc)"),
    ReleasedDescending(null, "Released (desc)"),
}

enum class LibraryFilter(val type: String?, val label: String) {
    All(null, "All"),
    Movies("movie", "Movies"),
    Series("series", "Series"),
}

private const val NEW_EPISODE = "New episode"

fun newEpisodeIds(ctx: CtxState?): Set<String> =
    ctx?.notifications?.items.orEmpty().filterValues { it.isNotEmpty() }.keys

fun libraryPosters(entries: List<LibraryEntry>, newEpisodes: Set<String>, ratings: Map<String, String?> = emptyMap()): List<PosterItem> =
    entries.map {
        PosterItem(it.id, it.type, it.name, it.poster, if (it.type == "series" && it.id in newEpisodes) NEW_EPISODE else null, ratings[it.id])
    }

fun releasedOrder(entries: List<LibraryEntry>, type: String?, released: Map<String, String?>, descending: Boolean): List<LibraryEntry> {
    val (dated, undated) = entries.filter { type == null || it.type == type }.partition { released[it.id] != null }
    val ordered = dated.sortedBy { released[it.id] }
    return (if (descending) ordered.reversed() else ordered) + undated
}

@Composable
fun LibraryScreen(core: Core, onDetails: (type: String, id: String) -> Unit, onSection: (Section) -> Unit, onExit: () -> Unit) {
    val library by core.library.collectAsState()
    val ctx by core.ctx.collectAsState()
    var filter by rememberSaveable { mutableStateOf(LibraryFilter.All) }
    var sort by rememberSaveable { mutableStateOf(LibrarySort.LastWatched) }
    var sorting by remember { mutableStateOf(false) }
    val allPill = remember { FocusRequester() }
    val sortPill = remember { FocusRequester() }
    LaunchedEffect(Unit) { core.dispatch(Action.PullNotifications) }
    val sortKey = sort.key
    LaunchedEffect(filter, sort) { if (sortKey != null) core.dispatch(Action.LoadLibrary(filter.type, sortKey), Field.LIBRARY) }
    val app = LocalContext.current.applicationContext as MachTvApp
    val facts = app.libraryFacts.toMap()
    val source = if (sortKey != null) {
        library?.takeIf { it.selected?.request?.type == filter.type && it.selected?.request?.sort == sortKey }?.catalog
    } else {
        ctx?.library
    }
    val current = remember(source, sort, filter, facts) {
        if (sortKey != null) source else source?.let { entries -> releasedOrder(entries, filter.type, facts.mapValues { it.value.released }, sort == LibrarySort.ReleasedDescending) }
    }
    val items = remember(current, ctx, facts) { libraryPosters(current.orEmpty(), newEpisodeIds(ctx), facts.mapValues { it.value.rating }) }
    val addons = ctx?.profile?.addons.orEmpty()
    LaunchedEffect(source, addons) {
        source.orEmpty().filter { it.id !in app.libraryFacts }.forEach { item ->
            val url = cinemetaMetaUrl(addons, item.type, item.id) ?: return@forEach
            runCatching { withContext(Dispatchers.IO) { fetchFacts(url) } }.getOrNull()?.let { app.libraryFacts[item.id] = it }
        }
    }
    val empty = current != null && items.isEmpty()
    LaunchedEffect(empty) {
        if (empty) {
            withFrameNanos { }
            allPill.requestFocus()
        }
    }
    Box(Modifier.fillMaxSize().slate()) {
        Column(
            Modifier
                .fillMaxSize()
                .focusProperties { onEnter = { if (sorting) cancelFocusChange() } }
                .focusGroup(),
        ) {
            TopStrip(visible = true, current = Section.Library, onSection = onSection, onExit = onExit)
            PosterGrid(
                items = items,
                onClick = { onDetails(it.type, it.id) },
                onNearEnd = { if (sortKey != null && core.library.value?.selectable?.nextPage != null) core.dispatch(Action.LibraryNextPage, Field.LIBRARY) },
                message = if (empty) "Titles you add to your library appear here." else null,
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(Modifier.padding(top = 8.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LibraryFilter.entries.forEach { option ->
                            NavPill(
                                option.label,
                                selected = filter == option,
                                onClick = { filter = option },
                                modifier = if (option == LibraryFilter.All) Modifier.focusRequester(allPill) else Modifier,
                            )
                        }
                        Spacer(Modifier.width(16.dp))
                        PillButton("Sort: ${sort.label}", { sorting = true }, Modifier.focusRequester(sortPill), compact = true)
                    }
                }
            }
        }
        if (sorting) {
            ChoicePanel(
                title = "Sort",
                options = LibrarySort.entries.map { it.label },
                selected = sort.ordinal,
                onPick = {
                    sort = LibrarySort.entries[it]
                    sorting = false
                    sortPill.requestFocus()
                },
                onClose = {
                    sorting = false
                    sortPill.requestFocus()
                },
            )
        }
    }
}
