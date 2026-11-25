package io.github.machtvapps.machtv

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.ceil

class FocusShadow(val image: ImageBitmap, val pad: Int)

private val shadows = HashMap<List<Int>, FocusShadow>()

fun focusShadow(width: Int, height: Int, radius: Float, blur: Float): FocusShadow =
    shadows.getOrPut(listOf(width, height, radius.toInt(), blur.toInt())) {
        val pad = ceil(blur * 1.8f).toInt()
        val bitmap = Bitmap.createBitmap(width + 2 * pad, height + 2 * pad, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.BLACK
            maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL)
        }
        Canvas(bitmap).drawRoundRect(pad.toFloat(), pad.toFloat(), (pad + width).toFloat(), (pad + height).toFloat(), radius, radius, paint)
        bitmap.prepareToDraw()
        FocusShadow(bitmap.asImageBitmap(), pad)
    }
