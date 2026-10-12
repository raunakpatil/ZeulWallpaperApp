package com.wallpaperapp.app.push

import com.google.common.truth.Truth.assertThat
import com.wallpaperapp.app.domain.model.AppLink
import org.junit.Test

class PushPayloadTest {

    private val id = "7f3a9c2e-1b2c-4d5e-8f90-123456789abc"

    @Test
    fun `daily push opens its wallpaper on the daily channel`() {
        val p = PushPayload.from(
            mapOf(
                "type" to "daily", "title" to "Today's pick", "body" to "Misty peaks",
                "image_url" to "https://raunakpatil.github.io/wallpaper-cdn/w/x/preview.webp",
                "link" to "zeul://wallpaper/$id", "wallpaper_id" to id, "push_id" to id,
            ),
        )!!
        assertThat(p.channel).isEqualTo(PushChannel.DAILY)
        assertThat(p.appLink).isEqualTo(AppLink.Wallpaper(id))
        assertThat(p.imageUrl).startsWith("https://")
        assertThat(p.pushId).isEqualTo(id)
    }

    @Test
    fun `channels follow the type`() {
        fun channel(type: String) = PushPayload.from(mapOf("type" to type, "title" to "t"))!!.channel
        assertThat(channel("new_drop")).isEqualTo(PushChannel.NEW_DROPS)
        assertThat(channel("collection")).isEqualTo(PushChannel.NEW_DROPS)
        assertThat(channel("announcement")).isEqualTo(PushChannel.ANNOUNCEMENTS)
        assertThat(channel("something_new")).isEqualTo(PushChannel.ANNOUNCEMENTS)
    }

    @Test
    fun `untrusted fields are cleaned`() {
        val p = PushPayload.from(
            mapOf(
                "title" to "x".repeat(200), "body" to "y".repeat(500),
                "image_url" to "http://insecure.example/a.webp", "link" to "https://evil.example", "push_id" to "nope",
            ),
        )!!
        assertThat(p.title).hasLength(65)
        assertThat(p.body).hasLength(240)
        assertThat(p.imageUrl).isNull()
        assertThat(p.link).isNull() // opens Home
        assertThat(p.pushId).isNull()
    }

    @Test
    fun `nothing to show without a title`() {
        assertThat(PushPayload.from(mapOf("type" to "daily", "title" to "  "))).isNull()
    }

    @Test
    fun `topics are split by build type`() {
        assertThat(PushTopics.name(PushTopics.DAILY, debug = true)).isEqualTo("debug_daily_wallpaper")
        assertThat(PushTopics.name(PushTopics.NEW_DROPS, debug = false)).isEqualTo("prod_new_drops")
        assertThat(PushTopics.name(PushTopics.ALL, debug = false)).isEqualTo("prod_all")
    }
}
