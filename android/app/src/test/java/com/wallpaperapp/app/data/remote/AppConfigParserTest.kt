package com.wallpaperapp.app.data.remote

import com.google.common.truth.Truth.assertThat
import com.wallpaperapp.app.domain.model.AppConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Test

class AppConfigParserTest {

    private fun rows(json: String): Map<String, JsonElement> = Json.decodeFromString(json)

    @Test
    fun `empty table gives contract defaults`() {
        assertThat(AppConfigParser.parse(emptyMap())).isEqualTo(AppConfig())
    }

    @Test
    fun `parses the default rows seeded by the migration`() {
        val config = AppConfigParser.parse(
            rows(
                """{
                "cdn_base_url": "https://raunakpatil.github.io/wallpaper-cdn/",
                "min_app_version": 3,
                "maintenance": {"enabled": true, "message": "Back soon"},
                "ads": {"native_every": 8, "app_open_enabled": true, "app_open_min_hours": 6},
                "unlock_mode": "24h",
                "free_max_resolution": 1920,
                "home_sections": ["curated", "new"],
                "featured_banner": {"wallpaper_id": "abc", "title": "Neon week"},
                "daily_wallpaper_id": "def",
                "paywall": {"highlight": "monthly", "headline": "Go pro"}
            }""",
            ),
        )
        assertThat(config.cdnBaseUrl).isEqualTo("https://raunakpatil.github.io/wallpaper-cdn")
        assertThat(config.minAppVersion).isEqualTo(3)
        assertThat(config.maintenance).isEqualTo(AppConfig.Maintenance(true, "Back soon"))
        assertThat(config.ads).isEqualTo(AppConfig.Ads(8, true, 6))
        assertThat(config.unlockMode).isEqualTo(AppConfig.UnlockMode.HOURS_24)
        assertThat(config.freeMaxResolution).isEqualTo(1920)
        assertThat(config.homeSections).containsExactly("curated", "new").inOrder()
        assertThat(config.featuredBanner).isEqualTo(AppConfig.FeaturedBanner("abc", null, "Neon week"))
        assertThat(config.dailyWallpaperId).isEqualTo("def")
        assertThat(config.paywall).isEqualTo(AppConfig.Paywall("monthly", "Go pro"))
    }

    @Test
    fun `malformed or null values fall back to defaults`() {
        val config = AppConfigParser.parse(
            rows(
                """{
                "cdn_base_url": "",
                "min_app_version": "not a number",
                "maintenance": "oops",
                "home_sections": [],
                "featured_banner": null,
                "daily_wallpaper_id": null
            }""",
            ),
        )
        assertThat(config).isEqualTo(AppConfig())
    }

    @Test
    fun `auth_email_enabled is off unless the dashboard turns it on`() {
        assertThat(AppConfigParser.parse(emptyMap()).authEmailEnabled).isFalse()
        assertThat(AppConfigParser.parse(rows("""{"auth_email_enabled": true}""")).authEmailEnabled).isTrue()
        assertThat(AppConfigParser.parse(rows("""{"auth_email_enabled": "yes"}""")).authEmailEnabled).isFalse()
    }

    @Test
    fun `unlock_mode defaults to 24h and can be set to permanent`() {
        assertThat(AppConfigParser.parse(emptyMap()).unlockMode).isEqualTo(AppConfig.UnlockMode.HOURS_24)
        assertThat(AppConfigParser.parse(rows("""{"unlock_mode": "permanent"}""")).unlockMode).isEqualTo(AppConfig.UnlockMode.PERMANENT)
        assertThat(AppConfigParser.parse(rows("""{"unlock_mode": "forever"}""")).unlockMode).isEqualTo(AppConfig.UnlockMode.HOURS_24)
    }
}
