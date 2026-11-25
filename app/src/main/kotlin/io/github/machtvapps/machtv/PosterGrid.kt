package io.github.machtvapps.machtv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.machtvapps.machtv.core.MetaPreview

val POSTER_WIDTH = 120.dp
val POSTER_HEIGHT = 180.dp
const val GRID_COLUMNS = 6

data class PosterItem(val id: String, val type: String, val name: String, val poster: String?, val badge: String? = null, val rating: String? = null)

fun metaPoster(meta: MetaPreview): PosterItem = PosterItem(meta.id, meta.type, meta.name, meta.poster, rating = imdbRating(meta.links))

fun nearEnd(index: Int, count: Int, columns: Int = GRID_COLUMNS): Boolean = index >= count - 2 * columns

private class GridMemory(var item: String? = null)

private val GridMemorySaver = listSaver<GridMemory, String?>({ listOf(it.item) }, { GridMemory(it[0]) })

@Composable
fun PosterCard(item: PosterItem, onClick: () -> Unit, onFocused: () -> Unit, modifier: Modifier = Modifier) {
    val hold = remember { HoldState() }
    ZoomFrame(
        width = POSTER_WIDTH,
        height = POSTER_HEIGHT,
        radius = 8.dp,
        zoom = 1.12f,
        lift = 4.dp,
        pressedScale = 0.97f,
        onClick = hold.unlessHeld(onClick),
        modifier = modifier.holdPress(hold, movieWatchedToggle(item.id, item.type, item.name, item.poster)),
        onFocused = onFocused,
    ) {
        Box(Modifier.fillMaxSize().background(TileSurface)) {
            BasicText(
                item.name,
                style = MachText.cardTitle.copy(textAlign = TextAlign.Center),
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.Center).padding(8.dp),
            )
            if (item.poster != null) {
                AsyncImage(model = item.poster, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            item.rating?.let { RatingBadge(it, Modifier.align(Alignment.TopStart).padding(8.dp)) }
            if (item.id in LocalWatched.current) WatchedBadge(Modifier.align(Alignment.TopEnd).padding(8.dp))
            item.badge?.let {
                BasicText(
                    it,
                    style = MachText.badge,
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(BadgeFill).padding(vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
fun PosterGrid(
    items: List<PosterItem>,
    onClick: (PosterItem) -> Unit,
    onNearEnd: () -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
    state: LazyGridState = rememberLazyGridState(),
    fallbackFocus: FocusRequester? = null,
    onItemFocused: () -> Unit = {},
    header: LazyGridScope.() -> Unit = {},
) {
    val gridState = state
    val memory = rememberSaveable(saver = GridMemorySaver) { GridMemory() }
    val firstRequester = remember { FocusRequester() }
    val returnRequester = remember { FocusRequester() }
    var initialFocusDone by remember { mutableStateOf(false) }
    LaunchedEffect(items.isNotEmpty()) {
        if (initialFocusDone) return@LaunchedEffect
        if (items.isNotEmpty()) {
            withFrameNanos { }
            val remembered = items.any { it.id == memory.item }
            if (!remembered || !returnRequester.requestFocus(FocusDirection.Enter)) (fallbackFocus ?: firstRequester).requestFocus()
            initialFocusDone = true
        } else if (fallbackFocus != null && memory.item == null) {
            withFrameNanos { }
            fallbackFocus.requestFocus()
            initialFocusDone = true
        }
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(GRID_COLUMNS),
        state = gridState,
        contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 8.dp, bottom = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        header()
        message?.let { text ->
            item(span = { GridItemSpan(maxLineSpan) }) { BasicText(text, style = MachText.body.onSlate()) }
        }
        itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
            Box(contentAlignment = Alignment.Center) {
                PosterCard(
                    item = item,
                    onClick = { onClick(item) },
                    onFocused = {
                        memory.item = item.id
                        onItemFocused()
                        if (nearEnd(index, items.size)) onNearEnd()
                    },
                    modifier = when {
                        item.id == memory.item -> Modifier.focusRequester(returnRequester)
                        index == 0 -> Modifier.focusRequester(firstRequester)
                        else -> Modifier
                    },
                )
            }
        }
    }
}
