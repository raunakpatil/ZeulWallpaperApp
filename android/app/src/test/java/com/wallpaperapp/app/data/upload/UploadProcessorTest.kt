package com.wallpaperapp.app.data.upload

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class UploadProcessorTest {
    private fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    @Test
    fun `device type follows the dashboard rule`() {
        assertThat(UploadProcessor.deviceType(1080, 2400)).isEqualTo("phone")
        assertThat(UploadProcessor.deviceType(1536, 2048)).isEqualTo("both")
        assertThat(UploadProcessor.deviceType(2560, 1600)).isEqualTo("tablet")
    }

    @Test
    fun `dominant colour is the biggest bucket`() {
        val pixels = IntArray(70) { rgb(250, 90, 20) } + IntArray(30) { rgb(10, 10, 10) }
        assertThat(UploadProcessor.dominantColor(pixels)).isEqualTo("#FA5A14")
    }

    @Test
    fun `amoled ratio counts near-black pixels`() {
        val pixels = IntArray(60) { rgb(0, 0, 0) } + IntArray(40) { rgb(200, 200, 200) }
        assertThat(UploadProcessor.amoledRatio(pixels)).isWithin(0.001f).of(0.6f)
    }

    @Test
    fun `blurhash has the standard length and a valid alphabet`() {
        val w = 24
        val h = 32
        val pixels = IntArray(w * h) { i -> rgb((i % w) * 10, (i / w) * 7, 120) }
        val hash = BlurHashEncoder.encode(pixels, w, h, 4, 5)
        // 1 (size) + 1 (max AC) + 4 (DC) + 2 per AC component
        assertThat(hash).hasLength(1 + 1 + 4 + 2 * (4 * 5 - 1))
        assertThat(hash).matches("[0-9A-Za-z#$%*+,\\-.:;=?@\\[\\]^_{|}~]+")
    }

    @Test
    fun `blurhash of a flat colour encodes that colour as the DC term`() {
        val hash = BlurHashEncoder.encode(IntArray(16) { rgb(255, 255, 255) }, 4, 4, 1, 1)
        // 1x1 components: size char '0', max-AC char '0', DC = white = 0xFFFFFF in base 83
        assertThat(hash).isEqualTo("00TSUA") // the reference hash for pure white
    }
}
