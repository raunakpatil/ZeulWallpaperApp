package com.wallpaperapp.app.data.upload

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.core.graphics.scale
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** CDN variants + the metadata the `user-upload` function needs (same sizes/limits as the dashboard, CONTRACTS §3). */
class ProcessedUpload(
    val width: Int,
    val height: Int,
    val thumb: ByteArray,
    val preview: ByteArray,
    val full: ByteArray,
    val original: ByteArray?,
    val blurhash: String,
    val dominantColor: String,
    val isAmoled: Boolean,
    val deviceType: String,
)

class UploadTooSmallException(val width: Int, val height: Int) : Exception("too small")

/**
 * Turns a picked photo into the CDN files on the phone, so the server only checks and stores them.
 * Everything is re-encoded, which also strips EXIF (no camera GPS location leaks into public files).
 */
@Singleton
class UploadProcessor @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    suspend fun process(uri: Uri): ProcessedUpload = withContext(Dispatchers.Default) {
        val source = decode(uri, ORIGINAL_MAX_EDGE)
        try {
            val w = source.width
            val h = source.height
            if (max(w, h) < MIN_LONG_EDGE || min(w, h) < MIN_SHORT_EDGE) throw UploadTooSmallException(w, h)

            val thumb = webpUnder(source.scaledToWidth(400), THUMB_MAX, 80)
            val preview = webpUnder(source.scaledToWidth(1080), PREVIEW_MAX, 85)
            val full = webpUnder(source.scaledToLongEdge(FULL_MAX_EDGE), FULL_MAX, 90)
            val original = jpegUnder(source, ORIGINAL_MAX)

            val small = source.scaledToLongEdge(128)
            val pixels = IntArray(small.width * small.height).also { small.getPixels(it, 0, small.width, 0, 0, small.width, small.height) }
            val tiny = source.scaledToLongEdge(32)
            val portrait = h >= w
            val hash = BlurHashEncoder.encode(tiny, if (portrait) 4 else 5, if (portrait) 5 else 4)

            ProcessedUpload(
                width = w,
                height = h,
                thumb = thumb,
                preview = preview,
                full = full,
                original = original,
                blurhash = hash,
                dominantColor = dominantColor(pixels),
                isAmoled = amoledRatio(pixels) >= AMOLED_RATIO,
                deviceType = deviceType(w, h),
            )
        } finally {
            source.recycle()
        }
    }

    /** Decodes with EXIF rotation applied, downsampled so the long edge is at most [maxEdge]. */
    private fun decode(uri: Uri, maxEdge: Int): Bitmap {
        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                val s = min(1f, maxEdge.toFloat() / max(info.size.width, info.size.height))
                if (s < 1f) decoder.setTargetSize((info.size.width * s).roundToInt(), (info.size.height * s).roundToInt())
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = false
            }
        } else {
            legacyDecode(uri, maxEdge)
        }
        // Flatten transparency onto black so JPEG/WebP output looks the same as the source.
        if (!bitmap.hasAlpha()) return bitmap
        val flat = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        Canvas(flat).apply { drawColor(Color.BLACK); drawBitmap(bitmap, 0f, 0f, null) }
        bitmap.recycle()
        return flat
    }

    @Suppress("DEPRECATION")
    private fun legacyDecode(uri: Uri, maxEdge: Int): Bitmap {
        val resolver = context.contentResolver
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxEdge) sample *= 2
        val decoded = resolver.openInputStream(uri)?.use {
            android.graphics.BitmapFactory.decodeStream(it, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("could not read the image")
        val rotation = resolver.openInputStream(uri)?.use { exifRotation(android.media.ExifInterface(it)) } ?: 0
        val upright = if (rotation == 0) decoded else Bitmap.createBitmap(
            decoded, 0, 0, decoded.width, decoded.height, android.graphics.Matrix().apply { postRotate(rotation.toFloat()) }, true,
        ).also { decoded.recycle() }
        return upright.scaledToLongEdge(maxEdge)
    }

    private fun exifRotation(exif: android.media.ExifInterface): Int =
        when (exif.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL)) {
            android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90
            android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180
            android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }

    private fun webpUnder(bitmap: Bitmap, maxBytes: Int, startQuality: Int): ByteArray =
        encodeUnder(bitmap, maxBytes, startQuality, webpFormat())

    private fun jpegUnder(bitmap: Bitmap, maxBytes: Int): ByteArray? =
        runCatching { encodeUnder(bitmap, maxBytes, 92, Bitmap.CompressFormat.JPEG) }.getOrNull()

    private fun encodeUnder(bitmap: Bitmap, maxBytes: Int, startQuality: Int, format: Bitmap.CompressFormat): ByteArray {
        var quality = startQuality
        while (quality >= 50) {
            val out = ByteArrayOutputStream()
            bitmap.compress(format, quality, out)
            if (out.size() <= maxBytes) return out.toByteArray()
            quality -= 10
        }
        error("could not compress below ${maxBytes / 1024} KB")
    }

    @Suppress("DEPRECATION")
    private fun webpFormat(): Bitmap.CompressFormat =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP

    companion object {
        const val MIN_LONG_EDGE = 1280
        const val MIN_SHORT_EDGE = 720
        private const val ORIGINAL_MAX_EDGE = 4096
        private const val FULL_MAX_EDGE = 2560
        private const val THUMB_MAX = 300 * 1024
        private const val PREVIEW_MAX = 1536 * 1024
        private const val FULL_MAX = 6 * 1024 * 1024
        private const val ORIGINAL_MAX = 15 * 1024 * 1024
        private const val AMOLED_RATIO = 0.4f

        /** Same rule as the dashboard's suggestDevice. */
        fun deviceType(w: Int, h: Int): String {
            val r = w.toFloat() / h
            return when {
                r < 0.62f -> "phone"
                r < 1.2f -> "both"
                else -> "tablet"
            }
        }

        /** Most common 4-bit-per-channel colour bucket, averaged (same as the dashboard). */
        fun dominantColor(pixels: IntArray): String {
            val n = IntArray(4096); val r = LongArray(4096); val g = LongArray(4096); val b = LongArray(4096)
            for (p in pixels) {
                val pr = (p shr 16) and 0xFF; val pg = (p shr 8) and 0xFF; val pb = p and 0xFF
                val key = ((pr shr 4) shl 8) or ((pg shr 4) shl 4) or (pb shr 4)
                n[key]++; r[key] += pr.toLong(); g[key] += pg.toLong(); b[key] += pb.toLong()
            }
            val best = n.indices.maxByOrNull { n[it] } ?: 0
            val count = n[best].coerceAtLeast(1)
            return "#%02X%02X%02X".format(r[best] / count, g[best] / count, b[best] / count)
        }

        fun amoledRatio(pixels: IntArray): Float {
            if (pixels.isEmpty()) return 0f
            val black = pixels.count { ((it shr 16) and 0xFF) <= 10 && ((it shr 8) and 0xFF) <= 10 && (it and 0xFF) <= 10 }
            return black.toFloat() / pixels.size
        }
    }
}

private fun Bitmap.scaledToWidth(target: Int): Bitmap {
    if (width <= target) return this
    return scale(target, (height * target.toFloat() / width).roundToInt().coerceAtLeast(1))
}

private fun Bitmap.scaledToLongEdge(maxEdge: Int): Bitmap {
    val s = min(1f, maxEdge.toFloat() / max(width, height))
    if (s >= 1f) return this
    return scale((width * s).roundToInt().coerceAtLeast(1), (height * s).roundToInt().coerceAtLeast(1))
}
