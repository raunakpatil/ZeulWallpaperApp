package com.wallpaperapp.app.domain.model

import kotlin.math.max
import kotlin.math.roundToInt

/** Where to set a wallpaper. */
enum class ApplyTarget { HOME, LOCK, BOTH }

/** Region of the source image, as fractions (0..1) of its width and height. */
data class NormalizedCrop(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    companion object {
        val Full = NormalizedCrop(0f, 0f, 1f, 1f)
    }
}

/** User edits applied before setting a wallpaper. Both 0..1; 0 = unchanged. */
data class EditParams(val blur: Float = 0f, val dim: Float = 0f)

/**
 * Editor geometry. The image is drawn centred, scaled to cover the viewport ([baseScale]) times
 * a user [zoom] >= 1, then shifted by [panX]/[panY] viewport pixels. Whatever the viewport shows
 * is what becomes the wallpaper.
 */
object CropMath {

    fun baseScale(imageW: Int, imageH: Int, viewportW: Float, viewportH: Float): Float =
        max(viewportW / imageW, viewportH / imageH)

    /** Keeps the image covering the viewport: no empty bands at any edge. */
    fun clampPan(
        panX: Float, panY: Float, zoom: Float,
        imageW: Int, imageH: Int, viewportW: Float, viewportH: Float,
    ): Pair<Float, Float> {
        val scale = baseScale(imageW, imageH, viewportW, viewportH) * zoom
        val maxX = ((imageW * scale - viewportW) / 2).coerceAtLeast(0f)
        val maxY = ((imageH * scale - viewportH) / 2).coerceAtLeast(0f)
        return panX.coerceIn(-maxX, maxX) to panY.coerceIn(-maxY, maxY)
    }

    fun crop(
        panX: Float, panY: Float, zoom: Float,
        imageW: Int, imageH: Int, viewportW: Float, viewportH: Float,
    ): NormalizedCrop {
        val scale = baseScale(imageW, imageH, viewportW, viewportH) * zoom
        val drawnW = imageW * scale
        val drawnH = imageH * scale
        // Viewport's top-left, measured in the drawn image's coordinates.
        val left = (drawnW - viewportW) / 2 - panX
        val top = (drawnH - viewportH) / 2 - panY
        return NormalizedCrop(
            left = (left / drawnW).coerceIn(0f, 1f),
            top = (top / drawnH).coerceIn(0f, 1f),
            right = ((left + viewportW) / drawnW).coerceIn(0f, 1f),
            bottom = ((top + viewportH) / drawnH).coerceIn(0f, 1f),
        )
    }
}

/** File name for a saved wallpaper, e.g. `neon-grid-7f3a9c2e.webp`. */
fun Wallpaper.downloadFileName(variant: ImageVariant): String {
    val slug = title.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(40).ifEmpty { "wallpaper" }
    return "$slug-${id.filter(Char::isLetterOrDigit).take(8)}.${fileExtension(variant)}"
}

fun Wallpaper.mimeType(variant: ImageVariant): String = when (fileExtension(variant)) {
    "png" -> "image/png"
    "jpg", "jpeg" -> "image/jpeg"
    else -> "image/webp"
}

private fun Wallpaper.fileExtension(variant: ImageVariant) =
    if (variant == ImageVariant.ORIGINAL) originalExt.lowercase() else "webp"

/** Output for WallpaperManager: the source region, the bitmap size, and which part the user framed. */
data class WallpaperPlan(
    val crop: NormalizedCrop,
    val width: Int,
    val height: Int,
    /** Rect (left, top, right, bottom) in output pixels that matches the editor's viewport. */
    val visible: IntArray,
) {
    override fun equals(other: Any?) = other is WallpaperPlan && crop == other.crop && width == other.width &&
        height == other.height && visible.contentEquals(other.visible)
    override fun hashCode() = 31 * (31 * (31 * crop.hashCode() + width) + height) + visible.contentHashCode()
}

object WallpaperSizing {
    /** Longest output edge; bigger tablets get scaled by the system rather than run out of memory. */
    const val MAX_EDGE = 4096f

    /**
     * Phones: the wallpaper is exactly the framed viewport. Tablets/foldables ask for more
     * ([desiredW]×[desiredH], usually square so it covers both orientations): widen the framed crop
     * around its centre towards that shape, as far as the image allows, and render it at least
     * that big. [visible] marks the part the user framed so Android shows it in this orientation.
     */
    fun plan(
        framed: NormalizedCrop,
        imageW: Int, imageH: Int,
        viewportW: Int, viewportH: Int,
        desiredW: Int, desiredH: Int,
    ): WallpaperPlan {
        val wantW = max(viewportW, desiredW)
        val wantH = max(viewportH, desiredH)
        val fx = framed.left * imageW; val fy = framed.top * imageH
        val fw = (framed.right - framed.left) * imageW; val fh = (framed.bottom - framed.top) * imageH
        if (wantW <= viewportW && wantH <= viewportH) {
            return WallpaperPlan(framed, viewportW, viewportH, intArrayOf(0, 0, viewportW, viewportH))
        }
        // Grow the framed region (px) by the same factors the output grows, clamped to the image.
        val cw = minOf(imageW.toFloat(), fw * wantW / viewportW)
        val ch = minOf(imageH.toFloat(), fh * wantH / viewportH)
        val cx = (fx + fw / 2 - cw / 2).coerceIn(0f, imageW - cw)
        val cy = (fy + fh / 2 - ch / 2).coerceIn(0f, imageH - ch)
        // Output keeps the crop's shape, scaled so it's at least the wanted size (memory-capped).
        val scale = minOf(max(wantW / cw, wantH / ch), MAX_EDGE / max(cw, ch))
        val outW = (cw * scale).roundToInt()
        val outH = (ch * scale).roundToInt()
        val visible = intArrayOf(
            ((fx - cx) * scale).roundToInt().coerceIn(0, outW),
            ((fy - cy) * scale).roundToInt().coerceIn(0, outH),
            ((fx + fw - cx) * scale).roundToInt().coerceIn(0, outW),
            ((fy + fh - cy) * scale).roundToInt().coerceIn(0, outH),
        )
        return WallpaperPlan(
            NormalizedCrop(cx / imageW, cy / imageH, (cx + cw) / imageW, (cy + ch) / imageH),
            outW, outH, visible,
        )
    }
}
