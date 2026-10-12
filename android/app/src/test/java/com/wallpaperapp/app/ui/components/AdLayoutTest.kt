package com.wallpaperapp.app.ui.components

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AdLayoutTest {

    @Test
    fun `one ad after every N wallpapers`() {
        val layout = AdLayout(every = 3)
        // W W W A W W W A W
        val slots = (0 until layout.slotCount(7)).map { if (layout.isAd(it)) "A" else "W${layout.wallpaperIndex(it)}" }
        assertThat(slots).containsExactly("W0", "W1", "W2", "A", "W3", "W4", "W5", "A", "W6").inOrder()
        assertThat(layout.adIndex(7)).isEqualTo(1)
    }

    @Test
    fun `no trailing ad before the next wallpaper exists`() {
        val layout = AdLayout(every = 12)
        assertThat(layout.slotCount(11)).isEqualTo(11)
        assertThat(layout.slotCount(12)).isEqualTo(13)
    }

    @Test
    fun `none means plain grid`() {
        val layout = AdLayout.None
        assertThat(layout.slotCount(30)).isEqualTo(30)
        assertThat((0 until 30).none(layout::isAd)).isTrue()
        assertThat(layout.wallpaperIndex(17)).isEqualTo(17)
    }

    @Test
    fun `every wallpaper index appears exactly once`() {
        val layout = AdLayout(every = 5)
        val n = 53
        val indexes = (0 until layout.slotCount(n)).filterNot(layout::isAd).map(layout::wallpaperIndex)
        assertThat(indexes).containsExactlyElementsIn(0 until n).inOrder()
    }
}
