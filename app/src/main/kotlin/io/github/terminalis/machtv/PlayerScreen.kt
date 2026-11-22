package io.github.terminalis.machtv

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.compose.PlayerSurface
import io.github.terminalis.machtv.core.Action
import io.github.terminalis.machtv.core.Core
import io.github.terminalis.machtv.core.Field
import io.github.terminalis.machtv.core.ProfileSettings
import io.github.terminalis.machtv.core.Subtitle
import io.github.terminalis.machtv.core.meta
import io.github.terminalis.machtv.core.readySubtitles
import io.github.terminalis.machtv.core.resourcePath
import io.github.terminalis.machtv.core.resourceRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray

private const val PROGRESS_MS = 1_000L
private const val POSITION_POLL_MS = 500L
private const val PLAYER_LOAD_MS = 30_000L

private sealed interface PlayerPanel {
    data object Subtitles : PlayerPanel
    data object Audio : PlayerPanel
    data class Streams(val videoId: String) : PlayerPanel
    data object Episodes : PlayerPanel
    data object StreamInfo : PlayerPanel
}

private fun mediaItem(url: String, subtitle: Subtitle?): MediaItem {
    val builder = MediaItem.Builder().setUri(url)
    if (subtitle != null) {
        builder.setSubtitleConfigurations(
            listOf(
                MediaItem.SubtitleConfiguration.Builder(Uri.parse(subtitle.url))
                    .setId(ADDON_SUBTITLE_ID)
                    .setMimeType(subtitleMimeType(subtitle.url))
                    .setLanguage(subtitle.lang)
                    .setLabel(subtitle.lang)
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    .build(),
            ),
        )
    }
    return builder.build()
}

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(core: Core, initial: Playback) {
    val context = LocalContext.current
    val app = context.applicationContext as MachTvApp
    val scope = rememberCoroutineScope()
    val details by core.metaDetails.collectAsState()
    val playerState by core.player.collectAsState()
    val ctx by core.ctx.collectAsState()
    val settings = ctx?.profile?.settings ?: ProfileSettings()
    @Suppress("DEPRECATION")
    val exo = remember {
        ExoPlayer.Builder(context, LenientRenderersFactory(context))
            .setMediaSourceFactory(DefaultMediaSourceFactory(context).experimentalParseSubtitlesDuringExtraction(false))
            .build()
    }
    var playback by remember { mutableStateOf(initial) }
    var loads by remember { mutableIntStateOf(0) }
    var engine by remember { mutableStateOf<String?>(null) }
    var streamUrl by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    var exoState by remember { mutableIntStateOf(Player.STATE_IDLE) }
    var chip by remember { mutableStateOf(false) }
    var swarm by remember { mutableStateOf<SwarmStats?>(null) }
    var overlay by remember { mutableStateOf(false) }
    var panel by remember { mutableStateOf<PlayerPanel?>(null) }
    var inputTick by remember { mutableIntStateOf(0) }
    var position by remember { mutableLongStateOf(0L) }
    var buffered by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var paused by remember { mutableStateOf(false) }
    var tracks by remember { mutableStateOf(Tracks.EMPTY) }
    var cues by remember { mutableStateOf("") }
    var activeSubtitle by remember { mutableStateOf<Subtitle?>(null) }
    var autoSubtitleDone by remember { mutableStateOf(false) }
    val rootFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    val failureFocus = remember { FocusRequester() }
    val metaDetails = details?.takeIf { it.selected?.metaPath?.id == playback.metaId }

    fun seekBy(delta: Long) {
        val total = exo.duration.coerceAtLeast(0L)
        val target = (exo.currentPosition + delta).coerceIn(0L, total)
        exo.seekTo(target)
        position = target
        core.dispatch(Action.PlayerSeek(target, total), Field.PLAYER)
    }

    fun applyKey(action: KeyAction) {
        if (action.seekMs != 0L) seekBy(action.seekMs)
        if (action.togglePause) exo.playWhenReady = !exo.playWhenReady
        if (action.showOverlay) overlay = true
    }

    fun switchTo(next: Playback, saveProgress: Boolean) {
        if (saveProgress && exo.duration > 0) {
            core.dispatch(Action.PlayerTimeChanged(exo.currentPosition, exo.duration), Field.PLAYER)
        }
        playback = next
        loads++
    }

    fun nextEpisode() {
        val state = playerState ?: return
        val video = state.nextVideo ?: return
        core.dispatch(Action.PlayerNextVideo, Field.PLAYER)
        val stream = state.nextStream
        val streams = state.nextStreams
        if (stream != null && streams != null) {
            switchTo(
                Playback(
                    type = playback.type,
                    metaId = playback.metaId,
                    videoId = video.id,
                    name = playback.name,
                    title = episodeTitle(playback.name, video),
                    stream = stream,
                    streamRequest = resourceRequest(streams.request.base, resourcePath("stream", playback.type, video.id)),
                    metaRequest = playback.metaRequest,
                ),
                saveProgress = false,
            )
        } else {
            panel = PlayerPanel.Streams(video.id)
        }
    }

    fun attachSubtitle(subtitle: Subtitle) {
        val url = streamUrl ?: return
        activeSubtitle = subtitle
        exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setPreferredTextLanguage(subtitle.lang)
            .build()
        exo.setMediaItem(mediaItem(url, subtitle), exo.currentPosition)
        exo.prepare()
    }

    LaunchedEffect(Unit) {
        engine = app.startEngine()
    }

    LaunchedEffect(playback, engine, loads) {
        if (engine == null) return@LaunchedEffect
        error = null
        failure = null
        cues = ""
        activeSubtitle = null
        autoSubtitleDone = false
        core.dispatch(Action.Unload, Field.PLAYER)
        withTimeoutOrNull(PLAYER_LOAD_MS) { core.player.first { it?.stream == null } }
        core.dispatch(
            Action.LoadPlayer(
                playback.stream,
                playback.streamRequest,
                playback.metaRequest,
                resourcePath("subtitles", playback.type, playback.videoId),
            ),
            Field.PLAYER,
        )
        val state = withTimeoutOrNull(PLAYER_LOAD_MS) {
            core.player.filterNotNull().first { it.stream?.type == "Ready" || it.stream?.type == "Err" }
        }
        val urls = state?.stream?.takeIf { it.type == "Ready" }?.content?.jsonArray?.getOrNull(0) as? JsonObject
        val url = (urls?.get("streaming_url") as? JsonPrimitive)?.contentOrNull
            ?: streamText(playback.stream, "url").ifEmpty { null }
        if (url == null) {
            failure = UNPLAYABLE_MESSAGE
            return@LaunchedEffect
        }
        streamUrl = url
        val resume = state?.libraryItem?.state
            ?.takeIf { it.videoId == null || it.videoId == playback.videoId }
            ?.timeOffset ?: 0L
        exo.setMediaItem(mediaItem(url, null), resume)
        exo.prepare()
        exo.playWhenReady = true
    }

    LaunchedEffect(error) {
        if (error != null) {
            delay(MESSAGE_MS)
            error = null
        }
    }

    LaunchedEffect(settings.audioLanguage, settings.subtitlesLanguage) {
        exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
            .setPreferredAudioLanguage(settings.audioLanguage)
            .setPreferredTextLanguage(settings.subtitlesLanguage)
            .build()
    }

    val addonSubtitles = playerState?.subtitles.orEmpty().flatMap { it.readySubtitles() }
    LaunchedEffect(tracks, addonSubtitles, settings.subtitlesLanguage) {
        if (autoSubtitleDone || tracks.isEmpty()) return@LaunchedEffect
        val embedded = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
            .flatMap { group -> (0 until group.length).map { group.getTrackFormat(it).language } }
        val pick = autoSubtitle(settings.subtitlesLanguage, embedded, addonSubtitles) ?: return@LaunchedEffect
        autoSubtitleDone = true
        attachSubtitle(pick)
    }

    val loading = failure == null && (exoState == Player.STATE_IDLE || exoState == Player.STATE_BUFFERING)

    LaunchedEffect(loading, playback) {
        chip = false
        swarm = null
        if (!loading) return@LaunchedEffect
        delay(LOADING_CHIP_DELAY_MS)
        chip = true
        val infoHash = streamText(playback.stream, "infoHash")
        if (infoHash.isEmpty()) return@LaunchedEffect
        while (true) {
            engine?.let { url -> fetchSwarmStats(url, infoHash)?.let { swarm = it } }
            delay(SWARM_POLL_MS)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(PROGRESS_MS)
            if (exo.isPlaying && exo.duration > 0) {
                core.dispatch(Action.PlayerTimeChanged(exo.currentPosition, exo.duration), Field.PLAYER)
            }
        }
    }

    LaunchedEffect(overlay) {
        while (overlay) {
            position = exo.currentPosition
            buffered = exo.bufferedPosition
            duration = exo.duration.coerceAtLeast(0L)
            delay(POSITION_POLL_MS)
        }
    }

    LaunchedEffect(overlay, panel, inputTick) {
        if (overlay && panel == null) {
            delay(OVERLAY_IDLE_MS)
            overlay = false
        }
    }

    LaunchedEffect(overlay, panel, failure) {
        if (panel == null) {
            withFrameNanos { }
            when {
                failure != null -> failureFocus.requestFocus()
                overlay -> playFocus.requestFocus()
                else -> rootFocus.requestFocus()
            }
        }
    }

    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                exoState = playbackState
                if (playbackState == Player.STATE_ENDED) core.dispatch(Action.PlayerEnded, Field.PLAYER)
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                paused = !playWhenReady
                core.dispatch(Action.PlayerPausedChanged(!playWhenReady), Field.PLAYER)
            }

            override fun onTracksChanged(changed: Tracks) {
                tracks = changed
            }

            override fun onCues(cueGroup: CueGroup) {
                cues = cueGroup.cues.mapNotNull { it.text?.toString() }.joinToString("\n")
            }

            override fun onPlayerError(playbackError: PlaybackException) {
                failure = failureMessage(playbackError.errorCode)
                overlay = false
            }
        }
        exo.addListener(listener)
        onDispose {
            exo.removeListener(listener)
            if (exo.duration > 0) {
                core.dispatch(Action.PlayerTimeChanged(exo.currentPosition, exo.duration), Field.PLAYER)
            }
            exo.release()
            core.dispatch(Action.Unload, Field.PLAYER)
            app.stopEngine()
        }
    }

    BackHandler(enabled = overlay && panel == null) { overlay = false }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) inputTick++
                false
            },
    ) {
        PlayerSurface(player = exo, modifier = Modifier.fillMaxSize())
        if (cues.isNotEmpty()) {
            val scale = settings.subtitlesSize / 100f
            BasicText(
                cues,
                style = MachText.subtitle.copy(
                    fontSize = MachText.subtitle.fontSize * scale,
                    lineHeight = MachText.subtitle.lineHeight * scale,
                    color = parseRgba(settings.subtitlesTextColor) ?: Color.White,
                ),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 96.dp, end = 96.dp, bottom = if (overlay) 140.dp else 64.dp)
                    .background(parseRgba(settings.subtitlesBackgroundColor) ?: Color.Transparent)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .focusRequester(rootFocus)
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown || overlay || panel != null) return@onKeyEvent false
                    val action = hiddenOverlayKey(playerKey(event.key))
                    applyKey(action)
                    action.consumed
                }
                .focusable(),
        )
        if (overlay && panel == null) {
            PlayerOverlay(
                title = playback.title,
                position = position,
                buffered = buffered,
                duration = duration,
                paused = paused,
                series = playback.type == "series",
                nextEpisode = nextEpisodeVisible(position, duration, playerState?.nextVideo != null),
                playFocus = playFocus,
                onSeekKey = { key -> seekBarKey(key).also(::applyKey).consumed },
                onPlayPause = { exo.playWhenReady = !exo.playWhenReady },
                onSeek = ::seekBy,
                onSubtitles = { panel = PlayerPanel.Subtitles },
                onAudio = { panel = PlayerPanel.Audio },
                onNextEpisode = ::nextEpisode,
                onEpisodes = { panel = PlayerPanel.Episodes },
                onStreams = { panel = PlayerPanel.Streams(playback.videoId) },
                onStreamInfo = { panel = PlayerPanel.StreamInfo },
            )
        }
        if (chip) LoadingChip(loadingText(swarm), Modifier.align(Alignment.Center))
        failure?.let { message ->
            FailurePanel(message, failureFocus, { panel = PlayerPanel.Streams(playback.videoId) }, Modifier.align(Alignment.Center))
        }
        when (val open = panel) {
            PlayerPanel.Subtitles -> {
                val addonSelected = addonSubtitleSelected(tracks)
                val addons = ctx?.profile?.addons.orEmpty()
                val addonChoices = playerState?.subtitles.orEmpty().flatMap { list ->
                    val addon = addons.firstOrNull { it.transportUrl == list.request.base }?.manifest?.name ?: list.request.base
                    list.readySubtitles().map { subtitle -> addon to subtitle }
                }.mapIndexed { index, (addon, subtitle) ->
                    TrackChoice("addon:$index", "${subtitle.lang} · $addon", addonSelected && activeSubtitle?.url == subtitle.url) {
                        attachSubtitle(subtitle)
                    }
                }
                val off = TrackChoice("off", "Off", !textSelected(tracks)) {
                    exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                        .build()
                }
                TracksPanel("Subtitles", listOf(off) + exoTrackChoices(exo, tracks, C.TRACK_TYPE_TEXT) + addonChoices) { panel = null }
            }
            PlayerPanel.Audio -> TracksPanel("Audio", exoTrackChoices(exo, tracks, C.TRACK_TYPE_AUDIO)) { panel = null }
            PlayerPanel.StreamInfo -> StreamInfoPanel(
                video = exo.videoFormat,
                audio = exo.audioFormat,
                engine = engine,
                infoHash = streamText(playback.stream, "infoHash"),
                onClose = { panel = null },
            )
            is PlayerPanel.Streams -> StreamsPanel(
                core = core,
                type = playback.type,
                metaId = playback.metaId,
                videoId = open.videoId,
                addons = ctx?.profile?.addons.orEmpty(),
                onPick = {
                    panel = null
                    switchTo(it, saveProgress = open.videoId == playback.videoId)
                },
                onClose = { panel = null },
            )
            PlayerPanel.Episodes -> {
                val item = metaDetails?.meta()
                if (metaDetails != null && item != null) {
                    EpisodesPanel(
                        meta = item,
                        library = metaDetails.libraryItem,
                        watched = metaDetails.watchedVideos,
                        onPick = { video ->
                            panel = null
                            scope.launch {
                                runCatching { resolveVideo(core, playback.type, playback.metaId, video.id) }
                                    .onSuccess { switchTo(it, saveProgress = true) }
                                    .onFailure { error = it.message }
                            }
                        },
                        onClose = { panel = null },
                    )
                }
            }
            null -> Unit
        }
        error?.let {
            MetalPanel(Modifier.align(Alignment.Center)) { BasicText(it, style = MachText.body) }
        }
    }
}
