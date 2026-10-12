package com.wallpaperapp.app.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WallpaperSizingTest {

    @Test
    fun `phones set exactly what was framed`() {
        val framed = CropMath.crop(0f, 0f, 1f, 1536, 2752, 1080f, 2400f)
        val plan = WallpaperSizing.plan(framed, 1536, 2752, 1080, 2400, desiredW = 1080, desiredH = 2400)
        assertThat(plan.crop).isEqualTo(framed)
        assertThat(plan.width to plan.height).isEqualTo(1080 to 2400)
        assertThat(plan.visible.toList()).containsExactly(0, 0, 1080, 2400).inOrder()
    }

    @Test
    fun `tablets get a wider crop that covers both orientations`() {
        // Landscape 2560x1600 tablet, launcher wants 2560x2560; image is a wide 3840x2400.
        val framed = CropMath.crop(0f, 0f, 1f, 3840, 2400, 2560f, 1600f)
        val plan = WallpaperSizing.plan(framed, 3840, 2400, 2560, 1600, desiredW = 2560, desiredH = 2560)
        assertThat(plan.width).isAtLeast(2560)
        assertThat(plan.height).isAtLeast(2560)
        // The framed part is inside the output and keeps the viewport's shape.
        val (l, t, r, b) = plan.visible.toList()
        assertThat(l).isAtLeast(0); assertThat(t).isAtLeast(0)
        assertThat(r).isAtMost(plan.width); assertThat(b).isAtMost(plan.height)
        assertThat((r - l).toFloat() / (b - t)).isWithin(0.02f).of(2560f / 1600f)
        // The crop grew but stayed inside the image.
        assertThat(plan.crop.left).isAtLeast(0f); assertThat(plan.crop.right).isAtMost(1f)
        assertThat(plan.crop.bottom - plan.crop.top).isAtLeast(framed.bottom - framed.top)
    }

    @Test
    fun `output never exceeds the memory cap`() {
        val framed = CropMath.crop(0f, 0f, 1f, 8000, 8000, 3000f, 3000f)
        val plan = WallpaperSizing.plan(framed, 8000, 8000, 3000, 3000, desiredW = 6000, desiredH = 6000)
        assertThat(maxOf(plan.width, plan.height).toFloat()).isAtMost(WallpaperSizing.MAX_EDGE + 1)
    }

    @Test
    fun `small images still fill what they can`() {
        // A 1080x1920 image on a tablet asking for 2560x2560: crop is the whole image, scaled up.
        val framed = CropMath.crop(0f, 0f, 1f, 1080, 1920, 1600f, 2560f)
        val plan = WallpaperSizing.plan(framed, 1080, 1920, 1600, 2560, desiredW = 2560, desiredH = 2560)
        assertThat(plan.crop.left).isAtLeast(0f)
        assertThat(plan.crop.right).isAtMost(1f)
        assertThat(plan.height).isAtLeast(2560)
    }
}
