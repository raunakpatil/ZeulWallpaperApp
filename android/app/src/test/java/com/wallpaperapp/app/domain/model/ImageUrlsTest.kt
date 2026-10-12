package com.wallpaperapp.app.domain.model

import com.google.common.truth.Truth.assertThat
import com.wallpaperapp.app.data.remote.FakeWallpaperRemoteDataSource
import org.junit.Test

class ImageUrlsTest {

    private val wallpaper = FakeWallpaperRemoteDataSource().wallpapers.first()
        .copy(storageKey = "w/7f3a9c2e1b", originalExt = "png")

    @Test
    fun `builds CDN urls per contract`() {
        val base = "https://raunakpatil.github.io/wallpaper-cdn/"
        assertThat(wallpaper.imageUrl(base, ImageVariant.THUMB))
            .isEqualTo("https://raunakpatil.github.io/wallpaper-cdn/w/7f3a9c2e1b/thumb.webp")
        assertThat(wallpaper.imageUrl(base, ImageVariant.FULL))
            .isEqualTo("https://raunakpatil.github.io/wallpaper-cdn/w/7f3a9c2e1b/full.webp")
        assertThat(wallpaper.imageUrl(base, ImageVariant.ORIGINAL))
            .isEqualTo("https://raunakpatil.github.io/wallpaper-cdn/w/7f3a9c2e1b/original.png")
    }

    @Test
    fun `no url before config is loaded`() {
        assertThat(wallpaper.imageUrl("", ImageVariant.THUMB)).isNull()
    }
}
