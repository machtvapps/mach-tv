package io.github.terminalis.machtv

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.terminalis.machtv.core.Action
import io.github.terminalis.machtv.core.Core
import io.github.terminalis.machtv.core.ExtraValue
import io.github.terminalis.machtv.core.Field
import io.github.terminalis.machtv.core.ResourceRequest
import io.github.terminalis.machtv.core.Searchable

private const val SUGGESTIONS = 8
private const val SEARCH_RANGE_END = 50

fun suggestionLabel(item: Searchable): String =
    item.releaseInfo?.takeIf { it.isNotBlank() }?.let { "${item.name} ($it)" } ?: item.name

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SearchScreen(
    core: Core,
    onDetails: (type: String, id: String) -> Unit,
    onSection: (Section) -> Unit,
    onExit: () -> Unit,
    onSeeAll: (ResourceRequest, String) -> Unit,
) {
    val local by core.localSearch.collectAsState()
    val app = LocalContext.current.applicationContext as MachTvApp
    val searchRows by app.rows.search.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf<String?>(null) }
    val fieldRequester = remember { FocusRequester() }
    val returnRequester = remember { FocusRequester() }
    val lastFocus = rememberSaveable(saver = FocusMemorySaver) { FocusMemory() }
    LaunchedEffect(Unit) {
        if (core.localSearch.value?.ready != true) core.dispatch(Action.LoadLocalSearch, Field.LOCAL_SEARCH)
        withFrameNanos { }
        if (lastFocus.item == null || !returnRequester.requestFocus(FocusDirection.Enter)) fieldRequester.requestFocus()
    }
    fun submit() {
        val text = query.trim()
        if (text.isEmpty()) return
        submitted = text
        core.dispatch(Action.LoadCatalogsWithExtra(type = null, extra = listOf(ExtraValue("search", text))), Field.SEARCH)
        core.dispatch(Action.LoadCatalogRange(start = 0, end = SEARCH_RANGE_END), Field.SEARCH)
    }
    val rows = if (submitted == null) emptyList() else searchRows
    val suggestions = if (query.isBlank()) emptyList() else local?.searchResults.orEmpty()
    val bringIntoView = LocalBringIntoViewSpec.current
    val suggestionState = rememberLazyListState()
    LaunchedEffect(suggestions.map { it.id }) { suggestionState.scrollToItem(0) }
    Column(Modifier.fillMaxSize().slate()) {
        TopStrip(visible = true, current = Section.Search, onSection = onSection, onExit = onExit)
        Row(Modifier.padding(start = 48.dp, end = 48.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            TextInput(
                value = query,
                onValueChange = {
                    query = it
                    if (it.isNotBlank()) core.dispatch(Action.Search(it, SUGGESTIONS), Field.LOCAL_SEARCH)
                },
                onDone = ::submit,
                placeholder = "Search titles",
                keyboardType = KeyboardType.Text,
                modifier = Modifier.weight(1f).focusRequester(fieldRequester),
            )
            PillButton("Search", ::submit, Modifier.padding(start = 16.dp))
        }
        LazyRow(
            state = suggestionState,
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(suggestions, key = { it.id }) { item ->
                PillButton(suggestionLabel(item), { onDetails(item.type, item.id) }, compact = true)
            }
        }
        LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(bottom = 32.dp)) {
            items(rows, key = { it.key }) { row ->
                CardRow(
                    row = row,
                    bringIntoView = bringIntoView,
                    onClick = { onDetails(it.type, it.id) },
                    onFocused = {
                        lastFocus.row = row.key
                        lastFocus.item = it.id
                    },
                    itemModifier = { item ->
                        if (row.key == lastFocus.row && item.id == lastFocus.item) Modifier.focusRequester(returnRequester) else Modifier
                    },
                    onSeeAll = { row.request?.let { onSeeAll(it, row.title) } },
                    onSeeAllFocused = {
                        lastFocus.row = row.key
                        lastFocus.item = SEE_ALL_KEY
                    },
                    seeAllModifier = if (row.key == lastFocus.row && lastFocus.item == SEE_ALL_KEY) Modifier.focusRequester(returnRequester) else Modifier,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
            }
        }
    }
}
