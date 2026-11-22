package io.github.terminalis.machtv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle

private val SlateInk = Color(0xFF16181B)

class SlatePalette(val ink: Color, val focusInk: Color, val shadow: Boolean)

val WhiteInk = SlatePalette(Color.White, PillInk, shadow = true)
val DarkInk = SlatePalette(SlateInk, Color.White, shadow = false)

enum class AppTheme(val key: String, val label: String, val slate: Int, val windowColor: Int, val palette: SlatePalette) {
    Light("light", "Light", R.drawable.brushed_silver, 0xFFBBBDC1.toInt(), DarkInk),
    Dark("dark", "Dark", R.drawable.brushed_black, 0xFF121213.toInt(), WhiteInk),
}

fun parseTheme(key: String?): AppTheme = AppTheme.entries.firstOrNull { it.key == key } ?: AppTheme.Dark

val LocalAppTheme = staticCompositionLocalOf { AppTheme.Dark }
val LocalWatched = staticCompositionLocalOf { emptySet<String>() }

val LocalSlatePalette = staticCompositionLocalOf { WhiteInk }

@Composable
fun Modifier.slate(): Modifier = paint(painterResource(LocalAppTheme.current.slate), contentScale = ContentScale.Crop)

@Composable
fun TextStyle.onSlate(focus: Float = 0f, focused: Color = LocalSlatePalette.current.focusInk): TextStyle {
    val palette = LocalSlatePalette.current
    val shadow = homeTextShadow()
    return copy(
        color = lerp(palette.ink.copy(alpha = color.alpha), focused, focus),
        shadow = if (palette.shadow) shadow.copy(color = shadow.color.copy(alpha = shadow.color.alpha * (1f - focus))) else null,
    )
}
