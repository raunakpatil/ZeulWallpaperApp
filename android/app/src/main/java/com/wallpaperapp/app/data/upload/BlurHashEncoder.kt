package com.wallpaperapp.app.data.upload

import android.graphics.Bitmap
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.withSign

/** BlurHash encoder (https://blurha.sh), same output as the `blurhash` npm package the dashboard uses. */
object BlurHashEncoder {
    private const val CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz#$%*+,-.:;=?@[]^_{|}~"

    fun encode(bitmap: Bitmap, componentsX: Int, componentsY: Int): String {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h).also { bitmap.getPixels(it, 0, w, 0, 0, w, h) }
        return encode(pixels, w, h, componentsX, componentsY)
    }

    fun encode(pixels: IntArray, width: Int, height: Int, componentsX: Int, componentsY: Int): String {
        require(componentsX in 1..9 && componentsY in 1..9)
        val factors = Array(componentsX * componentsY) { DoubleArray(3) }
        for (j in 0 until componentsY) for (i in 0 until componentsX) {
            val norm = if (i == 0 && j == 0) 1.0 else 2.0
            var r = 0.0; var g = 0.0; var b = 0.0
            for (y in 0 until height) {
                val cy = cos(PI * j * y / height)
                for (x in 0 until width) {
                    val basis = norm * cos(PI * i * x / width) * cy
                    val p = pixels[y * width + x]
                    r += basis * srgbToLinear((p shr 16) and 0xFF)
                    g += basis * srgbToLinear((p shr 8) and 0xFF)
                    b += basis * srgbToLinear(p and 0xFF)
                }
            }
            val scale = 1.0 / (width * height)
            factors[j * componentsX + i].apply { this[0] = r * scale; this[1] = g * scale; this[2] = b * scale }
        }

        val sb = StringBuilder()
        sb.append(base83((componentsX - 1) + (componentsY - 1) * 9, 1))
        val ac = factors.drop(1)
        val maxValue: Double
        if (ac.isNotEmpty()) {
            val actualMax = ac.maxOf { f -> max(abs(f[0]), max(abs(f[1]), abs(f[2]))) }
            val quantised = floor(actualMax * 166 - 0.5).toInt().coerceIn(0, 82)
            maxValue = (quantised + 1) / 166.0
            sb.append(base83(quantised, 1))
        } else {
            maxValue = 1.0
            sb.append(base83(0, 1))
        }
        val dc = factors[0]
        sb.append(base83((linearToSrgb(dc[0]) shl 16) + (linearToSrgb(dc[1]) shl 8) + linearToSrgb(dc[2]), 4))
        for (f in ac) {
            val q = { v: Double -> floor(signPow(v / maxValue, 0.5) * 9 + 9.5).toInt().coerceIn(0, 18) }
            sb.append(base83(q(f[0]) * 19 * 19 + q(f[1]) * 19 + q(f[2]), 2))
        }
        return sb.toString()
    }

    private fun base83(value: Int, length: Int): String {
        val sb = StringBuilder()
        for (i in 1..length) {
            val digit = (value / 83.0.pow(length - i).toInt()) % 83
            sb.append(CHARS[digit])
        }
        return sb.toString()
    }

    private fun srgbToLinear(v: Int): Double {
        val c = v / 255.0
        return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }

    private fun linearToSrgb(v: Double): Int {
        val c = min(1.0, max(0.0, v))
        return if (c <= 0.0031308) (c * 12.92 * 255 + 0.5).toInt() else ((1.055 * c.pow(1 / 2.4) - 0.055) * 255 + 0.5).roundToInt()
    }

    private fun signPow(v: Double, exp: Double): Double = abs(v).pow(exp).withSign(sign(v))
}
