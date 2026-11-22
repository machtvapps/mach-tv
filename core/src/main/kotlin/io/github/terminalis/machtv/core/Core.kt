package io.github.terminalis.machtv.core

import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class Core private constructor() {
    private val json = coreJson
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val ctxState = MutableStateFlow<CtxState?>(null)
    private val homeState = MutableStateFlow<BoardState?>(null)
    private val moviesState = MutableStateFlow<BoardState?>(null)
    private val seriesState = MutableStateFlow<BoardState?>(null)
    private val catalogState = MutableStateFlow<CatalogWithFiltersState?>(null)
    private val searchState = MutableStateFlow<BoardState?>(null)
    private val localSearchState = MutableStateFlow<LocalSearchState?>(null)
    private val libraryState = MutableStateFlow<LibraryState?>(null)
    private val addonDetailsState = MutableStateFlow<AddonDetailsState?>(null)
    private val metaDetailsState = MutableStateFlow<MetaDetailsState?>(null)
    private val playerState = MutableStateFlow<PlayerState?>(null)
    private val continueWatchingState = MutableStateFlow<ContinueWatchingState?>(null)
    private val coreEvents = MutableSharedFlow<CoreEvent>(extraBufferCapacity = 64)
    @Volatile private var ctxJson: JsonObject? = null
    private val boardVersions = mutableMapOf(Field.MOVIES to 0L, Field.SERIES to 0L)
    private lateinit var eventLoop: Job

    val ctx: StateFlow<CtxState?> = ctxState.asStateFlow()
    val home: StateFlow<BoardState?> = homeState.asStateFlow()
    val movies: StateFlow<BoardState?> = moviesState.asStateFlow()
    val series: StateFlow<BoardState?> = seriesState.asStateFlow()
    val catalog: StateFlow<CatalogWithFiltersState?> = catalogState.asStateFlow()
    val search: StateFlow<BoardState?> = searchState.asStateFlow()
    val localSearch: StateFlow<LocalSearchState?> = localSearchState.asStateFlow()
    val library: StateFlow<LibraryState?> = libraryState.asStateFlow()
    val addonDetails: StateFlow<AddonDetailsState?> = addonDetailsState.asStateFlow()
    val metaDetails: StateFlow<MetaDetailsState?> = metaDetailsState.asStateFlow()
    val player: StateFlow<PlayerState?> = playerState.asStateFlow()
    val continueWatching: StateFlow<ContinueWatchingState?> = continueWatchingState.asStateFlow()
    val events: SharedFlow<CoreEvent> = coreEvents.asSharedFlow()

    fun installedAddon(transportUrl: String): JsonObject? =
        ctxJson?.get("profile")?.jsonObject?.get("addons")?.jsonArray?.map { it.jsonObject }
            ?.firstOrNull { it["transportUrl"]?.jsonPrimitive?.content == transportUrl }

    fun profileSettings(): JsonObject? = ctxJson?.get("profile")?.jsonObject?.get("settings")?.jsonObject

    fun dispatch(action: Action, field: Field? = null) {
        NativeCore.dispatch(action.toJson().toString(), field?.key)
    }

    fun exportBackup(): String = NativeCore.exportBackup()

    suspend fun importBackup(json: String): BackupRestore {
        eventLoop.cancelAndJoin()
        val outcome = try {
            json.decodeImport()
        } finally {
            Field.entries.forEach(::refresh)
            startEventLoop()
        }
        val failed = outcome.missingAddons.filterNot { installAddon(it) }
        return BackupRestore(outcome.restored, outcome.missingAddons.size - failed.size, failed)
    }

    private fun String.decodeImport(): BackupImport = json.decodeFromString(NativeCore.importBackup(this))

    private suspend fun installAddon(url: String): Boolean {
        dispatch(Action.LoadAddonDetails(url), Field.ADDON_DETAILS)
        val content = withTimeoutOrNull(ADDON_INSTALL_MS) {
            addonDetails.first { it?.remoteAddon?.transportUrl == url && it.remoteAddon.content.type != "Loading" }
        }?.remoteAddon?.content
        val descriptor = content?.takeIf { it.type == "Ready" }?.content as? JsonObject ?: return false
        dispatch(Action.InstallAddon(descriptor))
        return withTimeoutOrNull(ADDON_INSTALL_MS) {
            ctx.first { state -> state?.profile?.addons.orEmpty().any { it.transportUrl == url } }
        } != null
    }

    suspend fun close() {
        eventLoop.cancelAndJoin()
        NativeCore.shutdown()
        scope.cancel()
    }

    private fun startEventLoop() {
        eventLoop = scope.launch {
            while (isActive) {
                val first = NativeCore.nextEvent(EVENT_WAIT_MS) ?: continue
                val batch = generateSequence(first) { NativeCore.nextEvent(0) }.map { json.parseToJsonElement(it).jsonObject }.toList()
                eventSteps(batch).forEach { step ->
                    when (step) {
                        is EventStep.Refresh -> refresh(step.field)
                        is EventStep.Emit -> coreEvents.emit(step.event)
                    }
                }
            }
        }
    }

    private fun refresh(field: Field) {
        fun state() = NativeCore.getState(field.key)
        when (field) {
            Field.CTX -> {
                val element = json.parseToJsonElement(state())
                ctxJson = element.jsonObject
                ctxState.value = json.decodeFromJsonElement<CtxState>(element)
            }
            Field.HOME -> homeState.value = json.decodeFromString<BoardState>(state())
            Field.MOVIES -> moviesState.value = boardWithDelta(field, moviesState.value)
            Field.SERIES -> seriesState.value = boardWithDelta(field, seriesState.value)
            Field.CATALOG -> catalogState.value = json.decodeFromString<CatalogWithFiltersState>(state())
            Field.SEARCH -> searchState.value = json.decodeFromString<BoardState>(state())
            Field.LOCAL_SEARCH -> localSearchState.value = json.decodeFromString<LocalSearchState>(state())
            Field.LIBRARY -> libraryState.value = json.decodeFromString<LibraryState>(state())
            Field.ADDON_DETAILS -> addonDetailsState.value = json.decodeFromString<AddonDetailsState>(state())
            Field.META_DETAILS -> metaDetailsState.value = json.decodeFromString<MetaDetailsState>(state())
            Field.PLAYER -> playerState.value = json.decodeFromString<PlayerState>(state())
            Field.CONTINUE_WATCHING_PREVIEW ->
                continueWatchingState.value = json.decodeFromString<ContinueWatchingState>(state())
        }
    }

    private fun boardWithDelta(field: Field, board: BoardState?): BoardState {
        val delta = json.decodeFromString<BoardDelta>(NativeCore.getBoardDelta(field.key, boardVersions.getValue(field)))
        boardVersions[field] = delta.version
        return board.patched(delta)
    }

    companion object {
        private const val EVENT_WAIT_MS = 250L
        private const val ADDON_INSTALL_MS = 30_000L

        fun start(dataDir: File): Core {
            NativeCore.initialize(dataDir.absolutePath)
            return Core().apply {
                Field.entries.forEach(::refresh)
                startEventLoop()
            }
        }
    }
}
