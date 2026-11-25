package io.github.machtvapps.machtv

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp

val TOP_STRIP_HEIGHT = 56.dp

@Composable
fun TopStrip(
    visible: Boolean,
    current: Section,
    onSection: (Section) -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    atTop: Boolean = true,
    onReturn: () -> Unit = {},
) {
    val shown by animateFloatAsState(if (visible) 1f else 0f, tween(FOCUS_MS, easing = OutCubic), label = "strip")
    val home = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    BackHandler {
        when {
            !focused && !atTop -> onReturn()
            current != Section.Home -> onSection(Section.Home)
            else -> onExit()
        }
    }
    Box(
        modifier
            .fillMaxWidth()
            .height(TOP_STRIP_HEIGHT)
            .graphicsLayer {
                alpha = shown
                translationY = -(1f - shown) * TOP_STRIP_HEIGHT.toPx()
            }
            .padding(horizontal = 48.dp)
            .onFocusChanged { focused = it.hasFocus }
            .focusProperties { onEnter = { if (requestedFocusDirection != FocusDirection.Enter) home.requestFocus() } }
            .focusGroup(),
    ) {
        NavPill(
            text = SEARCH_GLYPH,
            selected = current == Section.Search,
            onClick = { onSection(Section.Search) },
            modifier = Modifier.align(Alignment.CenterStart),
            glyph = true,
        )
        Row(
            Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Section.entries.filter { it != Section.Search }.forEach { section ->
                NavPill(
                    text = section.label,
                    selected = section == current,
                    onClick = { onSection(section) },
                    modifier = if (section == Section.Home) Modifier.focusRequester(home) else Modifier,
                )
            }
        }
        LogoMark(Modifier.align(Alignment.CenterEnd).padding(end = 12.dp))
    }
}

@Composable
private fun LogoMark(modifier: Modifier = Modifier) {
    val silverTile = LocalSlatePalette.current.shadow
    Box(
        modifier.size(20.dp).pillShadow(rest = 1f, corner = 5.dp) { 0f }.metalBody(corner = 5.dp) { if (silverTile) 1f else 0f },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(12.dp, 7.9.dp)) {
            val unit = size.width / 46.25f
            val peak = Path().apply {
                moveTo(2.75f * unit, 27.5f * unit)
                lineTo(13f * unit, 2.75f * unit)
                lineTo(23f * unit, 16.25f * unit)
                lineTo(33.5f * unit, 6.25f * unit)
                lineTo(43.5f * unit, 27.75f * unit)
            }
            drawPath(peak, markBrush(polished = !silverTile), style = Stroke(5.5f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

@Composable
fun NavPill(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, glyph: Boolean = false) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val t by animateFloatAsState(if (focused) 1f else 0f, tween(FOCUS_MS, easing = OutCubic), label = "nav")
    val idle = (if (glyph) MachText.navGlyph else MachText.navPill).copy(color = NavIdle).onSlate(t)
    Box(
        modifier
            .height(36.dp)
            .pillShadow(rest = if (selected) 1f else 0f) { t }
            .metalBody(rest = selected) { t }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = if (glyph) 12.dp else 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text, style = idle.copy(color = lerp(if (selected) MetalText else idle.color, MetalInk, t)))
    }
}
