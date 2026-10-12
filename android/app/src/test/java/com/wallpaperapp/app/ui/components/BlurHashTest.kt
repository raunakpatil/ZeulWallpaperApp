package com.wallpaperapp.app.ui.components

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BlurHashTest {

    @Test
    fun `decodes a valid hash to opaque pixels`() {
        val pixels = BlurHash.decode("LEHV6nWB2yk8pyo0adR*.7kCMdnj", 8, 6)!!
        assertThat(pixels).hasLength(48)
        assertThat(pixels.all { (it ushr 24) == 0xFF }).isTrue()
        assertThat(pixels.toSet().size).isGreaterThan(1) // a gradient, not a flat colour
    }

    @Test
    fun `average colour matches the DC component`() {
        // The DC component of this hash is #979695, a neutral grey.
        val pixels = BlurHash.decode("LEHV6nWB2yk8pyo0adR*.7kCMdnj", 32, 32)!!
        assertThat(pixels.map { (it shr 16) and 0xFF }.average()).isWithin(25.0).of(151.0)
        assertThat(pixels.map { (it shr 8) and 0xFF }.average()).isWithin(25.0).of(150.0)
        assertThat(pixels.map { it and 0xFF }.average()).isWithin(25.0).of(149.0)
    }

    @Test
    fun `rejects malformed hashes`() {
        assertThat(BlurHash.decode("", 4, 4)).isNull()
        assertThat(BlurHash.decode("LEHV6nWB2yk8", 4, 4)).isNull() // wrong length for its size flag
        assertThat(BlurHash.decode("LEHV6nWB2yk8pyo0adR*.7kCMdné", 4, 4)).isNull() // invalid char
    }
}
