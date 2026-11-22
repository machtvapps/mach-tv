package io.github.terminalis.machtv.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.jsonArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CoreBridgeTest {
    private lateinit var dataDir: File

    @Before
    fun createDataDir() {
        val cacheDir = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
        dataDir = File(cacheDir, "core-bridge-${System.nanoTime()}").apply { mkdirs() }
    }

    @After
    fun deleteDataDir() {
        dataDir.deleteRecursively()
    }

    @Test
    fun torrentioInstallsByUrl() = runBlocking {
        val core = Core.start(dataDir)
        try {
            core.installTorrentio()
            assertTrue(core.ctx.value!!.profile.addons.any { it.manifest.id == TORRENTIO_ID })
        } finally {
            core.close()
        }
    }

    @Test
    fun cinemetaPopularMoviesLoads() = runBlocking {
        val core = Core.start(dataDir)
        try {
            core.dispatch(Action.LoadCatalogsWithExtra(type = "movie"), Field.HOME)
            core.dispatch(Action.LoadCatalogRange(start = 0, end = 10), Field.HOME)
            val home = withTimeout(NETWORK_TIMEOUT_MS) {
                core.home.first { popularMovies(it)?.content?.type in SETTLED }
            }
            val content = popularMovies(home)!!.content!!
            assertEquals("Ready", content.type)
            assertTrue(content.content!!.jsonArray.isNotEmpty())
        } finally {
            core.close()
        }
    }

    @Test
    fun installTorrentioBeforeRestart() = runBlocking {
        restartDir.deleteRecursively()
        val core = Core.start(restartDir)
        try {
            core.installTorrentio()
        } finally {
            core.close()
        }
    }

    @Test
    fun addonsSurviveRestart() = runBlocking {
        val core = Core.start(restartDir)
        try {
            val ids = core.ctx.value!!.profile.addons.map { it.manifest.id }
            assertEquals(listOf(CINEMETA_ID, OPENSUBTITLES_ID, TORRENTIO_ID), ids)
        } finally {
            core.close()
            restartDir.deleteRecursively()
        }
    }

    private val restartDir: File
        get() = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "core-restart")

    private fun popularMovies(home: BoardState?): CatalogPage? =
        home?.catalogs?.firstNotNullOfOrNull { catalog ->
            catalog.firstOrNull()?.takeIf { page ->
                page.request.base == CINEMETA_URL &&
                    page.request.path.type == "movie" &&
                    page.request.path.id == "top"
            }
        }

    private companion object {
        const val CINEMETA_URL = "https://v3-cinemeta.strem.io/manifest.json"
        const val CINEMETA_ID = "com.linvo.cinemeta"
        const val OPENSUBTITLES_ID = "org.stremio.opensubtitlesv3"
    }
}
