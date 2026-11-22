package io.github.terminalis.machtv

import android.content.Intent
import android.media.tv.TvContract
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PublishedChannelsTest {
    private val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as MachTvApp
    private val resolver = app.contentResolver

    private fun channels(): Map<String, Long> {
        val columns = arrayOf(TvContract.Channels._ID, TvContract.Channels.COLUMN_DISPLAY_NAME)
        val found = LinkedHashMap<String, Long>()
        resolver.query(TvContract.Channels.CONTENT_URI, columns, null, null, null)!!.use { cursor ->
            while (cursor.moveToNext()) found[cursor.getString(1)] = cursor.getLong(0)
        }
        return found
    }

    private fun published(channelId: Long): List<Pair<String, String>> {
        val columns = arrayOf(
            TvContract.PreviewPrograms.COLUMN_TITLE,
            TvContract.PreviewPrograms.COLUMN_INTENT_URI,
            TvContract.PreviewPrograms.COLUMN_WEIGHT,
        )
        val rows = mutableListOf<Triple<String, String, Int>>()
        resolver.query(TvContract.buildPreviewProgramsUriForChannel(channelId), columns, null, null, null)!!.use { cursor ->
            while (cursor.moveToNext()) rows += Triple(cursor.getString(0), cursor.getString(1), cursor.getInt(2))
        }
        return rows.sortedByDescending { it.third }.map { it.first to it.second }
    }

    private fun expected(channel: HomeChannel): List<Pair<String, String>> =
        channelPrograms(channel, app.core.continueWatching.value, app.core.ctx.value?.library.orEmpty()).map {
            it.title to detailsLink(it.type, it.id)
        }

    @Test
    fun homeScreenRowsMatchTheApp() {
        HomeChannel.entries.forEach { channel ->
            var rows = emptyList<Pair<String, String>>()
            val deadline = System.currentTimeMillis() + 10_000
            do {
                rows = channels()[channel.title]?.let(::published).orEmpty().map { (title, intentUri) ->
                    title to Intent.parseUri(intentUri, Intent.URI_INTENT_SCHEME).getStringExtra(DETAILS_EXTRA).orEmpty()
                }
                if (rows == expected(channel)) break
                Thread.sleep(250)
            } while (System.currentTimeMillis() < deadline)
            Log.i("PublishedChannelsTest", "${channel.title}: ${rows.size} titles, first ${rows.firstOrNull()?.first}")
            assertEquals(expected(channel), rows)
        }
        assertEquals(HomeChannel.entries.map { it.title }, channels().keys.toList())
    }
}
