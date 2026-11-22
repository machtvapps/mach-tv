package io.github.terminalis.machtv

import android.view.KeyEvent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import coil3.compose.AsyncImage

val ROW_POSTER_WIDTH = 160.dp
val ROW_POSTER_HEIGHT = 240.dp
val ROW_WIDE_WIDTH = 284.dp
val ROW_WIDE_HEIGHT = 160.dp
private val POSTER_RADIUS = 8.dp
private val ImdbYellow = Color(0xFFF5C518)
private val BadgeShade = Color(0xB3000000)

@Composable
fun PosterCard(
    item: CardItem,
    onClick: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
    onRemove: (() -> Unit)? = null,
    wide: Boolean = false,
) {
    val art = if (wide) item.backdrop ?: item.poster else item.poster
    var focused by remember { mutableStateOf(false) }
    val t by animateFloatAsState(if (focused) 1f else 0f, tween(FOCUS_MS, easing = OutCubic), label = "poster")
    var asking by remember { mutableStateOf(false) }
    val hold = remember { HoldState() }
    val markWatched = movieWatchedToggle(item.id, item.type, item.title, item.poster)
    Box(
        modifier
            .size(if (wide) ROW_WIDE_WIDTH else ROW_POSTER_WIDTH, if (wide) ROW_WIDE_HEIGHT else ROW_POSTER_HEIGHT)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .holdPress(hold, if (onRemove != null) ({ asking = true }) else markWatched)
            .clickable(interactionSource = null, indication = null, onClick = hold.unlessHeld(onClick))
            .drawWithContent {
                drawContent()
                if (t > 0f) chromeRim(chromeBrush(size), 3.dp.toPx(), POSTER_RADIUS.toPx(), t)
                if (item.progress > 0f) {
                    val inset = 10.dp.toPx()
                    val bar = 4.dp.toPx()
                    val rim = 2.dp.toPx()
                    val track = Size(size.width - 2 * inset, bar)
                    val topLeft = Offset(inset, size.height - inset - bar)
                    drawRoundRect(
                        BadgeShade,
                        topLeft = Offset(topLeft.x - rim, topLeft.y - rim),
                        size = Size(track.width + 2 * rim, bar + 2 * rim),
                        cornerRadius = CornerRadius(bar / 2 + rim),
                    )
                    drawRoundRect(ProgressTrack, topLeft = topLeft, size = track, cornerRadius = CornerRadius(bar / 2))
                    drawRoundRect(Color.White, topLeft = topLeft, size = Size(track.width * item.progress, bar), cornerRadius = CornerRadius(bar / 2))
                }
            }
            .clip(RoundedCornerShape(POSTER_RADIUS))
            .background(TileSurface),
    ) {
        if (art == null) {
            BasicText(
                item.title,
                style = MachText.cardTitle.copy(textAlign = TextAlign.Center),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.Center).padding(12.dp),
            )
        } else {
            AsyncImage(model = art, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            if (wide) {
                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, BadgeShade)))
                        .padding(start = 12.dp, end = 12.dp, top = 32.dp, bottom = 22.dp),
                ) {
                    BasicText(item.title, style = MachText.rowTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (item.subtitle.isNotEmpty()) {
                        BasicText(item.subtitle, style = MachText.small.copy(color = Color.White), maxLines = 1, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
        }
        item.rating?.let { RatingBadge(it, Modifier.align(Alignment.TopStart).padding(8.dp)) }
        if (!wide && item.id in LocalWatched.current) WatchedBadge(Modifier.align(Alignment.TopEnd).padding(8.dp))
        if (asking && onRemove != null) {
            RemoveTooltip(
                onRemove = {
                    asking = false
                    onRemove()
                },
                onDismiss = { asking = false },
            )
        }
    }
}

@Composable
fun SeeAllCard(onClick: () -> Unit, onFocused: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier
            .size(ROW_POSTER_WIDTH, ROW_POSTER_HEIGHT)
            .metalCard(focused, corner = POSTER_RADIUS)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .clickable(interactionSource = null, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BasicText(EYE_GLYPH, style = MachText.navGlyph.copy(fontSize = 36.sp))
            BasicText("Show all", style = MachText.rowTitle, modifier = Modifier.padding(top = 10.dp))
        }
    }
}

private class PosterFoot(private val gap: Int) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset =
        IntOffset(anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2, anchorBounds.bottom - gap - popupContentSize.height)
}

@Composable
private fun RemoveTooltip(onRemove: () -> Unit, onDismiss: () -> Unit) {
    val focus = remember { FocusRequester() }
    val gap = with(LocalDensity.current) { 24.dp.roundToPx() }
    var pressed by remember { mutableStateOf(false) }
    Popup(popupPositionProvider = remember(gap) { PosterFoot(gap) }, onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        LaunchedEffect(Unit) {
            withFrameNanos { }
            focus.requestFocus()
        }
        BasicText(
            "Dismiss",
            style = MachText.navPill.copy(color = MetalInk),
            modifier = Modifier
                .focusRequester(focus)
                .onPreviewKeyEvent { event ->
                    val key = event.nativeKeyEvent
                    when {
                        key.keyCode == KeyEvent.KEYCODE_BACK -> return@onPreviewKeyEvent false
                        key.keyCode !in OkKeys -> if (key.action == KeyEvent.ACTION_DOWN) onDismiss()
                        key.action == KeyEvent.ACTION_DOWN && key.repeatCount == 0 -> pressed = true
                        key.action == KeyEvent.ACTION_UP && pressed -> onRemove()
                    }
                    true
                }
                .focusable()
                .metalBody(rest = false) { 1f }
                .padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

@Composable
fun WatchedBadge(modifier: Modifier = Modifier) {
    BasicText(
        EYE_FILL_GLYPH,
        style = MachText.navGlyph.copy(fontSize = 14.sp, color = Color.White),
        modifier = modifier.clip(RoundedCornerShape(4.dp)).background(BadgeShade).padding(horizontal = 5.dp, vertical = 2.dp),
    )
}

@Composable
fun RatingBadge(rating: String, modifier: Modifier = Modifier) {
    Row(modifier.clip(RoundedCornerShape(4.dp)).background(BadgeShade), verticalAlignment = Alignment.CenterVertically) {
        BasicText(
            "IMDb",
            style = MachText.badge.copy(color = Color.Black),
            modifier = Modifier.background(ImdbYellow).padding(horizontal = 4.dp, vertical = 2.dp),
        )
        BasicText(rating, style = MachText.badge, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
    }
}
