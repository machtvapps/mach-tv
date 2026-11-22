package io.github.terminalis.machtv

import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import io.github.terminalis.machtv.core.Core
import io.github.terminalis.machtv.core.ResourceRequest

class MainActivity : ComponentActivity() {
    private lateinit var navigator: Navigator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as MachTvApp
        val core = app.core
        navigator = Navigator(listOf(Screen.Home) + listOfNotNull(detailsScreen(intent.getStringExtra(DETAILS_EXTRA))))
        if (BuildConfig.DEBUG) intent.getStringExtra("update_url")?.let { app.releaseUrl = it }
        window.setBackgroundDrawable(ColorDrawable(app.theme.windowColor))
        setContent {
            val ctx by core.ctx.collectAsState()
            val watched = remember(ctx?.watched) { ctx?.watched.orEmpty().toSet() }
            CompositionLocalProvider(LocalAppTheme provides app.theme, LocalWatched provides watched) {
                Root(core, navigator)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        navigator.leavePlayer()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        detailsScreen(intent.getStringExtra(DETAILS_EXTRA))?.let(navigator::push)
    }
}

@Composable
fun Root(core: Core, navigator: Navigator) {
    val saved = rememberSaveableStateHolder()
    val activity = LocalActivity.current
    var exiting by remember { mutableStateOf(false) }
    val onExit = { exiting = true }
    BackHandler(enabled = navigator.stack.size > 1) { navigator.pop() }
    val onDetails: (String, String) -> Unit = { type, id -> navigator.push(Screen.Details(type, id)) }
    val onPlay: (Playback) -> Unit = { navigator.push(Screen.Player(it)) }
    val onSection: (Section) -> Unit = navigator::openSection
    val onSeeAll: (ResourceRequest, String) -> Unit = { request, title -> navigator.push(Screen.SeeAll(request, title)) }
    val screen = navigator.top
    val palette = if (screen is Screen.Details || screen is Screen.Player) WhiteInk else LocalAppTheme.current.palette
    CompositionLocalProvider(LocalSlatePalette provides palette) {
        when (screen) {
            Screen.Home -> saved.SaveableStateProvider("home") { HomeScreen(core, onDetails, onSection, onExit, onSeeAll) }
            Screen.Movies -> saved.SaveableStateProvider("movies") { TypeBoardScreen(core, "movie", onDetails, onSection, onExit) }
            Screen.Series -> saved.SaveableStateProvider("series") { TypeBoardScreen(core, "series", onDetails, onSection, onExit) }
            Screen.Library -> saved.SaveableStateProvider("library") { LibraryScreen(core, onDetails, onSection, onExit) }
            Screen.Search -> saved.SaveableStateProvider("search") { SearchScreen(core, onDetails, onSection, onExit, onSeeAll) }
            is Screen.SeeAll -> saved.SaveableStateProvider("see-all|${rowKey(screen.request)}") {
                SeeAllScreen(core, screen.request, screen.title, onDetails)
            }
            is Screen.Details -> DetailsScreen(core, screen.type, screen.id, onPlay = onPlay)
            is Screen.Player -> PlayerScreen(core, screen.playback)
            Screen.Settings -> saved.SaveableStateProvider("settings") { SettingsScreen(core, onSection, onExit) }
        }
    }
    if (exiting) {
        ConfirmDialog(title = "Exit Mach TV?", action = "Exit", onConfirm = { activity?.finish() }, onCancel = { exiting = false })
    }
}
