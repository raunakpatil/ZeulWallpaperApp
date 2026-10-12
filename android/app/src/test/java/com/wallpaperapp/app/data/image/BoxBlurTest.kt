package com.wallpaperapp.app.data.image

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BoxBlurTest {

    private fun gray(v: Int) = (0xFF shl 24) or (v shl 16) or (v shl 8) or v

    @Test
    fun `flat image stays flat`() {
        val pixels = IntArray(25) { gray(120) }
        BoxBlur.blur(pixels, 5, 5, radius = 2, passes = 3)
        assertThat(pixels.toSet()).containsExactly(gray(120))
    }

    @Test
    fun `hard edge becomes a gradient and keeps alpha`() {
        val w = 20
        val h = 3
        val pixels = IntArray(w * h) { i -> if (i % w < w / 2) gray(0) else gray(255) }
        BoxBlur.blur(pixels, w, h, radius = 2, passes = 2)
        val row = (0 until w).map { pixels[w + it] and 0xFF }
        assertThat(row.first()).isLessThan(20)
        assertThat(row.last()).isGreaterThan(235)
        assertThat(row[w / 2]).isIn(60..200) // mid-grey at the old edge
        assertThat(row).isInOrder() // monotonic ramp
        assertThat(pixels.all { it ushr 24 == 0xFF }).isTrue()
    }
}
