package io.github.machtvapps.machtv

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import io.github.machtvapps.machtv.core.Action
import io.github.machtvapps.machtv.core.Core
import io.github.machtvapps.machtv.core.ProfileSettings
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private enum class SubtitleRow { Audio, Language, Size, Colour, Background }

@Composable
fun SubtitlesScreen(core: Core) {
    val ctx by core.ctx.collectAsState()
    val settings = ctx?.profile?.settings ?: ProfileSettings()
    var open by remember { mutableStateOf<SubtitleRow?>(null) }
    var returnTo by remember { mutableStateOf(SubtitleRow.Audio) }
    val requesters = remember { SubtitleRow.entries.associateWith { FocusRequester() } }
    LaunchedEffect(open) {
        if (open == null) {
            withFrameNanos { }
            requesters.getValue(returnTo).requestFocus()
        }
    }
    fun show(row: SubtitleRow) {
        returnTo = row
        open = row
    }
    fun update(key: String, value: JsonElement) {
        core.profileSettings()?.let { current -> core.dispatch(Action.UpdateSettings(JsonObject(current + (key to value)))) }
        open = null
    }
    fun row(row: SubtitleRow) = Modifier.focusRequester(requesters.getValue(row))
    val names = LANGUAGES.map { it.name }
    Box(Modifier.fillMaxSize()) {
        SettingsPage(blocked = open != null) {
            SettingRow("Audio language", languageName(settings.audioLanguage, "Default"), { show(SubtitleRow.Audio) }, row(SubtitleRow.Audio))
            SettingRow("Subtitle language", languageName(settings.subtitlesLanguage, "Off"), { show(SubtitleRow.Language) }, row(SubtitleRow.Language))
            SettingRow(
                "Subtitle size",
                SubtitleSize.entries.firstOrNull { it.percent == settings.subtitlesSize }?.label ?: "${settings.subtitlesSize}%",
                { show(SubtitleRow.Size) },
                row(SubtitleRow.Size),
            )
            SettingRow(
                "Subtitle colour",
                SubtitleColour.entries.firstOrNull { it.value.equals(settings.subtitlesTextColor, ignoreCase = true) }?.label ?: settings.subtitlesTextColor,
                { show(SubtitleRow.Colour) },
                row(SubtitleRow.Colour),
            )
            SettingRow(
                "Subtitle background",
                SubtitleBackground.entries.firstOrNull { it.value.equals(settings.subtitlesBackgroundColor, ignoreCase = true) }?.label ?: settings.subtitlesBackgroundColor,
                { show(SubtitleRow.Background) },
                row(SubtitleRow.Background),
            )
        }
        when (open) {
            SubtitleRow.Audio -> ChoicePanel(
                "Audio language",
                names,
                LANGUAGES.indexOfFirst { sameLanguage(it.code, settings.audioLanguage) },
                { update("audioLanguage", JsonPrimitive(LANGUAGES[it].code)) },
                { open = null },
            )
            SubtitleRow.Language -> ChoicePanel(
                "Subtitle language",
                listOf("Off") + names,
                settings.subtitlesLanguage?.let { code -> LANGUAGES.indexOfFirst { sameLanguage(it.code, code) } + 1 } ?: 0,
                { update("subtitlesLanguage", if (it == 0) JsonNull else JsonPrimitive(LANGUAGES[it - 1].code)) },
                { open = null },
            )
            SubtitleRow.Size -> ChoicePanel(
                "Subtitle size",
                SubtitleSize.entries.map { it.label },
                SubtitleSize.entries.indexOfFirst { it.percent == settings.subtitlesSize },
                { update("subtitlesSize", JsonPrimitive(SubtitleSize.entries[it].percent)) },
                { open = null },
            )
            SubtitleRow.Colour -> ChoicePanel(
                "Subtitle colour",
                SubtitleColour.entries.map { it.label },
                SubtitleColour.entries.indexOfFirst { it.value.equals(settings.subtitlesTextColor, ignoreCase = true) },
                { update("subtitlesTextColor", JsonPrimitive(SubtitleColour.entries[it].value)) },
                { open = null },
            )
            SubtitleRow.Background -> ChoicePanel(
                "Subtitle background",
                SubtitleBackground.entries.map { it.label },
                SubtitleBackground.entries.indexOfFirst { it.value.equals(settings.subtitlesBackgroundColor, ignoreCase = true) },
                { update("subtitlesBackgroundColor", JsonPrimitive(SubtitleBackground.entries[it].value)) },
                { open = null },
            )
            null -> Unit
        }
    }
}
