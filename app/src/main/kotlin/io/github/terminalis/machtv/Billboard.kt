package io.github.terminalis.machtv

import android.content.Context
import android.graphics.Bitmap
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.view.KeyEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.res.ResourcesCompat
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import kotlinx.coroutines.delay

val BILLBOARD_HEIGHT = 312.dp
private val OUTLINE_GAP = 6.dp
private const val CYCLE_MS = 8_000L
private const val CORNER_DP = 16f

@Composable
fun Billboard(titles: List<CardItem>, inPlace: Boolean, onOpen: (CardItem) -> Unit, onFocused: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var index by remember(titles) { mutableIntStateOf(0) }
    var turns by remember(titles) { mutableIntStateOf(0) }
    var focused by remember { mutableStateOf(false) }
    val painter = remember { BillboardPainter(context) }
    val item = titles.getOrNull(index)
    LaunchedEffect(titles, turns) {
        if (titles.size > 1) {
            while (true) {
                delay(CYCLE_MS)
                index = (index + 1) % titles.size
            }
        }
    }
    LaunchedEffect(item, index, titles.size) {
        painter.show(item?.let { BillboardContent(it, loadBitmap(context, it.backdrop), loadBitmap(context, it.logo), index, titles.size) })
    }
    Box(
        modifier
            .fillMaxWidth()
            .height(BILLBOARD_HEIGHT + OUTLINE_GAP * 2)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .onKeyEvent { event ->
                val key = event.nativeKeyEvent
                val step = when (key.keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> -1
                    KeyEvent.KEYCODE_DPAD_RIGHT -> 1
                    else -> 0
                }
                if (step == 0 || titles.size < 2) return@onKeyEvent false
                if (key.action == KeyEvent.ACTION_DOWN) {
                    index = (index + step + titles.size) % titles.size
                    turns++
                }
                true
            }
            .clickable(interactionSource = null, indication = null) { item?.let(onOpen) }
            .drawBehind {
                val gap = OUTLINE_GAP.toPx()
                if (inPlace) drawRoundRect(TileSurface, topLeft = Offset(gap, gap), size = Size(size.width - 2 * gap, size.height - 2 * gap), cornerRadius = CornerRadius(CORNER_DP.dp.toPx()))
                if (focused) chromeRim(chromeBrush(size), 3.dp.toPx(), (CORNER_DP.dp + OUTLINE_GAP).toPx())
            },
    ) {
        AndroidView(
            factory = {
                SurfaceView(it).apply {
                    setZOrderOnTop(true)
                    holder.setFormat(PixelFormat.TRANSLUCENT)
                    holder.addCallback(painter)
                }
            },
            update = { it.visibility = if (inPlace) View.VISIBLE else View.INVISIBLE },
            modifier = Modifier.padding(OUTLINE_GAP).fillMaxSize(),
        )
    }
}

private suspend fun loadBitmap(context: Context, url: String?): Bitmap? {
    if (url == null) return null
    val result = SingletonImageLoader.get(context).execute(ImageRequest.Builder(context).data(url).build())
    return (result as? SuccessResult)?.image?.toBitmap()
}

private class BillboardContent(val item: CardItem, val art: Bitmap?, val logo: Bitmap?, val index: Int, val count: Int)

private class BillboardPainter(context: Context) : SurfaceHolder.Callback {
    private val density = context.resources.displayMetrics.density
    private val scaled = context.resources.displayMetrics.scaledDensity
    private val bold = ResourcesCompat.getFont(context, R.font.inter_extrabold)
    private val semibold = ResourcesCompat.getFont(context, R.font.inter_semibold)
    private val regular = ResourcesCompat.getFont(context, R.font.inter_regular)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private var holder: SurfaceHolder? = null
    private var content: BillboardContent? = null

    fun show(next: BillboardContent?) {
        content = next
        draw()
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        this.holder = holder
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        this.holder = holder
        draw()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        this.holder = null
    }

