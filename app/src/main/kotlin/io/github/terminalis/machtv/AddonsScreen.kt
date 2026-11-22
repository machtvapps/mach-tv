package io.github.terminalis.machtv

import android.content.ClipboardManager
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.terminalis.machtv.core.Action
import io.github.terminalis.machtv.core.AddonDescriptor
import io.github.terminalis.machtv.core.Core
import io.github.terminalis.machtv.core.Field
import io.github.terminalis.machtv.core.Loadable
import io.github.terminalis.machtv.core.coreJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

@Composable
fun AddonsScreen(core: Core, enters: Int) {
    val ctx by core.ctx.collectAsState()
    val addonDetails by core.addonDetails.collectAsState()
    var url by remember { mutableStateOf("") }
    var lookedUp by remember { mutableStateOf<String?>(null) }
    var rejected by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<AddonDescriptor?>(null) }
    var configuring by remember { mutableStateOf<AddonDescriptor?>(null) }
    var replacing by remember { mutableStateOf<AddonDescriptor?>(null) }
    val firstAddon = remember { FocusRequester() }
    val context = LocalContext.current
    val clipboard = remember { context.getSystemService(ClipboardManager::class.java) }

    fun lookUp(replace: AddonDescriptor? = null) {
        replacing = replace
        val target = url.trim()
        if (target.isEmpty()) return
        rejected = try {
            core.dispatch(Action.LoadAddonDetails(target), Field.ADDON_DETAILS)
            false
        } catch (_: IllegalStateException) {
            true
        }
        lookedUp = target
    }

    LaunchedEffect(enters) {
        if (enters > 0) {
            withFrameNanos { }
            firstAddon.requestFocus()
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.focusGroup()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextInput(url, { url = it }, onDone = { lookUp() }, placeholder = "Install by URL", keyboardType = KeyboardType.Uri, modifier = Modifier.weight(1f))
                PillButton(
                    "Paste",
                    { clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.let { url = it.toString().trim() } },
                    Modifier.padding(start = 12.dp),
                )
                PillButton("Add", { lookUp() }, Modifier.padding(start = 12.dp))
            }
            if (lookedUp != null) {
                RemoteAddonCard(addonDetails?.remoteAddon?.content.takeUnless { rejected }, rejected) { descriptor ->
                    firstAddon.requestFocus()
                    replacing?.let { old -> core.installedAddon(old.transportUrl)?.let { core.dispatch(Action.UninstallAddon(it)) } }
                    replacing = null
                    core.dispatch(Action.InstallAddon(descriptor))
                    lookedUp = null
                    url = ""
                }
            }
            LazyColumn(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(ctx?.profile?.addons.orEmpty(), key = { _, addon -> addon.transportUrl }) { index, addon ->
                    OutlineRow(
                        onClick = { if (!addon.flags.protected || addon.manifest.behaviorHints.configurable) confirm = addon },
                        modifier = if (index == 0) Modifier.focusRequester(firstAddon) else Modifier,
                    ) {
                        Column(Modifier.weight(1f)) {
                            BasicText(addon.manifest.name, style = MachText.rowTitle)
                            addon.manifest.description?.let {
                                BasicText(it, style = MachText.small, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                            }
                        }
                        val actions = listOfNotNull(
                            if (addon.manifest.behaviorHints.configurable) "Configure" else null,
                            if (!addon.flags.protected) "Uninstall" else null,
                        )
                        if (actions.isNotEmpty()) BasicText(actions.joinToString(" · "), style = MachText.small, modifier = Modifier.padding(start = 16.dp))
                    }
                }
            }
        }
        confirm?.let { addon ->
            val close: () -> Unit = {
                confirm = null
                firstAddon.requestFocus()
            }
            val uninstall: () -> Unit = {
                core.installedAddon(addon.transportUrl)?.let { core.dispatch(Action.UninstallAddon(it)) }
                close()
            }
            if (addon.manifest.behaviorHints.configurable) {
                ConfirmDialog(
                    title = addon.manifest.name,
                    action = "Configure",
                    onConfirm = {
                        confirm = null
                        configuring = addon
                    },
                    onCancel = close,
                    second = if (addon.flags.protected) null else "Uninstall" to uninstall,
                )
            } else {
                ConfirmDialog(title = "Uninstall ${addon.manifest.name}?", action = "Uninstall", onConfirm = uninstall, onCancel = close)
            }
        }
        configuring?.let { addon ->
            AddonConfigure(
                url = addon.transportUrl.removeSuffix("manifest.json") + "configure",
                onInstall = { manifest ->
                    configuring = null
                    url = manifest
                    lookUp(addon)
                },
                onClose = {
                    configuring = null
                    firstAddon.requestFocus()
                },
            )
        }
    }
}

@Composable
private fun RemoteAddonCard(content: Loadable?, rejected: Boolean, onInstall: (JsonObject) -> Unit) {
    val install = remember { FocusRequester() }
    val descriptor = content?.takeIf { it.type == "Ready" }?.content as? JsonObject
    val addon = descriptor?.let { runCatching { coreJson.decodeFromJsonElement<AddonDescriptor>(it) }.getOrNull() }
    LaunchedEffect(addon != null) {
        if (addon != null) {
            withFrameNanos { }
            install.requestFocus()
        }
    }
    MetalPanel(Modifier.padding(top = 16.dp).fillMaxWidth()) {
        when {
            rejected -> BasicText("That isn't a full URL. Addon URLs start with https:// and end in /manifest.json", style = MachText.body)
            content?.type == "Ready" -> {
                if (descriptor != null && addon != null) {
                    BasicText(addon.manifest.name, style = MachText.rowTitle)
                    addon.manifest.description?.let { BasicText(it, style = MachText.body, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp)) }
                    PillButton("Install", { onInstall(descriptor) }, Modifier.padding(top = 12.dp).focusRequester(install))
                } else {
                    BasicText("That URL is not an addon manifest", style = MachText.body)
                }
            }
            content?.type == "Err" -> BasicText("Couldn't load that addon", style = MachText.body)
            else -> BasicText("Loading…", style = MachText.body)
        }
    }
}
