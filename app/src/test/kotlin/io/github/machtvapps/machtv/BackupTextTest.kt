package io.github.machtvapps.machtv

import io.github.machtvapps.machtv.core.BackupRestore
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupTextTest {
    @Test
    fun restoreMessages() {
        assertEquals("Restored 2 titles and 1 addon.", restoreMessage(BackupRestore(2, 1, emptyList())))
        assertEquals("Restored 0 titles and 0 addons.", restoreMessage(BackupRestore(0, 0, emptyList())))
        assertEquals(
            "Restored 1 title and 0 addons. Couldn't install: https://x/manifest.json",
            restoreMessage(BackupRestore(1, 0, listOf("https://x/manifest.json"))),
        )
    }

    @Test
    fun fileNameCarriesTheDate() {
        assertEquals("mach-tv-backup-2026-11-13.json", backupFileName(LocalDate.of(2026, 11, 13)))
    }
}
