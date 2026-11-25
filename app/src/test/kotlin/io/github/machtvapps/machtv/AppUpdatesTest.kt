package io.github.machtvapps.machtv

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdatesTest {
    @Test
    fun readsTagNotesAndTheApkAsset() {
        val release = Json.parseToJsonElement(
            """{"tag_name":"0.2.0","body":" Fixes \n","assets":[{"name":"other.apk","browser_download_url":"https://x/other"},{"name":"mach-tv-universal-release.apk","browser_download_url":"https://x/m.apk"}]}""",
        ).jsonObject
        assertEquals(Release("0.2.0", "Fixes", "https://x/m.apk"), parseRelease(release))
    }

    @Test
    fun aMissingBodyGivesNoNotes() {
        val release = Json.parseToJsonElement(
            """{"tag_name":"0.2.0","body":null,"assets":[{"name":"mach-tv-universal-release.apk","browser_download_url":"https://x/m.apk"}]}""",
        ).jsonObject
        assertEquals("", parseRelease(release).notes)
    }

    @Test
    fun comparesVersionsComponentByComponent() {
        assertTrue(isNewerVersion("0.10.0", "0.9.9"))
        assertTrue(isNewerVersion("0.1.1", "0.1.0"))
        assertTrue(isNewerVersion("0.1.0.1", "0.1.0"))
        assertFalse(isNewerVersion("0.1.0", "0.1.0"))
        assertFalse(isNewerVersion("0.1", "0.1.0"))
    }
}
