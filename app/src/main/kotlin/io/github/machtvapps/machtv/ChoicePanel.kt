package io.github.machtvapps.machtv

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider

@Composable
fun ChoicePanel(title: String, options: List<String>, selected: Int, onPick: (Int) -> Unit, onClose: () -> Unit) {
    val focusIndex = selected.coerceIn(0, options.lastIndex)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = focusIndex)
    val current = remember { FocusRequester() }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val window = (LocalView.current.parent as DialogWindowProvider).window
        SideEffect { window.setDimAmount(0.8f) }
        LaunchedEffect(Unit) {
            withFrameNanos { }
            current.requestFocus()
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            MetalPanel(Modifier.width(420.dp)) {
                BasicText(title, style = MachText.panelTitle)
                LazyColumn(state = listState, modifier = Modifier.padding(top = 16.dp).heightIn(max = 360.dp)) {
                    itemsIndexed(options) { index, option ->
                        FocusRow(
                            onClick = { onPick(index) },
                            modifier = if (index == focusIndex) Modifier.focusRequester(current) else Modifier,
                            polished = true,
                        ) {
                            BasicText(option, style = MachText.rowTitle, modifier = Modifier.weight(1f))
                            if (index == selected) BasicText(CHECK_GLYPH, style = MachText.navGlyph)
                        }
                    }
                }
            }
        }
    }
}
