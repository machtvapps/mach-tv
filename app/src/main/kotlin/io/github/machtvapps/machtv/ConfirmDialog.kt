package io.github.machtvapps.machtv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider

@Composable
fun ConfirmDialog(title: String, action: String, onConfirm: () -> Unit, onCancel: () -> Unit, second: Pair<String, () -> Unit>? = null) {
    val first = remember { FocusRequester() }
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val window = (LocalView.current.parent as DialogWindowProvider).window
        SideEffect { window.setDimAmount(0.8f) }
        LaunchedEffect(Unit) {
            withFrameNanos { }
            first.requestFocus()
        }
        Box(Modifier.fillMaxSize().onPreviewKeyEvent { it.nativeKeyEvent.repeatCount > 0 }, contentAlignment = Alignment.Center) {
            MetalPanel {
                BasicText(title, style = MachText.panelTitle)
                Row(Modifier.padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    PillButton(action, onConfirm, Modifier.focusRequester(first))
                    second?.let { (label, run) -> PillButton(label, run) }
                    PillButton("Cancel", onCancel)
                }
            }
        }
    }
}
