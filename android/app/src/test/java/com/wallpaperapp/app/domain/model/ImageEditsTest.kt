package com.wallpaperapp.app.domain.model

import com.google.common.truth.Truth.assertThat
import com.wallpaperapp.app.data.remote.FakeWallpaperRemoteDataSource
import org.junit.Test

class ImageEditsTest {

    // Landscape image on a portrait phone: the real test upload is 2560×1491.
    private val imgW = 2560
    private val imgH = 1491
    private val vpW = 1080f
    private val vpH = 2400f

    @Test
    fun `unzoomed and centred crops the middle at the screen's aspect`() {
        val crop = CropMath.crop(0f, 0f, 1f, imgW, imgH, vpW, vpH)
        assertThat(crop.top).isWithin(1e-4f).of(0f)
        assertThat(crop.bottom).isWithin(1e-4f).of(1f)
        val widthPx = (crop.right - crop.left) * imgW
        assertThat(widthPx / imgH).isWithin(1e-3f).of(vpW / vpH) // same shape as the screen
        assertThat((crop.left + crop.right) / 2).isWithin(1e-4f).of(0.5f)
    }

    @Test
    fun `pan is clamped so the image always covers the screen`() {
        val (x, y) = CropMath.clampPan(100_000f, 100_000f, 1f, imgW, imgH, vpW, vpH)
        assertThat(y).isEqualTo(0f) // fills the height exactly, so no vertical room
        val crop = CropMath.crop(x, y, 1f, imgW, imgH, vpW, vpH)
        assertThat(crop.left).isWithin(1e-4f).of(0f) // dragged fully right shows the left edge
    }

    @Test
    fun `zoom shrinks the crop around the centre`() {
        val crop = CropMath.crop(0f, 0f, 2f, imgW, imgH, vpW, vpH)
        assertThat(crop.bottom - crop.top).isWithin(1e-4f).of(0.5f)
        assertThat((crop.top + crop.bottom) / 2).isWithin(1e-4f).of(0.5f)
    }

    @Test
    fun `download file names are safe and typed`() {
        val w = FakeWallpaperRemoteDataSource().wallpapers.first()
            .copy(id = "7f3a9c2e-1111-2222", title = "Neon Grid: Tokyo / 2026!", originalExt = "PNG")
        assertThat(w.downloadFileName(ImageVariant.FULL)).isEqualTo("neon-grid-tokyo-2026-7f3a9c2e.webp")
        assertThat(w.downloadFileName(ImageVariant.ORIGINAL)).isEqualTo("neon-grid-tokyo-2026-7f3a9c2e.png")
        assertThat(w.mimeType(ImageVariant.FULL)).isEqualTo("image/webp")
        assertThat(w.mimeType(ImageVariant.ORIGINAL)).isEqualTo("image/png")
        assertThat(w.copy(originalExt = "jpg").mimeType(ImageVariant.ORIGINAL)).isEqualTo("image/jpeg")
        assertThat(w.copy(title = "!!!").downloadFileName(ImageVariant.FULL)).startsWith("wallpaper-")
    }
}
