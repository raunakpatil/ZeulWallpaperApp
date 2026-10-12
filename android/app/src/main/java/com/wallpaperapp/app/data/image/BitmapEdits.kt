package com.wallpaperapp.app.data.image

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import com.wallpaperapp.app.domain.model.EditParams
import com.wallpaperapp.app.domain.model.NormalizedCrop
import kotlin.math.max
import kotlin.math.roundToInt

/** On-device image edits for Apply. Everything is plain Bitmap/Canvas, so it works on API 26+. */
object BitmapEdits {

    /**
     * Blur is applied to the whole source first, exactly as the editor previews it, then the
     * crop is scaled to [outW]×[outH] and dimmed. Same code path = what you see is what you get.
     */
    fun render(source: Bitmap, edits: EditParams, crop: NormalizedCrop, outW: Int, outH: Int): Bitmap {
        val blurred = blur(source, edits.blur)
        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val src = Rect(
            (crop.left * blurred.width).roundToInt(),
            (crop.top * blurred.height).roundToInt(),
            (crop.right * blurred.width).roundToInt(),
            (crop.bottom * blurred.height).roundToInt(),
        )
        canvas.drawBitmap(blurred, src, Rect(0, 0, outW, outH), Paint(Paint.FILTER_BITMAP_FLAG))
        dim(canvas, edits.dim)
        if (blurred !== source) blurred.recycle()
        return out
    }

    /**
     * Fast blur: shrink, box-blur the small copy, scale back with filtering. [strength] 0..1
     * maps to a radius relative to the image size, so previews and full-size renders match.
     */
    fun blur(source: Bitmap, strength: Float): Bitmap {
        if (strength <= 0f) return source
        val factor = 1f + strength * 23f
        val w = max(1, (source.width / factor).roundToInt())
        val h = max(1, (source.height / factor).roundToInt())
        val small = Bitmap.createScaledBitmap(source, w, h, true)
        val pixels = IntArray(w * h)
        small.getPixels(pixels, 0, w, 0, 0, w, h)
        BoxBlur.blur(pixels, w, h, radius = 2, passes = 3)
        small.setPixels(pixels, 0, w, 0, 0, w, h)
        return Bitmap.createScaledBitmap(small, source.width, source.height, true).also { small.recycle() }
    }

    fun dim(canvas: Canvas, amount: Float) {
        if (amount <= 0f) return
        canvas.drawColor(Color.argb((amount.coerceIn(0f, 1f) * 255).roundToInt(), 0, 0, 0))
    }
}

/** Separable box blur on ARGB pixels; several passes approximate a Gaussian. */
object BoxBlur {
    fun blur(pixels: IntArray, width: Int, height: Int, radius: Int, passes: Int) {
        if (radius <= 0 || width <= 0 || height <= 0) return
        val tmp = IntArray(pixels.size)
        repeat(passes) {
            pass(pixels, tmp, width, height, radius, horizontal = true)
            pass(tmp, pixels, width, height, radius, horizontal = false)
        }
    }

    private fun pass(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        val lines = if (horizontal) h else w
        val length = if (horizontal) w else h
        for (line in 0 until lines) {
            fun index(i: Int): Int {
                val p = i.coerceIn(0, length - 1) // clamp at the edges
                return if (horizontal) line * w + p else p * w + line
            }
            var a = 0; var red = 0; var g = 0; var b = 0
            for (i in -r..r) {
                val c = src[index(i)]
                a += c ushr 24; red += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; b += c and 0xFF
            }
            val size = 2 * r + 1
            for (i in 0 until length) {
                dst[index(i)] = ((a / size) shl 24) or ((red / size) shl 16) or ((g / size) shl 8) or (b / size)
                val out = src[index(i - r)]
                val inc = src[index(i + r + 1)]
                a += (inc ushr 24) - (out ushr 24)
                red += ((inc shr 16) and 0xFF) - ((out shr 16) and 0xFF)
                g += ((inc shr 8) and 0xFF) - ((out shr 8) and 0xFF)
                b += (inc and 0xFF) - (out and 0xFF)
            }
        }
    }
}
