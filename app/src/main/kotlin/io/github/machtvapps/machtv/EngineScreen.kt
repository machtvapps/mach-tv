package io.github.machtvapps.machtv

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private enum class EngineRow { Cache, Dht, Proxy, Interface }

@Composable
fun EngineScreen() {
    val app = LocalContext.current.applicationContext as MachTvApp
    val scope = rememberCoroutineScope()
    var client by remember { mutableStateOf<EngineSettingsClient?>(null) }
    var settings by remember { mutableStateOf<EngineSettings?>(null) }
    var failed by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf<EngineRow?>(null) }
    var returnTo by remember { mutableStateOf(EngineRow.Cache) }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val requesters = remember { EngineRow.entries.associateWith { FocusRequester() } }
    val interfaces = remember { networkInterfaces() }
    LaunchedEffect(Unit) {
        runCatching {
            val engine = EngineSettingsClient(app.startEngine())
            val values = engineSettings(engine.read())
            host = values.proxyHost
            port = values.proxyPort.takeIf { it > 0 }?.toString().orEmpty()
            username = values.proxyUsername
            password = values.proxyPassword
            client = engine
            settings = values
        }.onFailure { failed = true }
    }
    DisposableEffect(Unit) {
        onDispose { app.stopEngine() }
    }
    LaunchedEffect(open, settings != null) {
        if (open == null && settings != null) {
            withFrameNanos { }
            requesters.getValue(returnTo).requestFocus()
        }
    }
    fun save(fields: JsonObject) {
        val engine = client ?: return
        open = null
        scope.launch {
            runCatching {
                engine.write(fields)
                settings = engineSettings(engine.read())
            }.onFailure { failed = true }
        }
    }
    fun show(row: EngineRow) {
        returnTo = row
        open = row
    }
    fun row(row: EngineRow) = Modifier.focusRequester(requesters.getValue(row))
    val current = settings
    Box(Modifier.fillMaxSize()) {
        SettingsPage(blocked = open != null) {
            when {
                failed -> BasicText("The engine didn't respond.", style = MachText.body.onSlate())
                current == null -> BasicText("Starting the engine…", style = MachText.body.onSlate())
                else -> {
                    SettingRow("Cache size", cacheLabel(current.cacheSize), { show(EngineRow.Cache) }, row(EngineRow.Cache))
                    SettingRow("DHT", if (current.dht) "On" else "Off", { show(EngineRow.Dht) }, row(EngineRow.Dht))
                    SettingRow("Proxy", current.proxy.label, { show(EngineRow.Proxy) }, row(EngineRow.Proxy))
                    if (current.proxy != ProxyKind.Off) {
                        ProxyField("Proxy host", host, { host = it }) { save(buildJsonObject { put("btProxyHost", host) }) }
                        ProxyField("Proxy port", port, { port = it.filter(Char::isDigit) }, KeyboardType.Number) {
                            save(buildJsonObject { put("btProxyPort", port.toIntOrNull() ?: 0) })
                        }
                        ProxyField("Proxy username", username, { username = it }) {
                            save(buildJsonObject {
                                put("btProxyUsername", username)
                                put("btProxyType", proxyType(current.proxy, username))
                            })
                        }
                        ProxyField("Proxy password", password, { password = it }, password = true) {
                            save(buildJsonObject { put("btProxyPassword", password) })
                        }
                    }
                    SettingRow("Network interface", current.outgoingInterfaces.ifEmpty { "Any" }, { show(EngineRow.Interface) }, row(EngineRow.Interface))
                }
            }
        }
        when (open) {
            EngineRow.Cache -> ChoicePanel(
                "Cache size",
                CacheSize.entries.map { it.label },
                CacheSize.entries.indexOfFirst { it.bytes == current?.cacheSize },
                { save(buildJsonObject { put("cacheSize", CacheSize.entries[it].bytes) }) },
                { open = null },
            )
            EngineRow.Dht -> ChoicePanel(
                "DHT",
                listOf("On", "Off"),
                if (current?.dht == false) 1 else 0,
                { save(buildJsonObject { put("btEnableDht", it == 0) }) },
                { open = null },
            )
            EngineRow.Proxy -> ChoicePanel(
                "Proxy",
                ProxyKind.entries.map { it.label },
                ProxyKind.entries.indexOf(current?.proxy ?: ProxyKind.Off),
                { save(buildJsonObject { put("btProxyType", proxyType(ProxyKind.entries[it], username)) }) },
                { open = null },
            )
            EngineRow.Interface -> ChoicePanel(
                "Network interface",
                listOf("Any") + interfaces.map { "${it.name} · ${it.address}" },
                if (current?.outgoingInterfaces.isNullOrEmpty()) 0 else interfaces.indexOfFirst { it.name == current?.outgoingInterfaces } + 1,
                { save(interfaceFields(if (it == 0) null else interfaces[it - 1].name)) },
                { open = null },
            )
            null -> Unit
        }
    }
}

@Composable
private fun ProxyField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    password: Boolean = false,
    onDone: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicText(label, style = MachText.rowTitle.onSlate(), modifier = Modifier.width(150.dp))
        TextInput(
            value,
            onValueChange,
            onDone,
            placeholder = "",
            keyboardType = keyboardType,
            modifier = Modifier.weight(1f).onFocusChanged { state ->
                if (focused && !state.hasFocus) onDone()
                focused = state.hasFocus
            },
            password = password,
        )
    }
}
