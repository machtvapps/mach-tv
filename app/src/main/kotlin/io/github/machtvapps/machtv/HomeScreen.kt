package io.github.machtvapps.machtv

import kotlin.random.Random
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import io.github.machtvapps.machtv.core.Action
import io.github.machtvapps.machtv.core.ContinueWatchingState
import io.github.machtvapps.machtv.core.Core
import io.github.machtvapps.machtv.core.Field
import io.github.machtvapps.machtv.core.ResourceRequest

const val CONTINUE_WATCHING_ROW = "continue-watching"

fun homeRows(rows: List<BoardRow>, continueWatching: ContinueWatchingState?): List<BoardRow> {
    val watching = continueWatching?.items.orEmpty()
    val leading = if (watching.isNotEmpty()) {
        listOf(BoardRow(CONTINUE_WATCHING_ROW, "Continue Watching", watching.map(::continueWatchingCard), wide = true))
    } else {
        emptyList()
    }
    return leading + rows
}

@Composable
fun HomeScreen(
    core: Core,
    onDetails: (type: String, id: String) -> Unit,
    onSection: (Section) -> Unit,
    onExit: () -> Unit,
    onSeeAll: (ResourceRequest, String) -> Unit,
) {
    val app = LocalContext.current.applicationContext as MachTvApp
    val focusManager = LocalFocusManager.current
    val home by core.home.collectAsState()
    val boardRows by app.rows.home.collectAsState()
    val continueWatching by core.continueWatching.collectAsState()
    val rows = remember(boardRows, continueWatching) { homeRows(boardRows, continueWatching) }
    val billboardSeed = remember { Random.nextLong() }
    val billboard = remember(rows) { billboardTitles(listOf(popularRow(rows, "movie"), popularRow(rows, "series")), Random(billboardSeed)) }
    val remove: (CardItem) -> Unit = { item ->
        val watching = rows.firstOrNull { it.key == CONTINUE_WATCHING_ROW }?.items.orEmpty()
        val index = watching.indexOfFirst { it.id == item.id }
        focusManager.moveFocus(
            when {
                index < watching.lastIndex -> FocusDirection.Right
                index > 0 -> FocusDirection.Left
                else -> FocusDirection.Up
            },
        )
        core.dispatch(Action.RewindLibraryItem(item.id))
        core.dispatch(Action.DismissNotificationItem(item.id))
    }
    LaunchedEffect(Unit) {
        core.dispatch(Action.LoadCatalogsWithExtra(type = null), Field.HOME)
    }
    RowsBoard(
        core = core,
        section = Section.Home,
        rows = rows,
        billboard = billboard,
        field = Field.HOME,
        boardSize = home?.catalogs?.size ?: 0,
        leadingRows = if (continueWatching?.items.orEmpty().isNotEmpty()) 1 else 0,
        onDetails = onDetails,
        onSection = onSection,
        onExit = onExit,
        onSeeAll = { row -> row.request?.let { onSeeAll(it, row.title) } },
        onRemove = { row -> if (row.key == CONTINUE_WATCHING_ROW) remove else null },
    )
}
