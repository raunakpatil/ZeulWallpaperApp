package com.wallpaperapp.app.ui.components

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.withSign

/**
 * Minimal BlurHash decoder (https://blurha.sh). Decodes to a tiny image that the UI stretches;
 * the result is smooth because a blurhash is only a few cosine components.
 */
object BlurHash {
    private const val CHARS =
        "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz#$%*+,-.:;=?@[]^_{|}~"

    private val cache by lazy { LruCache<String, ImageBitmap>(200) }

    /** Cached [ImageBitmap] for grids; null if [hash] is invalid. */
    fun decodeImageBitmap(hash: String, width: Int = 24, height: Int = 32): ImageBitmap? {
        val key = "$hash:$width:$height"
        cache.get(key)?.let { return it }
        val pixels = decode(hash, width, height) ?: return null
        val bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888).asImageBitmap()
        cache.put(key, bitmap)
        return bitmap
    }

    /** ARGB pixels, row by row, or null if [hash] is not a valid blurhash. */
    fun decode(hash: String, width: Int, height: Int, punch: Double = 1.0): IntArray? {
        if (hash.length < 6 || width <= 0 || height <= 0) return null
        val sizeFlag = decode83(hash, 0, 1) ?: return null
        val numY = sizeFlag / 9 + 1
        val numX = sizeFlag % 9 + 1
        if (hash.length != 4 + 2 * numX * numY) return null

        val maxAc = ((decode83(hash, 1, 2) ?: return null) + 1) / 166.0
        val colors = Array(numX * numY) { i ->
            if (i == 0) {
                decodeDc(decode83(hash, 2, 6) ?: return null)
            } else {
                decodeAc(decode83(hash, 4 + i * 2, 6 + i * 2) ?: return null, maxAc * punch)
            }
        }

        val cosX = Array(numX) { i -> DoubleArray(width) { x -> cos(PI * x * i / width) } }
        val cosY = Array(numY) { j -> DoubleArray(height) { y -> cos(PI * y * j / height) } }
        return IntArray(width * height) { index ->
            val x = index % width
            val y = index / width
            var r = 0.0
            var g = 0.0
            var b = 0.0
            for (j in 0 until numY) {
                for (i in 0 until numX) {
                    val basis = cosX[i][x] * cosY[j][y]
                    val c = colors[i + j * numX]
                    r += c[0] * basis
                    g += c[1] * basis
                    b += c[2] * basis
                }
            }
            (0xFF shl 24) or (linearToSrgb(r) shl 16) or (linearToSrgb(g) shl 8) or linearToSrgb(b)
        }
    }

    private fun decode83(s: String, from: Int, to: Int): Int? {
        var value = 0
        for (i in from until to) {
            val digit = CHARS.indexOf(s[i])
            if (digit < 0) return null
            value = value * 83 + digit
        }
        return value
    }

    private fun decodeDc(value: Int) = doubleArrayOf(
        srgbToLinear(value shr 16),
        srgbToLinear((value shr 8) and 255),
        srgbToLinear(value and 255),
    )

    private fun decodeAc(value: Int, maxAc: Double) = doubleArrayOf(
        signedPow2((value / (19 * 19) - 9) / 9.0) * maxAc,
        signedPow2(((value / 19) % 19 - 9) / 9.0) * maxAc,
        signedPow2((value % 19 - 9) / 9.0) * maxAc,
    )

    private fun signedPow2(v: Double) = (v * v).withSign(v)

    private fun srgbToLinear(value: Int): Double {
        val v = value / 255.0
        return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }

    private fun linearToSrgb(value: Double): Int {
        val v = value.coerceIn(0.0, 1.0)
        return if (v <= 0.0031308) {
            (v * 12.92 * 255 + 0.5).toInt()
        } else {
            ((1.055 * v.pow(1 / 2.4) - 0.055) * 255 + 0.5).toInt()
        }
    }
}
