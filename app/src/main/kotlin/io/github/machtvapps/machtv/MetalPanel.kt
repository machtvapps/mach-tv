package io.github.machtvapps.machtv

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp

@Composable
fun MetalPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.metalPanel().padding(24.dp)) {
        CompositionLocalProvider(LocalSlatePalette provides WhiteInk) { content() }
    }
}

@Composable
fun FocusRow(onClick: () -> Unit, modifier: Modifier = Modifier, polished: Boolean = false, content: @Composable RowScope.() -> Unit) {
    MetalRow(onClick, modifier, rest = false, polished = polished, content = content)
}

@Composable
fun OutlineRow(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    MetalRow(onClick, modifier, rest = true, polished = true, content = content)
}

private fun Modifier.inkTint(focus: () -> Float): Modifier = drawWithContent {
    val t = focus()
    if (t == 0f) {
        drawContent()
    } else {
        drawIntoCanvas { canvas ->
            canvas.saveLayer(Rect(Offset.Zero, size), Paint().apply { colorFilter = ColorFilter.tint(lerp(MetalText, MetalInk, t), BlendMode.SrcIn) })
            drawContent()
            canvas.restore()
        }
    }
}

@Composable
private fun MetalRow(onClick: () -> Unit, modifier: Modifier, rest: Boolean, polished: Boolean, content: @Composable RowScope.() -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val t by animateFloatAsState(if (focused) 1f else 0f, tween(FOCUS_MS, easing = OutCubic), label = "row")
    Row(
        modifier
            .fillMaxWidth()
            .then(if (polished) Modifier.metalBody(corner = 12.dp, rest = rest) { t } else Modifier.metalCard(focused, rest))
            .onFocusChanged { focused = it.isFocused }
            .clickable(interactionSource = null, indication = null, onClick = onClick)
            .then(if (polished) Modifier.inkTint { t } else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}
