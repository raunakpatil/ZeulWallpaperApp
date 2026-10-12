package com.wallpaperapp.app.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ShareLinksTest {

    private val id = "7f3a9c2e-1b2c-4d5e-8f90-123456789abc"

    @Test
    fun `web link round-trips`() {
        assertThat(ShareLinks.webUrl(id)).isEqualTo("https://raunakpatil.github.io/wallpaper-app-site/w/?id=$id")
        assertThat(ShareLinks.parseWallpaperId(ShareLinks.webUrl(id))).isEqualTo(id)
    }

    @Test
    fun `zeul links parse into the right target`() {
        assertThat(ShareLinks.parse("zeul://wallpaper/$id")).isEqualTo(AppLink.Wallpaper(id))
        assertThat(ShareLinks.parse("zeul://wallpaper/${id.uppercase()}")).isEqualTo(AppLink.Wallpaper(id))
        assertThat(ShareLinks.parse("zeul://collection/$id")).isEqualTo(AppLink.Collection(id))
        assertThat(ShareLinks.parse("zeul://section/amoled")).isEqualTo(AppLink.Section("amoled"))
        assertThat(ShareLinks.parse("zeul://section/for_you")).isEqualTo(AppLink.Section("for_you"))
    }

    @Test
    fun `pre-rename scheme still opens during development`() {
        assertThat(ShareLinks.parseWallpaperId("wallpaperapp://wallpaper/$id")).isEqualTo(id)
    }

    @Test
    fun `we only ever emit the web link`() {
        assertThat(ShareLinks.webUrl(id)).startsWith("https://")
    }

    @Test
    fun `extra query params are fine`() {
        assertThat(ShareLinks.parseWallpaperId("https://raunakpatil.github.io/wallpaper-app-site/w/?utm=x&id=$id")).isEqualTo(id)
    }

    @Test
    fun `anything else is rejected`() {
        listOf(
            null, "", "not a url",
            "https://evil.example/wallpaper-app-site/w/?id=$id",
            "https://raunakpatil.github.io/other/?id=$id",
            "https://raunakpatil.github.io/wallpaper-app-site/w/?id=../../etc",
            "wallpaperapp://other/$id",
            "wallpaperapp://wallpaper/not-a-uuid",
            "zeul://collection/not-a-uuid",
            "zeul://section/AMOLED; drop table",
            "zeul://section/",
            "otherapp://wallpaper/$id",
            "http://raunakpatil.github.io/wallpaper-app-site/w/?id=$id",
        ).forEach { assertThat(ShareLinks.parse(it)).isNull() }
    }
}
