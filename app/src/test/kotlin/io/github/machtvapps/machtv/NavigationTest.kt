package io.github.machtvapps.machtv

import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationTest {
    @Test
    fun openSectionKeepsHomeUnderneath() {
        val navigator = Navigator(listOf(Screen.Home, Screen.Details("movie", "tt1254207")))
        navigator.openSection(Section.Movies)
        assertEquals(listOf(Screen.Home, Screen.Movies), navigator.stack.toList())
        navigator.openSection(Section.Library)
        assertEquals(listOf(Screen.Home, Screen.Library), navigator.stack.toList())
    }

    @Test
    fun openingHomeLeavesOnlyHome() {
        val navigator = Navigator(listOf(Screen.Home, Screen.Search))
        navigator.openSection(Section.Home)
        assertEquals(listOf(Screen.Home), navigator.stack.toList())
    }
}
