package io.github.terminalis.machtv

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.unit.dp

@Composable
fun SettingsPage(blocked: Boolean, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .focusProperties { onEnter = { if (blocked) cancelFocusChange() } }
            .focusGroup()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
fun SettingRow(label: String, value: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlineRow(onClick = onClick, modifier = modifier) {
        Column(Modifier.weight(1f)) {
            BasicText(label, style = MachText.rowTitle)
            BasicText(value, style = MachText.small, modifier = Modifier.padding(top = 6.dp))
        }
        BasicText(CHEVRON_GLYPH, style = MachText.navGlyph.copy(color = NavIdle))
    }
}
