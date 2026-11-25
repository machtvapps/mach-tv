package io.github.machtvapps.machtv

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
    Font(R.font.inter_extrabold, FontWeight.ExtraBold),
)

val CupertinoIcons = FontFamily(Font(R.font.cupertino_icons))

const val SEARCH_GLYPH = "\uF4A5"
const val CHECK_GLYPH = "\uF3FD"
const val CHEVRON_GLYPH = "\uF3D3"
const val PLAY_GLYPH = "\uF488"
const val PAUSE_GLYPH = "\uF478"
const val BACK_10_GLYPH = "\uF68F"
const val FORWARD_10_GLYPH = "\uF698"
const val SUBTITLES_GLYPH = "\uF5BE"
const val AUDIO_GLYPH = "\uF894"
const val NEXT_EPISODE_GLYPH = "\uF67F"
const val EPISODES_GLYPH = "\uF6E8"
const val STREAMS_GLYPH = "\uF50B"
const val INFO_GLYPH = "\uF44C"
const val EYE_GLYPH = "\uF424"
const val EYE_FILL_GLYPH = "\uF425"
const val BOOKMARK_GLYPH = "\uF3E9"
const val BOOKMARK_FILL_GLYPH = "\uF3EA"

val NavIdle = Color(0xB3FFFFFF)
val BadgeFill = Color(0xCC000000)
val TileSurface = Color(0xFF121212)
val PillInk = Color(0xDD000000)
val ProgressTrack = Color(0x3DFFFFFF)

fun blurPx(logicalBlur: Float, density: Float): Float {
    if (logicalBlur <= 0f) return 0f
    val sigmaPx = (logicalBlur * 0.57735f + 0.5f) * density
    return (sigmaPx - 0.5f) / 0.57735f
}

@Composable
fun homeTextShadow(): Shadow {
    val density = LocalDensity.current.density
    return Shadow(Color(0x8A000000), Offset(0f, 2f * density), blurPx(4f, density))
}

@Composable
fun heroTitleShadow(): Shadow {
    val density = LocalDensity.current.density
    return Shadow(Color(0xDD000000), Offset(0f, 2f * density), blurPx(10f, density))
}

@Composable
fun heroBodyShadow(): Shadow {
    val density = LocalDensity.current.density
    return Shadow(Color(0xDD000000), Offset(0f, 1f * density), blurPx(6f, density))
}

object MachText {
    val pill = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 16.sp, letterSpacing = (0.3f / 16f).em)
    val navGlyph = TextStyle(fontFamily = CupertinoIcons, fontSize = 20.sp, color = Color.White)
    val barGlyph = TextStyle(fontFamily = CupertinoIcons, fontSize = 22.sp, color = Color.White)
    val badge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 10.sp, color = Color.White, textAlign = TextAlign.Center)
    val navPill = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, letterSpacing = (0.3f / 15f).em)
    val cardTitle = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = (0.25f / 12f).em, color = Color.White)
    val cardSubtitle = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 10.sp, letterSpacing = (0.25f / 10f).em, color = Color(0xCCFFFFFF))
    val rowTitle = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = Color.White)
    val heroTitle = TextStyle(fontFamily = Inter, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, letterSpacing = (-0.4f / 34f).em, color = Color.White)
    val heroBody = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = (14f * 1.35f).sp, color = Color(0xD9FFFFFF))
    val panelTitle = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, color = Color.White)
    val body = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = (14f * 1.35f).sp, color = Color(0xD9FFFFFF))
    val field = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 18.sp, color = Color.White)
    val small = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = Color(0xB3FFFFFF))
    val subtitle = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 28.sp, lineHeight = 36.sp, color = Color.White, textAlign = TextAlign.Center)
}
