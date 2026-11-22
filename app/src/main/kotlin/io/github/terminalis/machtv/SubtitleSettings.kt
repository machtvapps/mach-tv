package io.github.terminalis.machtv

import androidx.compose.ui.graphics.Color
import io.github.terminalis.machtv.core.Subtitle

data class Language(val code: String, val name: String, val aliases: List<String>)

val LANGUAGES = listOf(
    Language("eng", "English", listOf("en")),
    Language("spa", "Spanish", listOf("es")),
    Language("fre", "French", listOf("fr", "fra")),
    Language("ger", "German", listOf("de", "deu")),
    Language("ita", "Italian", listOf("it")),
    Language("por", "Portuguese", listOf("pt")),
    Language("dut", "Dutch", listOf("nl", "nld")),
    Language("swe", "Swedish", listOf("sv")),
    Language("nor", "Norwegian", listOf("no", "nb", "nob")),
    Language("dan", "Danish", listOf("da")),
    Language("fin", "Finnish", listOf("fi")),
    Language("pol", "Polish", listOf("pl")),
    Language("cze", "Czech", listOf("cs", "ces")),
    Language("hun", "Hungarian", listOf("hu")),
    Language("rum", "Romanian", listOf("ro", "ron")),
    Language("gre", "Greek", listOf("el", "ell")),
    Language("tur", "Turkish", listOf("tr")),
    Language("rus", "Russian", listOf("ru")),
    Language("ukr", "Ukrainian", listOf("uk")),
    Language("ara", "Arabic", listOf("ar")),
    Language("heb", "Hebrew", listOf("he", "iw")),
    Language("hin", "Hindi", listOf("hi")),
    Language("jpn", "Japanese", listOf("ja")),
    Language("kor", "Korean", listOf("ko")),
    Language("chi", "Chinese", listOf("zh", "zho")),
    Language("tha", "Thai", listOf("th")),
    Language("vie", "Vietnamese", listOf("vi")),
    Language("ind", "Indonesian", listOf("id", "in")),
)

enum class SubtitleSize(val percent: Int, val label: String) {
    Small(75, "Small"),
    Medium(100, "Medium"),
    Large(125, "Large"),
    ExtraLarge(150, "Extra large"),
}

enum class SubtitleColour(val value: String, val label: String) {
    White("#FFFFFFFF", "White"),
    Yellow("#FFFF00FF", "Yellow"),
}

enum class SubtitleBackground(val value: String, val label: String) {
    None("#00000000", "None"),
    Translucent("#00000099", "Translucent"),
    Black("#000000FF", "Black"),
}

private fun canonicalLanguage(code: String): String {
    val base = code.lowercase().substringBefore('-')
    return LANGUAGES.firstOrNull { it.code == base || base in it.aliases }?.code ?: base
}

fun sameLanguage(a: String?, b: String?): Boolean = a != null && b != null && canonicalLanguage(a) == canonicalLanguage(b)

fun languageName(code: String?, none: String): String =
    if (code == null) none else LANGUAGES.firstOrNull { it.code == canonicalLanguage(code) }?.name ?: code

fun parseRgba(value: String): Color? {
    if (value.length != 9 || value[0] != '#') return null
    val rgba = value.substring(1).toLongOrNull(16) ?: return null
    return Color(
        red = ((rgba shr 24) and 0xFF).toInt(),
        green = ((rgba shr 16) and 0xFF).toInt(),
        blue = ((rgba shr 8) and 0xFF).toInt(),
        alpha = (rgba and 0xFF).toInt(),
    )
}

fun autoSubtitle(language: String?, embedded: List<String?>, addon: List<Subtitle>): Subtitle? {
    if (language == null || embedded.any { sameLanguage(it, language) }) return null
    return addon.firstOrNull { sameLanguage(it.lang, language) }
}
