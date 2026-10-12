package com.wallpaperapp.app.domain.model

import com.google.common.truth.Truth.assertThat
import com.wallpaperapp.app.data.remote.FakeWallpaperRemoteDataSource
import org.junit.Test
import kotlin.random.Random

class AutoChangerTest {

    private val all = FakeWallpaperRemoteDataSource().wallpapers

    @Test
    fun `never repeats the current wallpaper when there is a choice`() {
        val two = all.filter { it.deviceType == DeviceType.PHONE }.take(2)
        repeat(20) { seed ->
            assertThat(AutoChangerPicker.pick(two, lastId = two[0].id, random = Random(seed))).isEqualTo(two[1])
        }
    }

    @Test
    fun `single candidate is reused, empty gives nothing`() {
        val one = all.filter { it.deviceType == DeviceType.PHONE }.take(1)
        assertThat(AutoChangerPicker.pick(one, lastId = one[0].id)).isEqualTo(one[0])
        assertThat(AutoChangerPicker.pick(emptyList(), lastId = null)).isNull()
    }

    @Test
    fun `phone wallpapers preferred over tablet-only ones`() {
        val mixed = all.take(30)
        repeat(20) { seed ->
            assertThat(AutoChangerPicker.pick(mixed, null, Random(seed))!!.deviceType).isNotEqualTo(DeviceType.TABLET)
        }
    }

    @Test
    fun `category source round-trips`() {
        val s = AutoChangerSettings(source = AutoChangerSettings.categorySource("space"))
        assertThat(s.categorySlug).isEqualTo("space")
        assertThat(AutoChangerSettings(source = AutoChangerSettings.SOURCE_FAVORITES).categorySlug).isNull()
    }
}
