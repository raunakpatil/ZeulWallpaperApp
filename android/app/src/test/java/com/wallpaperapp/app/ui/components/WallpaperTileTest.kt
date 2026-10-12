package com.wallpaperapp.app.ui.components

import com.google.common.truth.Truth.assertThat
import com.wallpaperapp.app.data.remote.FakeWallpaperRemoteDataSource
import org.junit.Test

class WallpaperTileTest {

    private val base = FakeWallpaperRemoteDataSource().wallpapers.first()

    @Test
    fun `aspect ratio follows the image but is clamped`() {
        assertThat(tileAspectRatio(base.copy(width = 1080, height = 1920))).isWithin(0.001f).of(0.5625f)
        assertThat(tileAspectRatio(base.copy(width = 1000, height = 5000))).isEqualTo(0.45f)
        assertThat(tileAspectRatio(base.copy(width = 5000, height = 1000))).isEqualTo(1.6f)
        assertThat(tileAspectRatio(base.copy(width = null, height = null))).isWithin(0.001f).of(0.5625f)
    }

    @Test
    fun `parses dominant colour`() {
        assertThat(parseHexColor("#FF0000")).isNotNull()
        assertThat(parseHexColor("red")).isNull()
        assertThat(parseHexColor(null)).isNull()
    }
}
