package io.github.terminalis.machtv

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import io.github.terminalis.machtv.core.Action
import io.github.terminalis.machtv.core.Core
import io.github.terminalis.machtv.core.Field
import kotlinx.coroutines.launch



private const val RANGE_AHEAD = 3
private const val FOCUS_FRAMES = 10
const val SEE_ALL_KEY = "see-all"
const val ROW_POSTERS = 10
private const val BILLBOARD_KEY = "billboard"

class FocusMemory(var row: String? = null, var item: String? = null)

val FocusMemorySaver = listSaver<FocusMemory, String?>({ listOf(it.row, it.item) }, { FocusMemory(it[0], it[1]) })

@OptIn(ExperimentalFoundationApi::class)
private val HoldRows = object : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = 0f
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RowsBoard(
    core: Core,
    section: Section,
    rows: List<BoardRow>,
    billboard: List<CardItem>,
    field: Field,
    boardSize: Int,
    leadingRows: Int,
    onDetails: (type: String, id: String) -> Unit,
    onSection: (Section) -> Unit,
    onExit: () -> Unit,
    onSeeAll: (BoardRow) -> Unit,
    onRemove: (BoardRow) -> ((CardItem) -> Unit)? = { null },
) {
    var cardsFocused by remember { mutableStateOf(false) }
    var listFocused by remember { mutableStateOf(false) }
    var loadedRange by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
    val listRequester = remember { FocusRequester() }
    val returnRequester = remember { FocusRequester() }
    val billboardRequester = remember { FocusRequester() }
    val lastFocus = rememberSaveable(saver = FocusMemorySaver) { FocusMemory() }
    var initialFocusDone by remember { mutableStateOf(false) }
    var billboardFocused by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val rowBringIntoView = LocalBringIntoViewSpec.current
    val leadingItems = if (billboard.isEmpty()) 0 else 1
    val billboardInPlace by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 } }
    LaunchedEffect(listState, boardSize, leadingRows, leadingItems) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }.collect { last ->
            val wanted = (last - leadingItems - leadingRows + RANGE_AHEAD).coerceIn(0, boardSize)
            if (wanted > loadedRange) {
                core.dispatch(Action.LoadCatalogRange(start = 0, end = wanted), field)
                loadedRange = wanted
            }
        }
    }
    val ready = billboard.isNotEmpty() || rows.any { it.items.isNotEmpty() }
    LaunchedEffect(ready) {
        if (ready && !initialFocusDone) {
            for (frame in 0 until FOCUS_FRAMES) {
                withFrameNanos { }
                if (listFocused) break
                if (lastFocus.item == null || !returnRequester.requestFocus(FocusDirection.Enter)) listRequester.requestFocus()
            }
            initialFocusDone = true
        }
    }
    LaunchedEffect(billboard.isNotEmpty()) {
        val first = rows.firstOrNull()
        if (billboard.isNotEmpty() && initialFocusDone && listFocused && lastFocus.row == first?.key && lastFocus.item == first?.items?.firstOrNull()?.id) {
            listState.scrollToItem(0)
            withFrameNanos { }
            billboardRequester.requestFocus()
        }
    }

    Box(Modifier.fillMaxSize().slate()) {
        Column(Modifier.fillMaxSize()) {
            TopStrip(
                visible = !cardsFocused,
                current = section,
                onSection = onSection,
                onExit = onExit,
                atTop = billboardFocused || billboard.isEmpty(),
                onReturn = {
                    scope.launch {
                        listState.scrollToItem(0)
                        withFrameNanos { }
                        billboardRequester.requestFocus()
                    }
                },
            )
            CompositionLocalProvider(LocalBringIntoViewSpec provides HoldRows) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .focusRequester(listRequester)
                        .onFocusChanged {
                            listFocused = it.hasFocus
                            if (!it.hasFocus) cardsFocused = false
                        },
                ) {
                    if (billboard.isNotEmpty()) {
                        item(key = BILLBOARD_KEY) {
                            Billboard(
                                titles = billboard,
                                inPlace = billboardInPlace,
                                onOpen = { onDetails(it.type, it.id) },
                                onFocused = {
                                    cardsFocused = false
                                    lastFocus.row = BILLBOARD_KEY
                                    lastFocus.item = BILLBOARD_KEY
                                    scope.launch { listState.animateScrollToItem(0) }
                                },
                                modifier = Modifier
                                    .padding(start = 42.dp, end = 42.dp, bottom = 16.dp)
                                    .onFocusChanged { billboardFocused = it.hasFocus }
                                    .focusRequester(billboardRequester)
                                    .then(if (lastFocus.item == BILLBOARD_KEY) Modifier.focusRequester(returnRequester) else Modifier),
                            )
                        }
                    }
                    itemsIndexed(rows, key = { _, row -> row.key }) { index, row ->
                        CardRow(
                            row = row,
                            bringIntoView = rowBringIntoView,
                            onClick = { onDetails(it.type, it.id) },
                            onRemove = onRemove(row),
                            onFocused = { item ->
                                cardsFocused = true
                                lastFocus.row = row.key
                                lastFocus.item = item.id
                            },
                            itemModifier = { item ->
                                if (row.key == lastFocus.row && item.id == lastFocus.item) Modifier.focusRequester(returnRequester) else Modifier
                            },
                            onSeeAll = { onSeeAll(row) },
                            onSeeAllFocused = {
                                cardsFocused = true
                                lastFocus.row = row.key
                                lastFocus.item = SEE_ALL_KEY
                            },
                            seeAllModifier = if (row.key == lastFocus.row && lastFocus.item == SEE_ALL_KEY) {
                                Modifier.focusRequester(returnRequester)
                            } else {
                                Modifier
                            },
                            modifier = Modifier
                                .padding(bottom = 16.dp)
                                .onFocusChanged { if (it.hasFocus) scope.launch { listState.animateScrollToItem(index + leadingItems) } },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CardRow(
    row: BoardRow,
    bringIntoView: BringIntoViewSpec,
    onClick: (CardItem) -> Unit,
    onFocused: (CardItem) -> Unit,
    itemModifier: (CardItem) -> Modifier,
    onSeeAll: () -> Unit,
    onSeeAllFocused: () -> Unit,
    seeAllModifier: Modifier,
    modifier: Modifier = Modifier,
    onRemove: ((CardItem) -> Unit)? = null,
) {
    val capped = row.request != null && row.items.size > ROW_POSTERS
    val shown = remember(row.items, capped) { if (capped) row.items.take(ROW_POSTERS) else row.items }
    Column(modifier) {
        BasicText(row.title, style = MachText.rowTitle.onSlate(), modifier = Modifier.padding(start = 48.dp, end = 48.dp, bottom = 20.dp))
        if (row.items.isEmpty()) {
            Spacer(Modifier.height(if (row.wide) ROW_WIDE_HEIGHT else ROW_POSTER_HEIGHT))
        } else {
            CompositionLocalProvider(LocalBringIntoViewSpec provides bringIntoView) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 48.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.focusRestorer(),
                ) {
                    items(shown, key = { it.id }) { item ->
                        PosterCard(
                            item = item,
                            onClick = { onClick(item) },
                            onFocused = { onFocused(item) },
                            onRemove = onRemove?.let { { it(item) } },
                            wide = row.wide,
                            modifier = itemModifier(item),
                        )
                    }
                    if (capped) {
                        item(key = SEE_ALL_KEY) { SeeAllCard(onSeeAll, onSeeAllFocused, seeAllModifier) }
                    }
                }
            }
        }
    }
}
