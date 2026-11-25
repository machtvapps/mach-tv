package io.github.machtvapps.machtv

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val MetalText = Color(0xFFF2F3F5)
val MetalInk = Color(0xFF111316)

private val BodyStops = floatArrayOf(0f, 0.38f, 0.52f, 0.7f, 1f)
private val RestBody = listOf(Color(0xFF3A3D43), Color(0xFF2A2C30), Color(0xFF17181B), Color(0xFF212327), Color(0xFF2B2D32))
private val SelectedBody = listOf(Color(0xFF6A6E77), Color(0xFF4E5158), Color(0xFF303237), Color(0xFF41444A), Color(0xFF52555C))
private val FocusBody = listOf(Color.White, Color(0xFFC9CDD3), Color(0xFF8E939B), Color(0xFFDFE2E6), Color(0xFFF8F9FA))
private val ChromeStops = arrayOf(
    0f to Color(0xFF8B9099),
    0.125f to Color(0xFFF7F8FA),
    0.25f to Color(0xFF7C828B),
    0.375f to Color(0xFF5D626B),
    0.5f to Color(0xFFD9DDE2),
    0.625f to Color(0xFFFDFDFE),
    0.75f to Color(0xFF8B9099),
    0.875f to Color(0xFF4F545C),
    1f to Color(0xFF8B9099),
)
private val PanelBody = Color(0xD91A1C20)
private val PanelStreaks = arrayOf(
    0f to Color(0x29FFFFFF),
    0.22f to Color.Transparent,
    0.38f to Color(0x12FFFFFF),
    0.55f to Color.Transparent,
    0.78f to Color(0x1AFFFFFF),
    1f to Color.Transparent,
)
private val CardBody = listOf(Color(0x1AFFFFFF), Color(0x05FFFFFF))

fun markBrush(polished: Boolean): Brush =
    Brush.verticalGradient(*Array(BodyStops.size) { BodyStops[it] to (if (polished) FocusBody else SelectedBody)[it] })

fun chromeBrush(size: Size): Brush = Brush.sweepGradient(*ChromeStops, center = Offset(size.width / 2, size.height / 2))

fun DrawScope.chromeRim(brush: Brush, width: Float, radius: Float, alpha: Float = 1f) {
    drawRoundRect(
        brush,
        topLeft = Offset(width / 2, width / 2),
        size = Size(size.width - width, size.height - width),
        cornerRadius = CornerRadius((radius - width / 2).coerceAtLeast(0f)),
        style = Stroke(width),
        alpha = alpha,
    )
}

fun Modifier.metalBody(corner: Dp? = null, selected: Boolean = false, rest: Boolean = true, focus: () -> Float): Modifier = drawWithCache {
    val t = focus()
    val radius = corner?.toPx() ?: (size.height / 2)
    val from = if (!rest) FocusBody else if (selected) SelectedBody else RestBody
    val body = Brush.verticalGradient(*Array(BodyStops.size) { BodyStops[it] to lerp(from[it], FocusBody[it], t) })
    val chrome = chromeBrush(size)
    val alpha = if (rest) 1f else t
    val rim = 1.5.dp.toPx()
    onDrawBehind {
        if (alpha > 0f) {
            drawRoundRect(body, cornerRadius = CornerRadius(radius), alpha = alpha)
            chromeRim(chrome, rim, radius, alpha)
        }
    }
}

fun Modifier.metalCard(focused: Boolean, rest: Boolean = true, corner: Dp = 12.dp): Modifier = drawWithCache {
    val body = Brush.verticalGradient(CardBody)
    val chrome = chromeBrush(size)
    val radius = corner.toPx()
    val thin = 1.dp.toPx()
    val thick = 3.dp.toPx()
    onDrawBehind {
        if (rest || focused) drawRoundRect(body, cornerRadius = CornerRadius(radius))
        if (focused) chromeRim(chrome, thick, radius) else if (rest) chromeRim(chrome, thin, radius, 0.55f)
    }
}

fun Modifier.metalPanel(corner: Dp? = 24.dp): Modifier = drawWithCache {
    val radius = corner?.toPx() ?: (size.height / 2)
    val streaks = Brush.linearGradient(*PanelStreaks, start = Offset.Zero, end = Offset(size.width, size.height))
    val chrome = chromeBrush(size)
    val rim = 2.dp.toPx()
    onDrawBehind {
        drawRoundRect(PanelBody, cornerRadius = CornerRadius(radius))
        drawRoundRect(streaks, cornerRadius = CornerRadius(radius))
        chromeRim(chrome, rim, radius)
    }
}
