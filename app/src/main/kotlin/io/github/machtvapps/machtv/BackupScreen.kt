package io.github.machtvapps.machtv

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
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
import androidx.core.content.ContextCompat
import io.github.machtvapps.machtv.core.BackupRestore
import io.github.machtvapps.machtv.core.Core
import java.io.File
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val NOT_A_BACKUP = "not a Mach TV backup"

private fun count(n: Int, noun: String) = if (n == 1) "1 $noun" else "$n ${noun}s"

fun restoreMessage(restore: BackupRestore): String = buildString {
    append("Restored ${count(restore.titles, "title")} and ${count(restore.addons, "addon")}.")
    if (restore.failedAddons.isNotEmpty()) append(" Couldn't install: ${restore.failedAddons.joinToString()}")
}

fun backupFileName(date: LocalDate): String = "mach-tv-backup-$date.json"

private fun saveToDownloads(context: Context, name: String, json: String): String {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: throw IOException("Download is unavailable")
        try {
            resolver.openOutputStream(uri)!!.use { it.write(json.toByteArray()) }
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        return resolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null } ?: name
    }
    @Suppress("DEPRECATION")
    val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), name)
    file.parentFile!!.mkdirs()
    file.writeText(json)
    return file.name
}

@Composable
fun BackupScreen(core: Core) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val exportFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        exportFocus.requestFocus()
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        status = "Restoring…"
        scope.launch {
            val json = runCatching {
                withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)!!.use { it.bufferedReader().readText() } }
            }.getOrNull()
            status = if (json == null) {
                "Couldn't read that file."
            } else {
                runCatching { withContext(Dispatchers.IO) { core.importBackup(json) } }.fold(
                    { restoreMessage(it) },
                    { if (it.message?.contains(NOT_A_BACKUP) == true) "That file isn't a Mach TV backup." else "Couldn't restore the backup." },
                )
            }
            busy = false
        }
    }
    fun export() {
        busy = true
        status = "Saving…"
        scope.launch {
            status = runCatching {
                withContext(Dispatchers.IO) { saveToDownloads(context, backupFileName(LocalDate.now()), core.exportBackup()) }
            }.fold({ "Saved to Download as $it." }, { "Couldn't save the backup." })
            busy = false
        }
    }
    val storage = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) export() else status = "Allow storage access to save the backup in Download."
    }
    fun startExport() {
        val permission = Manifest.permission.WRITE_EXTERNAL_STORAGE
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        ) {
            storage.launch(permission)
        } else {
            export()
        }
    }
    SettingsPage(blocked = busy) {
        BasicText("A backup holds your addons and your library, with watched state and progress.", style = MachText.body.onSlate())
        Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            PillButton("Export", ::startExport, Modifier.focusRequester(exportFocus))
            PillButton("Import", { picker.launch(arrayOf("application/json")) })
        }
        status?.let { BasicText(it, style = MachText.rowTitle.onSlate(), modifier = Modifier.padding(top = 24.dp)) }
    }
}