    private fun draw() {
        val surface = holder?.surface?.takeIf { it.isValid } ?: return
        val canvas = surface.lockHardwareCanvas()
        try {
            val w = canvas.width.toFloat()
            val h = canvas.height.toFloat()
            val corner = CORNER_DP * density
            canvas.drawColor(android.graphics.Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            canvas.save()
            canvas.clipPath(Path().apply { addRoundRect(0f, 0f, w, h, corner, corner, Path.Direction.CW) })
            canvas.drawColor(TileSurface.toArgb())
            val shown = content
            shown?.art?.let { drawCover(canvas, it, w, h) }
            paint.shader = LinearGradient(0f, 0f, w * 0.65f, 0f, 0xCC000000.toInt(), 0x00000000, Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, w, h, paint)
            paint.shader = LinearGradient(0f, h * 0.45f, 0f, h, 0x00000000, 0xB3000000.toInt(), Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, w, h, paint)
            paint.shader = null
            canvas.restore()
            if (shown != null) drawText(canvas, shown, w, h)
        } finally {
            surface.unlockCanvasAndPost(canvas)
        }
    }

    private fun drawCover(canvas: android.graphics.Canvas, bitmap: Bitmap, w: Float, h: Float) {
        val scale = maxOf(w / bitmap.width, h / bitmap.height)
        val srcW = (w / scale).toInt()
        val srcH = (h / scale).toInt()
        val left = (bitmap.width - srcW) / 2
        val top = (bitmap.height - srcH) / 4
        canvas.drawBitmap(bitmap, Rect(left, top, left + srcW, top + srcH), RectF(0f, 0f, w, h), paint)
    }

    private fun drawText(canvas: android.graphics.Canvas, shown: BillboardContent, w: Float, h: Float) {
        val pad = 40 * density
        val shadow = 0x8A000000.toInt()
        paint.setShadowLayer(4 * density, 0f, 2 * density, shadow)
        paint.color = android.graphics.Color.WHITE
        paint.typeface = semibold
        paint.textSize = 16 * scaled
        val factsBaseline = h - pad
        val facts = shown.item.facts
        if (facts.isNotEmpty()) canvas.drawText(facts, pad, factsBaseline, paint)
        shown.item.rating?.let { drawRating(canvas, it, pad + (if (facts.isEmpty()) 0f else paint.measureText(facts) + 16 * density), factsBaseline) }
        var titleBottom = factsBaseline - 28 * density
        shown.item.description?.let { text ->
            bodyPaint.typeface = regular
            bodyPaint.textSize = 15 * scaled
            bodyPaint.color = 0xE6FFFFFF.toInt()
            bodyPaint.setShadowLayer(4 * density, 0f, 2 * density, shadow)
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, bodyPaint, (w * 0.46f).toInt())
                .setMaxLines(3)
                .setEllipsize(TextUtils.TruncateAt.END)
                .setLineSpacing(0f, 1.12f)
                .build()
            canvas.save()
            canvas.translate(pad, titleBottom - layout.height)
            layout.draw(canvas)
            canvas.restore()
            titleBottom -= layout.height + 14 * density
        }
        val logo = shown.logo
        if (logo != null) {
            val maxW = w * 0.38f
            val maxH = h * 0.36f
            val scale = minOf(maxW / logo.width, maxH / logo.height)
            val lw = logo.width * scale
            val lh = logo.height * scale
            paint.clearShadowLayer()
            canvas.drawBitmap(logo, null, RectF(pad, titleBottom - lh, pad + lw, titleBottom), paint)
        } else {
            paint.typeface = bold
            paint.textSize = 34 * scaled
            canvas.drawText(shown.item.title, pad, titleBottom, paint)
        }
        paint.clearShadowLayer()
        if (shown.count > 1) {
            val r = 4 * density
            val gap = 14 * density
            var x = w - pad - (shown.count - 1) * gap
            for (i in 0 until shown.count) {
                paint.color = if (i == shown.index) android.graphics.Color.WHITE else 0x66FFFFFF
                canvas.drawCircle(x, factsBaseline - 5 * density, r, paint)
                x += gap
            }
        }
    }

    private fun drawRating(canvas: android.graphics.Canvas, rating: String, left: Float, baseline: Float) {
        paint.clearShadowLayer()
        paint.typeface = bold
        paint.textSize = 13 * scaled
        val padX = 6 * density
        val labelW = paint.measureText("IMDb") + padX * 2
        val valueW = paint.measureText(rating) + padX * 2
        val top = baseline - 17 * density
        val bottom = baseline + 6 * density
        val radius = 4 * density
        paint.color = 0xB3000000.toInt()
        canvas.drawRoundRect(left, top, left + labelW + valueW, bottom, radius, radius, paint)
        paint.color = 0xFFF5C518.toInt()
        canvas.drawRoundRect(left, top, left + labelW, bottom, radius, radius, paint)
        paint.color = android.graphics.Color.BLACK
        canvas.drawText("IMDb", left + padX, baseline, paint)
        paint.color = android.graphics.Color.WHITE
        canvas.drawText(rating, left + labelW + padX, baseline, paint)
    }
}
