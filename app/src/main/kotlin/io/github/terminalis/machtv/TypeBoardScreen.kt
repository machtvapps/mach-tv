package io.github.terminalis.machtv

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.terminalis.machtv.core.Action
import io.github.terminalis.machtv.core.Core
import io.github.terminalis.machtv.core.Field
import kotlinx.coroutines.launch

private const val BILLBOARD_ITEM = "billboard"
private const val FILTERS_ITEM = "filters"
private val BILLBOARD_OUTSET = 6.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TypeBoardScreen(
    core: Core,
    type: String,
    onDetails: (type: String, id: String) -> Unit,
    onSection: (Section) -> Unit,
    onExit: () -> Unit,
) {
    val movies = type == "movie"
    val app = LocalContext.current.applicationContext as MachTvApp
    val rows by (if (movies) app.rows.movies else app.rows.series).collectAsState()
    val posters by app.rows.seeAll.collectAsState()
    val filters = remember(rows) { typeFilters(rows) }
    var picked by rememberSaveable { mutableStateOf<String?>(null) }
    val filter = filters.firstOrNull { it.key == picked } ?: filters.firstOrNull()
    val request = filter?.request
    var headerFocused by remember { mutableStateOf(true) }
    var billboardFocused by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()
    val billboardFocus = remember { FocusRequester() }
    val pickedFocus = remember { FocusRequester() }
    val defaultBringIntoView = LocalBringIntoViewSpec.current
    val holdHeader = remember(defaultBringIntoView) {
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float =
                if (headerFocused) 0f else defaultBringIntoView.calculateScrollDistance(offset, size, containerSize)
        }
    }
    val scope = rememberCoroutineScope()
    val billboardInPlace by remember { derivedStateOf { gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset == 0 } }
    LaunchedEffect(type) {
        core.dispatch(Action.LoadCatalogsWithExtra(type), if (movies) Field.MOVIES else Field.SERIES)
    }
    LaunchedEffect(request) {
        if (request != null && core.catalog.value?.selected?.request != request) core.dispatch(Action.LoadCatalogWithFilters(request), Field.CATALOG)
    }
    val current = posters.takeIf { request != null && it.request == request }
    val items = current?.items.orEmpty()
    val message = when {
        current?.first == "Err" -> "This catalog didn't load."
        current?.first == "Ready" && items.isEmpty() -> "Nothing here."
        else -> null
    }
    Column(Modifier.fillMaxSize().slate()) {
        TopStrip(
            visible = headerFocused,
            current = if (movies) Section.Movies else Section.Series,
            onSection = onSection,
            onExit = onExit,
            atTop = billboardFocused,
            onReturn = {
                scope.launch {
                    gridState.scrollToItem(0)
                    withFrameNanos { }
                    billboardFocus.requestFocus()
                }
            },
        )
        CompositionLocalProvider(LocalBringIntoViewSpec provides holdHeader) {
        PosterGrid(
            items = items,
            onClick = { onDetails(it.type, it.id) },
            onNearEnd = { if (core.catalog.value?.selectable?.nextPage != null) core.dispatch(Action.CatalogNextPage, Field.CATALOG) },
            message = message,
            state = gridState,
            fallbackFocus = billboardFocus,
            onItemFocused = { headerFocused = false },
        ) {
            item(key = BILLBOARD_ITEM, span = { GridItemSpan(maxLineSpan) }) {
                Billboard(
                    titles = current?.featured.orEmpty(),
                    inPlace = billboardInPlace,
                    onOpen = { onDetails(it.type, it.id) },
                    onFocused = {
                        headerFocused = true
                        scope.launch { gridState.animateScrollToItem(0) }
                    },
                    modifier = Modifier
                        .layout { measurable, constraints ->
                            val outset = BILLBOARD_OUTSET.roundToPx()
                            val width = constraints.maxWidth + 2 * outset
                            val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
                            layout(constraints.maxWidth, placeable.height) { placeable.place(-outset, 0) }
                        }
                        .onFocusChanged { billboardFocused = it.hasFocus }
                        .focusRequester(billboardFocus),
                )
            }
            item(key = FILTERS_ITEM, span = { GridItemSpan(maxLineSpan) }) {
                CompositionLocalProvider(LocalBringIntoViewSpec provides defaultBringIntoView) {
                    LazyRow(
                        contentPadding = PaddingValues(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .onFocusChanged {
                                if (it.hasFocus) {
                                    headerFocused = true
                                    scope.launch { gridState.animateScrollToItem(0) }
                                }
                            }
                            .focusRestorer(pickedFocus),
                    ) {
                        items(filters, key = { it.key }) { item ->
                            val chosen = item.key == filter?.key
                            PillButton(item.label, { picked = item.key }, if (chosen) Modifier.focusRequester(pickedFocus) else Modifier, compact = true, selected = chosen)
                        }
                    }
                }
            }
        }
        }
    }
}
