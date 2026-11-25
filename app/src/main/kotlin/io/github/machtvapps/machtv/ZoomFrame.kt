package io.github.machtvapps.machtv

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private const val SHADOW_ALPHA = 0x99 / 255f
private const val SHADOW_BLUR_DP = 28f

@Composable
fun ZoomFrame(
    width: Dp,
    height: Dp,
    radius: Dp,
    zoom: Float,
    lift: Dp,
    pressedScale: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit = {},
    content: @Composable (focus: () -> Float) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val pressed by interaction.collectIsPressedAsState()
    val focus by animateFloatAsState(if (focused) 1f else 0f, tween(FOCUS_MS, easing = OutCubic), label = "focus")
    val scale by animateFloatAsState(
        when {
            pressed -> pressedScale
            focused -> zoom
            else -> 1f
        },
        tween(FOCUS_MS, easing = OutCubic),
        label = "scale",
    )
    val raised by animateFloatAsState(if (focused && !pressed) 1f else 0f, tween(FOCUS_MS, easing = OutCubic), label = "lift")
    val density = LocalDensity.current
    val shadow = remember(width, height, radius, density) {
        with(density) { focusShadow(width.roundToPx(), height.roundToPx(), radius.toPx(), blurPx(SHADOW_BLUR_DP, density.density)) }
    }
    Box(
        modifier
            .size(width, height)
            .onFocusChanged { if (it.isFocused) onFocused() }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationY = -lift.toPx() * raised
                }
                .drawBehind {
                    if (focus > 0f) {
                        drawImage(
                            shadow.image,
                            topLeft = Offset(-shadow.pad.toFloat(), -shadow.pad + 14.dp.toPx() * focus),
                            alpha = SHADOW_ALPHA * focus,
                        )
                    }
                }
                .clip(RoundedCornerShape(radius)),
        ) {
            content { focus }
        }
    }
}
