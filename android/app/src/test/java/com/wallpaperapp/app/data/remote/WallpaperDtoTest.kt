package com.wallpaperapp.app.data.remote

import com.google.common.truth.Truth.assertThat
import com.wallpaperapp.app.domain.model.DeviceType
import kotlinx.serialization.json.Json
import org.junit.Test
import java.time.Instant

class WallpaperDtoTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `decodes a wallpapers_public row`() {
        val row = """{
            "id": "7f3a9c2e-0000-4000-8000-000000000001", "title": "Neon Grid",
            "storage_key": "w/7f3a9c2e1b", "original_ext": "png", "width": 1440, "height": 3200,
            "aspect_ratio": 0.45, "device_type": "both", "is_amoled": true, "is_curated": false,
            "curated_order": null, "is_premium": true, "dominant_color": "#000000", "color_family": "black",
            "blurhash": "LEHV6nWB2yk8pyo0adR*.7kCMdnj", "category_id": null, "category_slug": "dark",
            "category_name": "Dark", "file_size_kb": 812, "author_name": "A", "license": "CC0",
            "source_url": null, "download_count": 5, "fav_count": 2, "view_count": 40,
            "popularity_score": 17.5, "tags": ["neon", "grid"],
            "published_at": "2026-09-30T10:15:00.123456+00:00", "created_at": "2026-09-29T08:00:00+00:00",
            "some_future_column": 1
        }"""
        val w = json.decodeFromString<WallpaperDto>(row).toDomain()

        assertThat(w.storageKey).isEqualTo("w/7f3a9c2e1b")
        assertThat(w.originalExt).isEqualTo("png")
        assertThat(w.deviceType).isEqualTo(DeviceType.BOTH)
        assertThat(w.isAmoled).isTrue()
        assertThat(w.tags).containsExactly("neon", "grid")
        assertThat(w.popularityScore).isEqualTo(17.5)
        assertThat(w.publishedAt).isEqualTo(Instant.parse("2026-09-30T10:15:00.123456Z"))
    }

    @Test
    fun `missing optional fields use safe defaults`() {
        val w = json.decodeFromString<WallpaperDto>("""{"id": "x", "title": "T", "storage_key": "w/0000000001"}""").toDomain()
        assertThat(w.originalExt).isEqualTo("jpg")
        assertThat(w.deviceType).isEqualTo(DeviceType.PHONE)
        assertThat(w.tags).isEmpty()
        assertThat(w.publishedAt).isNull()
    }

    @Test
    fun `unknown suggestion kinds are dropped`() {
        assertThat(SuggestionDto("neon", "tag").toDomain()).isNotNull()
        assertThat(SuggestionDto("neon", "collection").toDomain()).isNull()
    }
}
