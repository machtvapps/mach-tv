package io.github.machtvapps.machtv

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: Release) : UpdateState
    data object Downloading : UpdateState
    data object NeedsPermission : UpdateState
    data class Failed(val message: String) : UpdateState
}

@Composable
fun UpdatesScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as MachTvApp
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<UpdateState>(UpdateState.Idle) }
    var release by remember { mutableStateOf<Release?>(null) }
    val check = remember { FocusRequester() }
    val download = remember { FocusRequester() }
    val allow = remember { FocusRequester() }
    LaunchedEffect(state == UpdateState.NeedsPermission) {
        if (state == UpdateState.NeedsPermission) {
            withFrameNanos { }
            allow.requestFocus()
        }
    }
    LaunchedEffect(release) {
        withFrameNanos { }
        if (release != null) download.requestFocus() else check.requestFocus()
    }
    fun checkNow() {
        state = UpdateState.Checking
        release = null
        scope.launch {
            runCatching { fetchRelease(app.releaseUrl) }
                .onSuccess {
                    if (isNewerVersion(it.version, BuildConfig.VERSION_NAME)) {
                        release = it
                        state = UpdateState.Available(it)
                    } else {
                        state = UpdateState.UpToDate
                    }
                }
                .onFailure { state = UpdateState.Failed("Couldn't check for updates.") }
        }
    }
    fun install(update: Release) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            state = UpdateState.NeedsPermission
            return
        }
        state = UpdateState.Downloading
        scope.launch {
            runCatching { downloadApk(update, context.cacheDir) }
                .onSuccess {
                    state = UpdateState.Available(update)
                    context.startActivity(installIntent(context, it))
                }
                .onFailure { state = UpdateState.Failed("Couldn't download the update.") }
        }
    }
    val message = when (val current = state) {
        UpdateState.Idle -> null
        UpdateState.Checking -> "Checking…"
        UpdateState.UpToDate -> "Mach TV is up to date."
        is UpdateState.Available -> "Version ${current.release.version} is available."
        UpdateState.Downloading -> "Downloading…"
        UpdateState.NeedsPermission ->
            "Mach TV needs permission to install apps. Android closes Mach TV when you allow it: reopen Mach TV and choose Download and install again."
        is UpdateState.Failed -> current.message
    }
    SettingsPage(blocked = false) {
        BasicText("Installed version ${BuildConfig.VERSION_NAME}", style = MachText.body.onSlate())
        PillButton("Check for updates", ::checkNow, Modifier.padding(top = 16.dp).focusRequester(check))
        message?.let { BasicText(it, style = MachText.rowTitle.onSlate(), modifier = Modifier.padding(top = 24.dp)) }
        release?.let { update ->
            if (update.notes.isNotEmpty()) BasicText(update.notes, style = MachText.body.onSlate(), modifier = Modifier.padding(top = 12.dp))
            PillButton("Download and install", { install(update) }, Modifier.padding(top = 16.dp).focusRequester(download))
        }
        if (state == UpdateState.NeedsPermission) {
            PillButton(
                "Allow installs",
                { context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))) },
                Modifier.padding(top = 16.dp).focusRequester(allow),
            )
        }
    }
}
