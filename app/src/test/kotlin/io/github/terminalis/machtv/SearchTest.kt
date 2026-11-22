package io.github.terminalis.machtv

import io.github.terminalis.machtv.core.Searchable
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchTest {
    @Test
    fun suggestionsShowNameAndYear() {
        assertEquals("Pioneer One (2010–2011)", suggestionLabel(Searchable("tt1748166", "Pioneer One", "series", "2010–2011")))
        assertEquals("Pioneer One", suggestionLabel(Searchable("tt1748166", "Pioneer One", "series", null)))
        assertEquals("Pioneer One", suggestionLabel(Searchable("tt1748166", "Pioneer One", "series", " ")))
    }
}
