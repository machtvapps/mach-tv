package io.github.machtvapps.machtv

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.tv.TvContract
import androidx.annotation.RequiresApi
import io.github.machtvapps.machtv.core.ContinueWatchingState
import io.github.machtvapps.machtv.core.Core
import io.github.machtvapps.machtv.core.LibraryEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

enum class HomeChannel(val key: String, val title: String) {
    ContinueWatching("continue_watching", "Continue Watching"),
    MovieLibrary("movie_library", "Movie Library"),
    SeriesLibrary("series_library", "Series Library"),
}

data class ChannelProgram(
    val id: String,
    val type: String,
    val title: String,
    val poster: String,
    val position: Long = 0,
    val duration: Long = 0,
)

fun channelPrograms(channel: HomeChannel, watching: ContinueWatchingState?, library: List<LibraryEntry>): List<ChannelProgram> =
    when (channel) {
        HomeChannel.ContinueWatching -> watching?.items.orEmpty().mapNotNull { item ->
            item.poster?.let { ChannelProgram(item.id, item.type, item.name, it, item.state.timeOffset, item.state.duration) }
        }
        HomeChannel.MovieLibrary -> libraryPrograms(library, "movie")
        HomeChannel.SeriesLibrary -> libraryPrograms(library, "series")
    }

private fun libraryPrograms(library: List<LibraryEntry>, type: String): List<ChannelProgram> =
    library.filter { it.type == type }.mapNotNull { item -> item.poster?.let { ChannelProgram(item.id, item.type, item.name, it) } }

@RequiresApi(26)
class HomeScreenChannels(private val context: Context, private val core: Core) {
    private val resolver = context.contentResolver

    fun publishIn(scope: CoroutineScope) {
        scope.launch {
            HomeChannel.entries.map { it to channelId(it) }.forEach { (channel, channelId) ->
                launch {
                    combine(core.continueWatching, core.ctx) { watching, ctx -> channelPrograms(channel, watching, ctx?.library.orEmpty()) }
                        .distinctUntilChanged()
                        .collect { write(channelId, it) }
                }
            }
        }
    }

    private fun write(channelId: Long, programs: List<ChannelProgram>) {
        resolver.delete(TvContract.buildPreviewProgramsUriForChannel(channelId), null, null)
        resolver.bulkInsert(
            TvContract.PreviewPrograms.CONTENT_URI,
            programs.mapIndexed { index, program -> programValues(channelId, program, programs.size - index) }.toTypedArray(),
        )
    }

    private fun channelId(channel: HomeChannel): Long {
        val columns = arrayOf(TvContract.Channels._ID, TvContract.Channels.COLUMN_INTERNAL_PROVIDER_ID)
        resolver.query(TvContract.Channels.CONTENT_URI, columns, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getString(1) == channel.key) return cursor.getLong(0)
            }
        }
        val values = ContentValues().apply {
            put(TvContract.Channels.COLUMN_TYPE, TvContract.Channels.TYPE_PREVIEW)
            put(TvContract.Channels.COLUMN_DISPLAY_NAME, channel.title)
            put(TvContract.Channels.COLUMN_INTERNAL_PROVIDER_ID, channel.key)
            put(TvContract.Channels.COLUMN_APP_LINK_INTENT_URI, Intent(context, MainActivity::class.java).toUri(Intent.URI_INTENT_SCHEME))
        }
        val id = ContentUris.parseId(resolver.insert(TvContract.Channels.CONTENT_URI, values)!!)
        resolver.openOutputStream(TvContract.buildChannelLogoUri(id))!!.use {
            BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher).compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        TvContract.requestChannelBrowsable(context, id)
        return id
    }

    private fun programValues(channelId: Long, program: ChannelProgram, weight: Int) = ContentValues().apply {
        val link = Intent(context, MainActivity::class.java).putExtra(DETAILS_EXTRA, detailsLink(program.type, program.id))
        put(TvContract.PreviewPrograms.COLUMN_CHANNEL_ID, channelId)
        put(TvContract.PreviewPrograms.COLUMN_TYPE, programType(program.type))
        put(TvContract.PreviewPrograms.COLUMN_TITLE, program.title)
        put(TvContract.PreviewPrograms.COLUMN_POSTER_ART_URI, program.poster)
        put(TvContract.PreviewPrograms.COLUMN_POSTER_ART_ASPECT_RATIO, TvContract.PreviewPrograms.ASPECT_RATIO_2_3)
        put(TvContract.PreviewPrograms.COLUMN_INTENT_URI, link.toUri(Intent.URI_INTENT_SCHEME))
        put(TvContract.PreviewPrograms.COLUMN_INTERNAL_PROVIDER_ID, program.id)
        put(TvContract.PreviewPrograms.COLUMN_WEIGHT, weight)
        if (program.duration > 0) {
            put(TvContract.PreviewPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS, program.position)
            put(TvContract.PreviewPrograms.COLUMN_DURATION_MILLIS, program.duration)
        }
    }

    private fun programType(type: String): Int = when (type) {
        "movie" -> TvContract.PreviewPrograms.TYPE_MOVIE
        "series" -> TvContract.PreviewPrograms.TYPE_TV_SERIES
        else -> TvContract.PreviewPrograms.TYPE_CLIP
    }
}
