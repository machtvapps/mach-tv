package io.github.machtvapps.machtv

import android.app.Application
import android.content.Context
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import io.github.machtvapps.machtv.core.Core
import io.github.machtvapps.machtv.core.Engine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val DEFAULT_CACHE_BYTES = 5L * 1024 * 1024 * 1024
private const val THEME_KEY = "theme"

class MachTvApp : Application() {
    lateinit var core: Core
        private set
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    var releaseUrl = LATEST_RELEASE_URL
    private val engineLock = Mutex()
    private val settings by lazy { getSharedPreferences("app_settings", Context.MODE_PRIVATE) }
    var theme by mutableStateOf(AppTheme.Dark)
        private set
    val libraryFacts = mutableStateMapOf<String, MetaFacts>()
    lateinit var rows: BoardRowFlows
        private set

    override fun onCreate() {
        super.onCreate()
        theme = parseTheme(settings.getString(THEME_KEY, null))
        core = Core.start(filesDir)
        rows = BoardRowFlows(core, scope)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) HomeScreenChannels(this, core).publishIn(scope)
    }

    fun chooseTheme(choice: AppTheme) {
        theme = choice
        settings.edit().putString(THEME_KEY, choice.key).apply()
    }

    suspend fun startEngine(): String =
        engineLock.withLock {
            withContext(Dispatchers.IO) {
                val configured = File(filesDir, "engine/settings.json").exists()
                val url = Engine.start(this@MachTvApp)
                if (!configured) EngineSettingsClient(url).write(buildJsonObject { put("cacheSize", DEFAULT_CACHE_BYTES) })
                url
            }
        }

    fun stopEngine() {
        scope.launch { engineLock.withLock { Engine.stop() } }
    }
}
