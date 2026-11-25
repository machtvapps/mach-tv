package io.github.machtvapps.machtv

import androidx.compose.runtime.mutableStateListOf
import io.github.machtvapps.machtv.core.ResourceRequest

sealed interface Screen {
    data object Home : Screen
    data object Movies : Screen
    data object Series : Screen
    data object Library : Screen
    data object Search : Screen
    data object Settings : Screen
    data class SeeAll(val request: ResourceRequest, val title: String) : Screen
    data class Details(val type: String, val id: String) : Screen
    data class Player(val playback: Playback) : Screen
}

enum class Section(val label: String, val screen: Screen) {
    Search("Search", Screen.Search),
    Home("Home", Screen.Home),
    Movies("Movies", Screen.Movies),
    Series("Series", Screen.Series),
    Library("Library", Screen.Library),
    Settings("Settings", Screen.Settings),
}

const val DETAILS_EXTRA = "details"

fun detailsLink(type: String, id: String): String = "$type:$id"

fun detailsScreen(link: String?): Screen.Details? {
    val parts = link?.split(':', limit = 2) ?: return null
    return if (parts.size == 2) Screen.Details(parts[0], parts[1]) else null
}

class Navigator(start: List<Screen>) {
    val stack = mutableStateListOf<Screen>().apply { addAll(start) }
    val top: Screen get() = stack.last()

    fun push(screen: Screen) {
        stack.add(screen)
    }

    fun pop() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    fun openSection(section: Section) {
        stack.clear()
        stack.add(Screen.Home)
        if (section.screen != Screen.Home) stack.add(section.screen)
    }

    fun leavePlayer() {
        if (top is Screen.Player) pop()
    }
}
