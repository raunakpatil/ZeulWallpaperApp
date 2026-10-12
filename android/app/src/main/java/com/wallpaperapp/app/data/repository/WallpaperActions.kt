package com.wallpaperapp.app.data.repository

import android.graphics.Bitmap
import android.graphics.Rect
import com.wallpaperapp.app.domain.model.WallpaperSizing
import android.net.Uri
import com.wallpaperapp.app.billing.PremiumManager
import com.wallpaperapp.app.data.image.BitmapEdits
import com.wallpaperapp.app.data.image.GallerySaver
import com.wallpaperapp.app.data.image.HttpStatusException
import com.wallpaperapp.app.data.image.ImageFetcher
import com.wallpaperapp.app.data.image.WallpaperApplier
import com.wallpaperapp.app.domain.model.ApplyTarget
import com.wallpaperapp.app.domain.model.EditParams
import com.wallpaperapp.app.domain.model.ImageVariant
import com.wallpaperapp.app.domain.model.NormalizedCrop
import com.wallpaperapp.app.domain.model.Wallpaper
import com.wallpaperapp.app.domain.model.downloadFileName
import com.wallpaperapp.app.domain.model.imageUrl
import com.wallpaperapp.app.domain.model.mimeType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Download and Apply. Unlock gating (rewarded ad / premium) sits in front of these in B8;
 * these only do the work.
 */
@Singleton
class WallpaperActions @Inject constructor(
    private val appConfig: AppConfigRepository,
    private val premiumManager: PremiumManager,
    private val fetcher: ImageFetcher,
    private val gallery: GallerySaver,
    private val applier: WallpaperApplier,
    private val downloads: DownloadRepository,
    private val wallpapers: WallpaperRepository,
) {
    /** Premium users get the original file; everyone else gets full.webp (up to 1440p). */
    fun downloadVariant(): ImageVariant =
        if (premiumManager.isPremium.value) ImageVariant.ORIGINAL else ImageVariant.FULL

    /** Saves to Pictures/Wallpapers, adds to download history and counts it on the server. */
    suspend fun download(wallpaper: Wallpaper): Uri {
        var variant = downloadVariant()
        val bytes = try {
            fetcher.fetchBytes(url(wallpaper, variant))
        } catch (e: HttpStatusException) {
            // Originals are optional at upload (A5). Without one, premium still gets the best we have.
            if (variant != ImageVariant.ORIGINAL || e.code != 404) throw e
            variant = ImageVariant.FULL
            fetcher.fetchBytes(url(wallpaper, variant))
        }
        val uri = gallery.save(bytes, wallpaper.downloadFileName(variant), wallpaper.mimeType(variant))
        downloads.recordDownload(wallpaper, uri.toString())
        return uri
    }

    /** The image the Apply editor works on. full.webp is plenty for a screen. */
    suspend fun loadForEditing(wallpaper: Wallpaper, maxLongEdge: Int): Bitmap =
        fetcher.loadBitmap(url(wallpaper, ImageVariant.FULL), maxLongEdge)

    suspend fun apply(
        wallpaper: Wallpaper,
        source: Bitmap,
        edits: EditParams,
        crop: NormalizedCrop,
        outWidth: Int,
        outHeight: Int,
        target: ApplyTarget,
    ) {
        setOnScreen(source, edits, crop, outWidth, outHeight, target)
        wallpapers.recordApply(wallpaper.id)
    }

    /**
     * Renders [framed] (what the user saw in a [viewportW]×[viewportH] screen) at the size the
     * launcher wants (bigger on tablets) and sets it. Shared by Apply and the auto changer.
     */
    suspend fun setOnScreen(
        source: Bitmap,
        edits: EditParams,
        framed: NormalizedCrop,
        viewportW: Int,
        viewportH: Int,
        target: ApplyTarget,
    ) {
        val (desiredW, desiredH) = applier.desiredSize()
        val plan = WallpaperSizing.plan(framed, source.width, source.height, viewportW, viewportH, desiredW, desiredH)
        val rendered = withContext(Dispatchers.Default) { BitmapEdits.render(source, edits, plan.crop, plan.width, plan.height) }
        try {
            val (l, t, r, b) = plan.visible.toList()
            applier.apply(rendered, target, Rect(l, t, r, b))
        } finally {
            rendered.recycle()
        }
    }

    private fun url(wallpaper: Wallpaper, variant: ImageVariant): String =
        wallpaper.imageUrl(appConfig.config.value.cdnBaseUrl, variant)
            ?: throw IOException("Image server address not loaded yet. Check your connection.")
}
