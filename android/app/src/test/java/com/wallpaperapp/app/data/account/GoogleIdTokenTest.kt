package com.wallpaperapp.app.data.account

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GoogleIdTokenTest {
    @Test fun `nonce is random 64 hex chars`() {
        val a = GoogleIdToken.newNonce()
        assertThat(a).matches("[0-9a-f]{64}")
        assertThat(GoogleIdToken.newNonce()).isNotEqualTo(a)
    }

    @Test fun `sha256 matches the known vector`() {
        assertThat(GoogleIdToken.sha256Hex("abc")).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
    }
}
