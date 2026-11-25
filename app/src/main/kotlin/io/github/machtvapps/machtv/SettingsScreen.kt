package io.github.machtvapps.machtv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import io.github.machtvapps.machtv.core.Core

private enum class SettingsMenu(val label: String) {
    Addons("Addons"),
    Subtitles("Subtitles & audio"),
    Engine("Streaming engine"),
    Theme("Theme"),
    Updates("App updates"),
    Backup("Backup"),
}

@Composable
fun SettingsScreen(core: Core, onSection: (Section) -> Unit, onExit: () -> Unit) {
    val focusManager = LocalFocusManager.current
    var open by remember { mutableStateOf(SettingsMenu.Addons) }
    var enters by remember { mutableIntStateOf(0) }
    var paneFocused by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        focus.requestFocus()
    }
    Column(Modifier.fillMaxSize().slate()) {
        TopStrip(visible = true, current = Section.Settings, onSection = onSection, onExit = onExit)
        BackHandler(enabled = paneFocused) { focus.requestFocus() }
        Row(Modifier.fillMaxSize().padding(start = 64.dp, top = 16.dp, end = 48.dp, bottom = 48.dp)) {
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                Column(Modifier.width(IntrinsicSize.Max), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SettingsMenu.entries.forEach { menu ->
                        PillButton(
                            menu.label,
                            {
                                if (open == menu && menu != SettingsMenu.Addons) {
                                    focusManager.moveFocus(FocusDirection.Right)
                                } else {
                                    open = menu
                                    enters++
                                }
                            },
                            Modifier.fillMaxWidth().then(if (menu == open) Modifier.focusRequester(focus) else Modifier),
                            selected = open == menu,
                        )
                    }
                }
            }
            Box(Modifier.width(480.dp).fillMaxHeight().onFocusChanged { paneFocused = it.hasFocus }) {
                MetalPanel(Modifier.fillMaxSize()) {
                    BasicText(open.label, style = MachText.rowTitle, modifier = Modifier.padding(horizontal = 8.dp))
                    Box(Modifier.padding(top = 16.dp)) {
                        when (open) {
                            SettingsMenu.Addons -> AddonsScreen(core, enters)
                            SettingsMenu.Subtitles -> SubtitlesScreen(core)
                            SettingsMenu.Engine -> EngineScreen()
                            SettingsMenu.Theme -> ThemeChoices()
                            SettingsMenu.Updates -> UpdatesScreen()
                            SettingsMenu.Backup -> BackupScreen(core)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeChoices() {
    val app = LocalContext.current.applicationContext as MachTvApp
    val current = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        current.requestFocus()
    }
    SettingsPage(blocked = false) {
        AppTheme.entries.forEach { theme ->
            OutlineRow(
                onClick = { app.chooseTheme(theme) },
                modifier = if (theme == app.theme) Modifier.focusRequester(current) else Modifier,
            ) {
                BasicText(theme.label, style = MachText.rowTitle, modifier = Modifier.weight(1f))
                if (theme == app.theme) BasicText(CHECK_GLYPH, style = MachText.navGlyph)
            }
        }
    }
}
