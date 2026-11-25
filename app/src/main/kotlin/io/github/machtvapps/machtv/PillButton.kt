package io.github.machtvapps.machtv

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private const val REST_SHADOW_ALPHA = 0x42 / 255f
private const val FOCUS_SHADOW_ALPHA = 0x61 / 255f

fun Modifier.pillShadow(rest: Float, corner: Dp? = null, focus: () -> Float): Modifier = drawBehind {
    val t = focus()
    val w = size.width.roundToInt()
    val h = size.height.roundToInt()
    val radius = corner?.toPx() ?: (size.height / 2f)
    if (rest > 0f && t < 1f) {
        val shadow = focusShadow(w, h, radius, blurPx(4f, density))
        drawImage(shadow.image, topLeft = Offset(-shadow.pad.toFloat(), -shadow.pad + 2.dp.toPx()), alpha = REST_SHADOW_ALPHA * rest * (1f - t))
    }
    if (t > 0f) {
        val shadow = focusShadow(w, h, radius, blurPx(8f, density))
        drawImage(shadow.image, topLeft = Offset(-shadow.pad.toFloat(), -shadow.pad + 3.dp.toPx()), alpha = FOCUS_SHADOW_ALPHA * t)
    }
}

@Composable
fun PillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, compact: Boolean = false, selected: Boolean = false, glyph: Boolean = false, onHold: (() -> Unit)? = null) {
    var focused by remember { mutableStateOf(false) }
    val hold = remember { HoldState() }
    val t by animateFloatAsState(if (focused) 1f else 0f, tween(FOCUS_MS, easing = OutCubic), label = "pill")
    val style = if (glyph) MachText.navGlyph else if (compact) MachText.navPill else MachText.pill
    Box(
        modifier
            .height(if (compact) 36.dp else 48.dp)
            .pillShadow(rest = 1f) { t }
            .metalBody(selected = selected) { t }
            .onFocusChanged { focused = it.isFocused }
            .holdPress(hold, onHold)
            .clickable(interactionSource = null, indication = null, onClick = hold.unlessHeld(onClick))
            .padding(horizontal = if (glyph) 14.dp else if (compact) 18.dp else 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text, style = style.copy(color = lerp(MetalText, MetalInk, t)))
    }
}
